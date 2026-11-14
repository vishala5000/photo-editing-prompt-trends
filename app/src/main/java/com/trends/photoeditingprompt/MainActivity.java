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
    private OkHttpClient client = new OkHttpClient();
    private Map<String, List<RisingQueryAdapter.RisingItem>> trendsDataMap = new ConcurrentHashMap<>();
    private Map<String, String> errorMap = new ConcurrentHashMap<>(); // Track errors
    private AtomicInteger completedRequests = new AtomicInteger(0);
    private int totalRequests = 0;
    private boolean pipelineComplete = false;
    private String currentKeyword = "";
    private boolean allExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        tvActiveKeyword = findViewById(R.id.tvActiveNiche);
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
            item.isExpanded = allExpanded;
        }
        adapter.notifyDataSetChanged();
        Toast.makeText(this, allExpanded ? "📂 All expanded" : "📁 All collapsed", Toast.LENGTH_SHORT).show();
    }

    private void startPipeline() {
        String keyword = etSearch.getText().toString().trim();
        if (keyword.isEmpty()) {
            Toast.makeText(this, "⚠️ Please enter a keyword", Toast.LENGTH_SHORT).show();
            return;
        }
        
        currentKeyword = keyword;
        tvActiveKeyword.setText("🎯 Live Query: " + currentKeyword);
        
        progressBar.setVisibility(View.VISIBLE);
        suggestions.clear();
        trendsDataMap.clear();
        errorMap.clear();
        adapter.notifyDataSetChanged();
        completedRequests.set(0);
        pipelineComplete = false;
        allExpanded = false;

        statusText.setText("🎥 STEP 1: Fetching LIVE YouTube suggestions...");
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
                        statusText.setText("❌ Network error. Check connection.");
                    });
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                statusText.setText("❌ YouTube API failed: " + response.code());
                            });
                            return;
                        }
                        String body = response.body().string();
                        int start = body.indexOf("[");
                        int end = body.lastIndexOf("]");
                        if (start == -1 || end == -1) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                statusText.setText("❌ Invalid YouTube response");
                            });
                            return;
                        }
                        body = body.substring(start, end + 1);
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() < 2) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                statusText.setText("❌ No YouTube suggestions found");
                            });
                            return;
                        }
                        
                        JSONArray suggestionsArr = arr.getJSONArray(1);
                        List<String> liveSuggestions = new ArrayList<>();
                        
                        int limit = Math.min(8, suggestionsArr.length());
                        for (int i = 0; i < limit; i++) {
                            Object item = suggestionsArr.get(i);
                            String query = (item instanceof JSONArray) ? ((JSONArray) item).getString(0) : item.toString();
                            query = query.toLowerCase().trim();
                            if (!query.isEmpty() && !liveSuggestions.contains(query)) {
                                liveSuggestions.add(query);
                            }
                        }
                        
                        runOnUiThread(() -> {
                            statusText.setText("✅ Found " + liveSuggestions.size() + " live suggestions. Starting STEP 2: Live Google Trends...");
                            populateSuggestions(liveSuggestions);
                            startStep2(liveSuggestions);
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

    private void startStep2(List<String> liveSuggestions) {
        totalRequests = liveSuggestions.size();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (String suggestion : liveSuggestions) {
            executor.submit(() -> fetchLiveGoogleTrends(suggestion));
        }
        executor.shutdown();
    }

    private void fetchLiveGoogleTrends(String suggestion) {
        try {
            String jsonPayload = String.format(
                "{\"comparisonItem\":[{\"keyword\":\"%s\",\"geo\":\"\",\"time\":\"now 7-d\"}],\"category\":0,\"property\":\"\"}",
                suggestion
            );
            String encodedPayload = URLEncoder.encode(jsonPayload, "UTF-8");
            String trendsUrl = "https://trends.google.com/trends/api/explore?hl=en-US&tz=0&req=" + encodedPayload;
            
            // PRIMARY RELIABLE PROXY
            String proxyUrl = "https://corsproxy.io/?" + URLEncoder.encode(trendsUrl, "UTF-8");
            
            Request request = new Request.Builder().url(proxyUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
                
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { 
                    errorMap.put(suggestion, "Proxy blocked");
                    incrementAndCheck(); 
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            errorMap.put(suggestion, "HTTP " + response.code());
                            incrementAndCheck();
                            return;
                        }
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        String contents = json.getString("contents");
                        
                        if (contents.startsWith(")]}'\n")) {
                            contents = contents.substring(5);
                        }
                        
                        JSONObject root = new JSONObject(contents);
                        JSONObject defaultObj = root.getJSONObject("default");
                        JSONArray rankedList = defaultObj.getJSONArray("rankedList");
                        
                        if (rankedList.length() > 1) {
                            JSONObject risingObj = rankedList.getJSONObject(1);
                            JSONArray rankedKeywords = risingObj.getJSONArray("rankedKeyword");
                            
                            List<RisingQueryAdapter.RisingItem> realRisingItems = new ArrayList<>();
                            int limit = Math.min(10, rankedKeywords.length());
                            
                            for (int i = 0; i < limit; i++) {
                                JSONObject kw = rankedKeywords.getJSONObject(i);
                                String query = kw.getString("query");
                                String interest = kw.optString("value", "0");
                                String percent = kw.optString("formattedValue", "+0%");
                                realRisingItems.add(new RisingQueryAdapter.RisingItem(query, interest, percent));
                            }
                            
                            trendsDataMap.put(suggestion, realRisingItems);
                            runOnUiThread(() -> updateUI(suggestion));
                        } else {
                            errorMap.put(suggestion, "No rising data in API");
                        }
                    } catch (Exception e) {
                        errorMap.put(suggestion, "Parse error: " + e.getMessage());
                    } finally {
                        incrementAndCheck();
                    }
                }
            });
        } catch (Exception e) {
            errorMap.put(suggestion, "Request failed");
            incrementAndCheck();
        }
    }

    private synchronized void incrementAndCheck() {
        int completed = completedRequests.incrementAndGet();
        runOnUiThread(() -> {
            statusText.setText("📈 STEP 2: " + completed + "/" + totalRequests + " live Trends queries completed...");
            
            if (completed >= totalRequests && !pipelineComplete) {
                pipelineComplete = true;
                progressBar.setVisibility(View.GONE);
                
                int totalRising = 0;
                for (List<RisingQueryAdapter.RisingItem> list : trendsDataMap.values()) {
                    totalRising += list.size();
                }
                
                int errors = errorMap.size();
                String statusMsg = "✅ Complete! " + suggestions.size() + " suggestions | " + totalRising + " rising queries";
                if (errors > 0) {
                    statusMsg += " | ⚠️ " + errors + " failed (tap to see why)";
                }
                statusText.setText(statusMsg);
            }
        });
    }

    private void updateUI(String suggestion) {
        for (SuggestionAdapter.SuggestionItem item : suggestions) {
            if (item.suggestion.equalsIgnoreCase(suggestion)) {
                List<RisingQueryAdapter.RisingItem> realItems = trendsDataMap.getOrDefault(suggestion, new ArrayList<>());
                item.risingQueries.clear();
                item.risingQueries.addAll(realItems);
                
                // Attach error message if it failed
                if (realItems.isEmpty() && errorMap.containsKey(suggestion)) {
                    item.risingQueries.add(new RisingQueryAdapter.RisingItem(
                        "⚠️ Error: " + errorMap.get(suggestion), "0", "N/A"
                    ));
                }
                break;
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onCopyClick(SuggestionAdapter.SuggestionItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 Base Keyword: ").append(currentKeyword).append("\n");
        sb.append("🎥 Live YouTube Suggestion: ").append(item.suggestion).append("\n\n");
        sb.append("📈 REAL Google Trends Rising Queries (Past 7 Days):\n");
        
        if (item.risingQueries.isEmpty() || item.risingQueries.get(0).query.startsWith("⚠️")) {
            sb.append(item.risingQueries.isEmpty() ? "(No real-time rising data found)" : item.risingQueries.get(0).query);
        } else {
            sb.append("Query\tInterest\tIncrease\n");
            for (RisingQueryAdapter.RisingItem rq : item.risingQueries) {
                sb.append(rq.query).append("\t").append(rq.interest).append("\t").append(rq.percent).append("\n");
            }
        }
        
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("suggestion", sb.toString()));
        Toast.makeText(this, "📋 Copied data for: " + item.suggestion, Toast.LENGTH_SHORT).show();
    }

    private void copyAll() {
        if (suggestions.isEmpty()) { Toast.makeText(this, "No data to copy", Toast.LENGTH_SHORT).show(); return; }
        
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("🎯 BASE KEYWORD: ").append(currentKeyword).append("\n");
        sb.append("═══════════════════════════════════════\n\n");
        
        for (SuggestionAdapter.SuggestionItem item : suggestions) {
            sb.append("───────────────────────────────────────\n");
            sb.append("🎥 Live YouTube Suggestion: ").append(item.suggestion).append("\n");
            sb.append("───────────────────────────────────────\n");
            
            if (item.risingQueries.isEmpty() || item.risingQueries.get(0).query.startsWith("⚠️")) {
                sb.append(item.risingQueries.isEmpty() ? "(No real-time rising data found)\n\n" : item.risingQueries.get(0).query + "\n\n");
            } else {
                sb.append("📈 REAL Google Trends Rising Queries:\n");
                sb.append("Query\tInterest\tIncrease\n");
                for (RisingQueryAdapter.RisingItem rq : item.risingQueries) {
                    sb.append(rq.query).append("\t").append(rq.interest).append("\t").append(rq.percent).append("\n");
                }
                sb.append("\n");
            }
        }
        
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("all", sb.toString()));
        Toast.makeText(this, "📋 Copied all data!", Toast.LENGTH_SHORT).show();
    }
}
