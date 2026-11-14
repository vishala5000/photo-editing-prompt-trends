package com.trends.photoeditingprompt;

import android.os.Bundle;
import android.view.View;
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
    private TextView statusText;
    private EditText etSearch;
    private OkHttpClient client = new OkHttpClient();
    private Set<String> youtubeSuggestions = new HashSet<>();
    private Map<String, List<RisingQueryAdapter.RisingItem>> risingQueriesMap = new ConcurrentHashMap<>();
    private List<String> dynamicSeeds = new ArrayList<>();
    private int step0Count = 0, step1Count = 0, step2Count = 0;
    private boolean step0Complete = false, step1Complete = false;

    // DYNAMIC seeds: These are combined with live trending topics
    private final String[] BASE_PHRASES = {
        "photo editing prompt", "photo editing ideas", "portrait editing prompt",
        "face retouching prompt", "selfie editing prompt", "couple photo prompt"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        etSearch = findViewById(R.id.etSearch);
        SwipeRefreshLayout swipe = findViewById(R.id.swipeRefresh);
        Button btnCopyAll = findViewById(R.id.btnCopyAll);
        Button btnRefresh = findViewById(R.id.btnRefresh);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SuggestionAdapter(suggestions, this);
        recyclerView.setAdapter(adapter);

        btnCopyAll.setOnClickListener(v -> copyAll());
        btnRefresh.setOnClickListener(v -> startPipeline());
        swipe.setOnRefreshListener(() -> { startPipeline(); swipe.setRefreshing(false); });

        startPipeline();
    }

    private void startPipeline() {
        progressBar.setVisibility(View.VISIBLE);
        suggestions.clear();
        youtubeSuggestions.clear();
        risingQueriesMap.clear();
        dynamicSeeds.clear();
        adapter.notifyDataSetChanged();
        step0Count = 0; step1Count = 0; step2Count = 0;
        step0Complete = false; step1Complete = false;

        statusText.setText("🌐 STEP 0: Fetching today's trending topics as dynamic seeds...");

        // STEP 0: Get REAL trending topics from Google Trends RSS (changes daily!)
        fetchDynamicSeeds();
    }

    private void fetchDynamicSeeds() {
        try {
            String trendsUrl = "https://trends.google.com/trending/rss?geo=US&hl=en-US";
            String proxyUrl = "https://api.allorigins.win/get?url=" + URLEncoder.encode(trendsUrl, "UTF-8");
            
            Request request = new Request.Builder().url(proxyUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { useFallbackSeeds(); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) { useFallbackSeeds(); return; }
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        String contents = json.getString("contents");
                        
                        String[] items = contents.split("<item>");
                        for (int i = 1; i < Math.min(items.length, 50); i++) {
                            String item = items[i];
                            int titleStart = item.indexOf("<title>") + 7;
                            int titleEnd = item.indexOf("</title>");
                            if (titleStart > 7 && titleEnd > titleStart) {
                                String title = item.substring(titleStart, titleEnd).toLowerCase().trim();
                                // Only include photo/people related trending topics
                                if (title.contains("photo") || title.contains("edit") || 
                                    title.contains("portrait") || title.contains("selfie") ||
                                    title.contains("ai") || title.contains("image") ||
                                    title.contains("face") || title.contains("model") ||
                                    title.contains("celebrity") || title.contains("actor") ||
                                    title.contains("actress") || title.contains("singer")) {
                                    dynamicSeeds.add(title);
                                }
                            }
                        }
                        
                        runOnUiThread(() -> {
                            step0Complete = true;
                            statusText.setText("✅ Found " + dynamicSeeds.size() + " trending topics. Starting STEP 1...");
                            startStep1();
                        });
                    } catch (Exception e) { useFallbackSeeds(); }
                }
            });
        } catch (Exception e) { useFallbackSeeds(); }
    }

    private void useFallbackSeeds() {
        // If trending fetch fails, use base phrases + custom input
        String custom = etSearch.getText().toString().trim();
        if (!custom.isEmpty()) {
            dynamicSeeds.add(custom);
        }
        for (String base : BASE_PHRASES) {
            dynamicSeeds.add(base);
        }
        runOnUiThread(() -> {
            step0Complete = true;
            statusText.setText("✅ Using " + dynamicSeeds.size() + " seed phrases. Starting STEP 1...");
            startStep1();
        });
    }

    private void startStep1() {
        // Combine dynamic seeds with base phrases
        List<String> allKeywords = new ArrayList<>();
        String custom = etSearch.getText().toString().trim();
        if (!custom.isEmpty()) allKeywords.add(custom);
        
        for (String seed : dynamicSeeds) {
            allKeywords.add(seed + " editing prompt");
            allKeywords.add("photo editing prompt for " + seed);
        }
        for (String base : BASE_PHRASES) {
            allKeywords.add(base);
            allKeywords.add(base + " for boy");
            allKeywords.add(base + " for girl");
            allKeywords.add(base + " for couple");
        }

        statusText.setText("🎥 STEP 1: Fetching YouTube suggestions from " + allKeywords.size() + " dynamic keywords...");

        ExecutorService executor = Executors.newFixedThreadPool(12);
        for (String keyword : allKeywords) {
            executor.submit(() -> fetchYouTubeSuggestions(keyword, allKeywords.size()));
        }
        executor.shutdown();
    }

    private void fetchYouTubeSuggestions(String keyword, int total) {
        try {
            String url = "http://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=" + 
                URLEncoder.encode(keyword, "UTF-8") + "&hl=en-US";
            Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { checkStep1Complete(total); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) { checkStep1Complete(total); return; }
                        String body = response.body().string();
                        int start = body.indexOf("[");
                        int end = body.lastIndexOf("]");
                        if (start == -1 || end == -1) { checkStep1Complete(total); return; }
                        body = body.substring(start, end + 1);
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() < 2) { checkStep1Complete(total); return; }
                        JSONArray suggestionsArr = arr.getJSONArray(1);
                        
                        for (int i = 0; i < suggestionsArr.length(); i++) {
                            Object item = suggestionsArr.get(i);
                            String query = (item instanceof JSONArray) ? ((JSONArray) item).getString(0) : item.toString();
                            query = query.toLowerCase().trim();
                            if (!query.isEmpty()) {
                                synchronized (youtubeSuggestions) {
                                    youtubeSuggestions.add(query);
                                }
                            }
                        }
                    } catch (Exception e) {} finally { checkStep1Complete(total); }
                }
            });
        } catch (Exception e) { checkStep1Complete(total); }
    }

    private synchronized void checkStep1Complete(int total) {
        step1Count++;
        if (step1Count >= total && !step1Complete) {
            step1Complete = true;
            runOnUiThread(() -> {
                statusText.setText("✅ STEP 1 Complete! Found " + youtubeSuggestions.size() + " YouTube suggestions. Starting STEP 2...");
                populateSuggestionsList();
                startStep2();
            });
        } else {
            runOnUiThread(() -> {
                statusText.setText("🎥 STEP 1: " + step1Count + "/" + total + " | " + youtubeSuggestions.size() + " suggestions");
            });
        }
    }

    private void populateSuggestionsList() {
        runOnUiThread(() -> {
            suggestions.clear();
            for (String suggestion : youtubeSuggestions) {
                suggestions.add(new SuggestionAdapter.SuggestionItem(suggestion));
            }
            adapter.notifyDataSetChanged();
        });
    }

    private void startStep2() {
        statusText.setText("📈 STEP 2: Searching " + youtubeSuggestions.size() + " suggestions in Google Trends...");
        
        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (String suggestion : youtubeSuggestions) {
            executor.submit(() -> fetchGoogleTrendsRising(suggestion));
        }
        executor.shutdown();
    }

    private void fetchGoogleTrendsRising(String keyword) {
        try {
            String jsonPayload = String.format(
                "{\"comparisonItem\":[{\"keyword\":\"%s\",\"geo\":\"\",\"time\":\"now 7-d\"}],\"category\":0,\"property\":\"\"}",
                keyword
            );
            String encodedPayload = URLEncoder.encode(jsonPayload, "UTF-8");
            String trendsUrl = "https://trends.google.com/trends/api/explore?hl=en-US&tz=0&req=" + encodedPayload;
            String proxyUrl = "https://api.allorigins.win/get?url=" + URLEncoder.encode(trendsUrl, "UTF-8");
            
            Request request = new Request.Builder().url(proxyUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { checkStep2Complete(); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) { checkStep2Complete(); return; }
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        String contents = json.getString("contents");
                        
                        if (contents.startsWith(")]}'\n")) contents = contents.substring(5);
                        
                        JSONObject root = new JSONObject(contents);
                        JSONObject defaultObj = root.getJSONObject("default");
                        JSONArray rankedList = defaultObj.getJSONArray("rankedList");
                        
                        if (rankedList.length() > 1) {
                            JSONObject risingObj = rankedList.getJSONObject(1);
                            JSONArray rankedKeywords = risingObj.getJSONArray("rankedKeyword");
                            
                            List<RisingQueryAdapter.RisingItem> risingItems = new ArrayList<>();
                            for (int i = 0; i < rankedKeywords.length(); i++) {
                                JSONObject kw = rankedKeywords.getJSONObject(i);
                                String q = kw.getString("query");
                                String interest = kw.optString("value", "0");
                                String percent = kw.optString("formattedValue", "+0%");
                                risingItems.add(new RisingQueryAdapter.RisingItem(q, interest, percent));
                            }
                            
                            risingQueriesMap.put(keyword, risingItems);
                            runOnUiThread(() -> updateSuggestionWithRising(keyword, risingItems));
                        }
                    } catch (Exception e) {} finally { checkStep2Complete(); }
                }
            });
        } catch (Exception e) { checkStep2Complete(); }
    }

    private void updateSuggestionWithRising(String keyword, List<RisingQueryAdapter.RisingItem> risingItems) {
        for (SuggestionAdapter.SuggestionItem item : suggestions) {
            if (item.suggestion.equals(keyword)) {
                item.risingQueries.clear();
                item.risingQueries.addAll(risingItems);
                break;
            }
        }
        adapter.notifyDataSetChanged();
    }

    private synchronized void checkStep2Complete() {
        step2Count++;
        runOnUiThread(() -> {
            statusText.setText("📈 STEP 2: " + step2Count + "/" + youtubeSuggestions.size() + " | " + risingQueriesMap.size() + " with rising queries");
            if (step2Count >= youtubeSuggestions.size()) {
                progressBar.setVisibility(View.GONE);
                statusText.setText("✅ Complete! " + suggestions.size() + " suggestions, " + risingQueriesMap.size() + " with rising queries");
            }
        });
    }

    @Override
    public void onCopyClick(SuggestionAdapter.SuggestionItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("YouTube Suggestion: ").append(item.suggestion).append("\n\n");
        sb.append("Rising Queries:\n");
        
        if (item.risingQueries.isEmpty()) {
            sb.append("(No rising queries found)");
        } else {
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
        for (SuggestionAdapter.SuggestionItem item : suggestions) {
            sb.append("═══════════════════════════════════\n");
            sb.append("YouTube Suggestion: ").append(item.suggestion).append("\n");
            sb.append("═══════════════════════════════════\n");
            
            if (item.risingQueries.isEmpty()) {
                sb.append("(No rising queries found)\n\n");
            } else {
                sb.append("Rising Queries:\n");
                sb.append("Query\tInterest\tIncrease\n");
                for (RisingQueryAdapter.RisingItem rq : item.risingQueries) {
                    sb.append(rq.query).append("\t").append(rq.interest).append("\t").append(rq.percent).append("\n");
                }
                sb.append("\n");
            }
        }
        
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("all", sb.toString()));
        Toast.makeText(this, "📋 Copied all " + suggestions.size() + " suggestions!", Toast.LENGTH_SHORT).show();
    }
}
