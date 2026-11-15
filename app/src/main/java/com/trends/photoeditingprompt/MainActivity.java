package com.trends.photoeditingprompt;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class MainActivity extends AppCompatActivity implements SuggestionAdapter.OnCopyClickListener {
    private RecyclerView recyclerView;
    private List<SuggestionAdapter.SuggestionItem> suggestions = new ArrayList<>();
    private SuggestionAdapter adapter;
    private ProgressBar progressBar;
    private TextView statusText, tvActiveKeyword;
    private EditText etSearch;
    
    private OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build();
    
    private Map<String, List<RisingQueryAdapter.RisingItem>> trendsDataMap = new ConcurrentHashMap<>();
    private AtomicInteger completedVideoChecks = new AtomicInteger(0);
    private int totalVideoChecks = 0;
    private String currentKeyword = "";
    private boolean allExpanded = false;

    private static final String[] PIPED_INSTANCES = {
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.in.projectsegfau.lt"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        tvActiveKeyword = findViewById(R.id.tvActiveKeyword);
        etSearch = findViewById(R.id.etSearch);
        SwipeRefreshLayout swipe = findViewById(R.id.swipeRefresh);
        Button btnCopyAll = findViewById(R.id.btnCopyAll);
        Button btnExpandAll = findViewById(R.id.btnExpandAll);
        Button btnRefresh = findViewById(R.id.btnRefresh);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SuggestionAdapter(suggestions, this);
        recyclerView.setAdapter(adapter);

        btnCopyAll.setOnClickListener(v -> copyAll());
        btnExpandAll.setOnClickListener(v -> toggleExpandAll());
        btnRefresh.setOnClickListener(v -> startPipeline());
        swipe.setOnRefreshListener(() -> { startPipeline(); swipe.setRefreshing(false); });
        
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                startPipeline();
                return true;
            }
            return false;
        });
    }

    private void toggleExpandAll() {
        allExpanded = !allExpanded;
        for (SuggestionAdapter.SuggestionItem item : suggestions) {
            if (!item.risingQueries.isEmpty()) {
                item.isExpanded = allExpanded;
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void startPipeline() {
        String keyword = etSearch.getText().toString().trim();
        if (keyword.isEmpty()) {
            Toast.makeText(this, "⚠️ Please enter a keyword", Toast.LENGTH_SHORT).show();
            return;
        }
        
        currentKeyword = keyword;
        tvActiveKeyword.setText("🎯 Keyword: " + currentKeyword);
        
        progressBar.setVisibility(View.VISIBLE);
        suggestions.clear();
        trendsDataMap.clear();
        adapter.notifyDataSetChanged();
        completedVideoChecks.set(0);
        allExpanded = false;

        statusText.setText("🎥 STEP 1: Fetching YouTube suggestions...");
        fetchYouTubeSuggestions(keyword);
    }

    private void fetchYouTubeSuggestions(String keyword) {
        try {
            String url = "http://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=" + 
                URLEncoder.encode(keyword, "UTF-8") + "&hl=en-US";
            Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { 
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("❌ Network error");
                    });
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        int start = body.indexOf("[");
                        int end = body.lastIndexOf("]");
                        if (start == -1 || end == -1) return;
                        body = body.substring(start, end + 1);
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() < 2) return;
                        
                        JSONArray suggestionsArr = arr.getJSONArray(1);
                        List<String> liveSuggestions = new ArrayList<>();
                        
                        int limit = Math.min(10, suggestionsArr.length());
                        for (int i = 0; i < limit; i++) {
                            Object item = suggestionsArr.get(i);
                            String query = (item instanceof JSONArray) ? ((JSONArray) item).getString(0) : item.toString();
                            query = query.toLowerCase().trim();
                            if (!query.isEmpty() && !liveSuggestions.contains(query)) {
                                liveSuggestions.add(query);
                            }
                        }
                        
                        runOnUiThread(() -> {
                            statusText.setText("✅ Found " + liveSuggestions.size() + " suggestions. STEP 2: Checking video views...");
                            populateSuggestions(liveSuggestions);
                            checkAllVideosSequential(liveSuggestions);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            statusText.setText("❌ Parse error: " + e.getMessage());
                        });
                    }
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                statusText.setText("❌ Request failed: " + e.getMessage());
            });
        }
    }

    private void populateSuggestions(List<String> liveSuggestions) {
        suggestions.clear();
        for (String suggestion : liveSuggestions) {
            suggestions.add(new SuggestionAdapter.SuggestionItem(suggestion));
        }
        adapter.notifyDataSetChanged();
    }

    private void checkAllVideosSequential(List<String> liveSuggestions) {
        totalVideoChecks = liveSuggestions.size();
        new Thread(() -> {
            for (int i = 0; i < liveSuggestions.size(); i++) {
                String suggestion = liveSuggestions.get(i);
                checkVideosForSuggestion(suggestion);
                
                if (i < liveSuggestions.size() - 1) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }).start();
    }

    private void checkVideosForSuggestion(String suggestion) {
        try {
            List<SuggestionAdapter.VideoInfo> top3Videos = searchTop3Videos(suggestion);
            
            if (top3Videos.size() < 3) {
                markVideoCheckDone(suggestion, true, "⚠️ Could not find 3 videos. Fetching trends anyway...", top3Videos);
                fetchGoogleTrendsRising(suggestion);
                return;
            }
            
            boolean allPass = true;
            StringBuilder statusBuilder = new StringBuilder();
            statusBuilder.append("📊 Top 3 Videos (Views):\n");
            
            for (int i = 0; i < 3; i++) {
                SuggestionAdapter.VideoInfo v = top3Videos.get(i);
                boolean passes = v.passesRules();
                String icon = passes ? "✅" : "❌";
                
                statusBuilder.append(icon).append(" ").append(truncate(v.title, 40)).append("\n");
                statusBuilder.append("   👁 ").append(formatNumber(v.views)).append(" views\n");
                
                if (!passes) allPass = false;
            }
            
            String finalStatus;
            if (allPass) {
                finalStatus = "✅ LOW COMPETITION (All videos < 50K views)\n" + statusBuilder.toString();
            } else {
                finalStatus = "❌ HIGH COMPETITION (Some videos >= 50K views)\n" + statusBuilder.toString();
            }
            
            markVideoCheckDone(suggestion, allPass, finalStatus, top3Videos);
            
            if (allPass) {
                fetchGoogleTrendsRising(suggestion);
            }
            
        } catch (Exception e) {
            markVideoCheckDone(suggestion, true, "⚠️ Video check failed. Fetching trends...", new ArrayList<>());
            fetchGoogleTrendsRising(suggestion);
        }
    }

    private List<SuggestionAdapter.VideoInfo> searchTop3Videos(String query) {
        List<SuggestionAdapter.VideoInfo> videos = new ArrayList<>();
        
        for (String instance : PIPED_INSTANCES) {
            try {
                String url = instance + "/search?q=" + URLEncoder.encode(query, "UTF-8") + "&filter=videos";
                Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();
                
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) continue;
                
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                JSONArray items = json.optJSONArray("items");
                
                if (items == null) continue;
                
                for (int i = 0; i < items.length() && videos.size() < 3; i++) {
                    JSONObject item = items.getJSONObject(i);
                    
                    if (!"stream".equals(item.optString("type"))) continue;
                    
                    String title = item.optString("title", "Untitled");
                    
                    long views = 0;
                    try {
                        views = item.getLong("views");
                    } catch (Exception e) {
                        views = parseViewCount(item.optString("views", "0"));
                    }
                    
                    videos.add(new SuggestionAdapter.VideoInfo(title, views));
                }
                
                if (videos.size() >= 3) break;
                
            } catch (Exception e) {
                continue;
            }
        }
        return videos;
    }

    private long parseViewCount(String text) {
        try {
            text = text.replace(" views", "").replace(",", "").trim();
            if (text.contains("M")) return (long)(Double.parseDouble(text.replace("M", "")) * 1000000);
            if (text.contains("K")) return (long)(Double.parseDouble(text.replace("K", "")) * 1000);
            return Long.parseLong(text);
        } catch (Exception e) {
            return 0;
        }
    }

    private void markVideoCheckDone(String suggestion, boolean passed, String status, List<SuggestionAdapter.VideoInfo> videos) {
        runOnUiThread(() -> {
            for (SuggestionAdapter.SuggestionItem item : suggestions) {
                if (item.suggestion.equalsIgnoreCase(suggestion)) {
                    item.videoCheckDone = true;
                    item.videoCheckPassed = passed;
                    item.checkStatus = status;
                    item.checkedVideos.clear();
                    item.checkedVideos.addAll(videos);
                    break;
                }
            }
            adapter.notifyDataSetChanged();
            
            int completed = completedVideoChecks.incrementAndGet();
            statusText.setText("📊 STEP 2: " + completed + "/" + totalVideoChecks + " videos checked...");
            
            if (completed >= totalVideoChecks) {
                int passedCount = 0;
                for (SuggestionAdapter.SuggestionItem item : suggestions) {
                    if (item.videoCheckPassed) passedCount++;
                }
                statusText.setText("✅ Video check complete! " + passedCount + "/" + totalVideoChecks + " passed. Fetching Google Trends...");
            }
        });
    }

    private void fetchGoogleTrendsRising(String suggestion) {
        try {
            String jsonPayload = String.format(
                "{\"comparisonItem\":[{\"keyword\":\"%s\",\"geo\":\"\",\"time\":\"now 7-d\"}],\"category\":0,\"property\":\"youtube\"}",
                suggestion
            );
            String encodedPayload = URLEncoder.encode(jsonPayload, "UTF-8");
            String trendsUrl = "https://trends.google.com/trends/api/explore?hl=en-US&tz=0&req=" + encodedPayload;
            
            String[] proxies = {
                "https://api.allorigins.win/raw?url=",
                "https://corsproxy.io/?",
                "https://api.codetabs.com/v1/proxy/?quest="
            };
            
            for (String proxy : proxies) {
                try {
                    String proxyUrl = proxy + URLEncoder.encode(trendsUrl, "UTF-8");
                    Request request = new Request.Builder().url(proxyUrl).header("User-Agent", "Mozilla/5.0").build();
                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful()) continue;
                    
                    String body = response.body().string();
                    if (body.startsWith(")]}'\n")) body = body.substring(5);
                    
                    JSONObject root = new JSONObject(body);
                    JSONObject defaultObj = root.getJSONObject("default");
                    if (!defaultObj.has("rankedList")) continue;
                    
                    JSONArray rankedList = defaultObj.getJSONArray("rankedList");
                    if (rankedList.length() <= 1) continue;
                    
                    JSONObject risingObj = rankedList.getJSONObject(1);
                    JSONArray rankedKeywords = risingObj.getJSONArray("rankedKeyword");
                    
                    List<RisingQueryAdapter.RisingItem> risingItems = new ArrayList<>();
                    int limit = Math.min(10, rankedKeywords.length());
                    
                    for (int i = 0; i < limit; i++) {
                        JSONObject kw = rankedKeywords.getJSONObject(i);
                        String query = kw.getString("query");
                        String interest = kw.optString("value", "0");
                        String percent = kw.optString("formattedValue", "+0%");
                        risingItems.add(new RisingQueryAdapter.RisingItem(query, interest, percent));
                    }
                    
                    if (!risingItems.isEmpty()) {
                        trendsDataMap.put(suggestion, risingItems);
                        runOnUiThread(() -> updateUIWithTrends(suggestion));
                    }
                    break;
                } catch (Exception e) {
                    continue;
                }
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    private void updateUIWithTrends(String suggestion) {
        for (SuggestionAdapter.SuggestionItem item : suggestions) {
            if (item.suggestion.equalsIgnoreCase(suggestion)) {
                List<RisingQueryAdapter.RisingItem> items = trendsDataMap.getOrDefault(suggestion, new ArrayList<>());
                item.risingQueries.clear();
                item.risingQueries.addAll(items);
                
                if (item.videoCheckPassed && !items.isEmpty()) {
                    item.checkStatus = "✅ LOW COMPETITION - " + items.size() + " rising queries found!\n" + 
                        item.checkStatus.substring(item.checkStatus.indexOf("\n") + 1);
                }
                break;
            }
        }
        adapter.notifyDataSetChanged();
    }

    private String formatNumber(long num) {
        if (num >= 1000000) return String.format("%.1fM", num / 1000000.0);
        if (num >= 1000) return String.format("%.1fK", num / 1000.0);
        return String.valueOf(num);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    @Override
    public void onCopyClick(SuggestionAdapter.SuggestionItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 Keyword: ").append(currentKeyword).append("\n");
        sb.append("🎥 YouTube Suggestion: ").append(item.suggestion).append("\n\n");
        sb.append("📊 Video Analysis:\n").append(item.checkStatus.replace("\n", "\n")).append("\n\n");
        
        if (item.risingQueries.isEmpty()) {
            sb.append("📈 Google Trends: (No rising queries)");
        } else {
            sb.append("📈 Google Trends Rising Queries (Last 7 Days):\n");
            sb.append("Query\tInterest\tIncrease\n");
            for (RisingQueryAdapter.RisingItem rq : item.risingQueries) {
                sb.append(rq.query).append("\t").append(rq.interest).append("\t").append(rq.percent).append("\n");
            }
        }
        
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("suggestion", sb.toString()));
        Toast.makeText(this, "📋 Copied: " + item.suggestion, Toast.LENGTH_SHORT).show();
    }

    private void copyAll() {
        if (suggestions.isEmpty()) { Toast.makeText(this, "No data", Toast.LENGTH_SHORT).show(); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("🎯 KEYWORD: ").append(currentKeyword).append("\n");
        sb.append("═══════════════════════════════════════\n\n");
        
        for (SuggestionAdapter.SuggestionItem item : suggestions) {
            sb.append("───────────────────────────────────────\n");
            sb.append("🎥 YouTube Suggestion: ").append(item.suggestion).append("\n");
            sb.append("Status: ").append(item.videoCheckPassed ? "✅ LOW COMPETITION" : "❌ HIGH COMPETITION").append("\n\n");
            sb.append("📊 Video Analysis:\n").append(item.checkStatus.replace("\n", "\n")).append("\n");
            
            if (!item.risingQueries.isEmpty()) {
                sb.append("\n📈 Google Trends Rising Queries:\n");
                sb.append("Query\tInterest\tIncrease\n");
                for (RisingQueryAdapter.RisingItem rq : item.risingQueries) {
                    sb.append(rq.query).append("\t").append(rq.interest).append("\t").append(rq.percent).append("\n");
                }
            }
            sb.append("\n");
        }
        
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("all", sb.toString()));
        Toast.makeText(this, "📋 Copied all!", Toast.LENGTH_SHORT).show();
    }
}
