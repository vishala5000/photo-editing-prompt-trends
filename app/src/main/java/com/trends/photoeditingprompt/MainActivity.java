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
    private TextView statusText, tvActiveNiche;
    private EditText etSearch;
    private OkHttpClient client = new OkHttpClient();
    private Map<String, List<RisingQueryAdapter.RisingItem>> nicheDataMap = new ConcurrentHashMap<>();
    private AtomicInteger completedRequests = new AtomicInteger(0);
    private int totalRequests = 0;
    private boolean pipelineComplete = false;
    private String currentKeyword = "";
    private boolean allExpanded = false;

    // UNIVERSAL suffixes - work for ANY keyword in ANY language/niche
    private final String[] UNIVERSAL_SUFFIXES = {
        "", " for beginners", " tutorial", " tips", " ideas",
        " how to", " best", " 2026", " aesthetic", " viral",
        " trending", " easy", " professional", " creative"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        tvActiveNiche = findViewById(R.id.tvActiveNiche);
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
        
        // Allow Enter key to trigger search
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO) {
                startPipeline();
                return true;
            }
            return false;
        });

        // DO NOT auto-start. Wait for user to type their keyword.
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
            Toast.makeText(this, "⚠️ Please enter a keyword first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        currentKeyword = keyword;
        tvActiveNiche.setText("🎯 Active Keyword: " + currentKeyword);
        
        progressBar.setVisibility(View.VISIBLE);
        suggestions.clear();
        nicheDataMap.clear();
        adapter.notifyDataSetChanged();
        completedRequests.set(0);
        pipelineComplete = false;
        allExpanded = false;

        // Generate seeds DYNAMICALLY from user's keyword
        List<String> seeds = new ArrayList<>();
        for (String suffix : UNIVERSAL_SUFFIXES) {
            seeds.add(keyword + suffix);
        }
        // Also add question-based seeds
        seeds.add("how to " + keyword);
        seeds.add("best " + keyword);
        seeds.add("what is " + keyword);
        seeds.add("why " + keyword);

        // Total requests: seeds × 2 engines (YouTube + Google)
        totalRequests = seeds.size() * 2;
        
        statusText.setText("🚀 Fetching real-time data for \"" + keyword + "\" (" + seeds.size() + " seeds × 2 engines = " + totalRequests + " requests)...");

        ExecutorService executor = Executors.newFixedThreadPool(12);
        for (String seed : seeds) {
            executor.submit(() -> fetchYouTubeSuggestions(seed));
            executor.submit(() -> fetchGoogleSuggestions(seed));
        }
        executor.shutdown();
    }

    // ENGINE 1: YouTube Autocomplete (Real-Time)
    private void fetchYouTubeSuggestions(String keyword) {
        try {
            String url = "http://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=" + 
                URLEncoder.encode(keyword, "UTF-8") + "&hl=en-US";
            Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { incrementAndCheck(); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    processSuggestions(response, keyword, "youtube");
                }
            });
        } catch (Exception e) { incrementAndCheck(); }
    }

    // ENGINE 2: Google Search Autocomplete (Real-Time)
    private void fetchGoogleSuggestions(String keyword) {
        try {
            String url = "https://suggestqueries.google.com/complete/search?client=firefox&q=" + 
                URLEncoder.encode(keyword, "UTF-8") + "&hl=en-US";
            Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { incrementAndCheck(); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    processSuggestions(response, keyword, "google");
                }
            });
        } catch (Exception e) { incrementAndCheck(); }
    }

    private void processSuggestions(Response response, String baseKeyword, String source) {
        try {
            if (!response.isSuccessful()) { incrementAndCheck(); return; }
            String body = response.body().string();
            int start = body.indexOf("[");
            int end = body.lastIndexOf("]");
            if (start == -1 || end == -1) { incrementAndCheck(); return; }
            body = body.substring(start, end + 1);
            JSONArray arr = new JSONArray(body);
            if (arr.length() < 2) { incrementAndCheck(); return; }
            JSONArray suggestionsArr = arr.getJSONArray(1);
            
            List<RisingQueryAdapter.RisingItem> items = new ArrayList<>();
            int limit = Math.min(5, suggestionsArr.length());
            
            for (int i = 0; i < limit; i++) {
                Object item = suggestionsArr.get(i);
                String query = (item instanceof JSONArray) ? ((JSONArray) item).getString(0) : item.toString();
                query = query.toLowerCase().trim();
                
                // Skip if it's the exact same as the base keyword
                if (!query.isEmpty() && !query.equals(baseKeyword.toLowerCase().trim())) {
                    // Rank-based metrics (Rank 1 = Highest real-time demand)
                    int interest = Math.max(10, 100 - (i * 15));
                    int percent = Math.max(10, 250 - (i * 40));
                    String percentStr = "+" + percent + "%";
                    
                    items.add(new RisingQueryAdapter.RisingItem(query, String.valueOf(interest), percentStr));
                }
            }
            
            if (!items.isEmpty()) {
                synchronized (nicheDataMap) {
                    List<RisingQueryAdapter.RisingItem> existing = nicheDataMap.getOrDefault(baseKeyword, new ArrayList<>());
                    existing.addAll(items);
                    nicheDataMap.put(baseKeyword, existing);
                }
                runOnUiThread(() -> updateUI(baseKeyword));
            }
        } catch (Exception e) {
            // Silent fail
        } finally {
            incrementAndCheck();
        }
    }

    private synchronized void incrementAndCheck() {
        int completed = completedRequests.incrementAndGet();
        runOnUiThread(() -> {
            statusText.setText("📡 " + completed + "/" + totalRequests + " requests | Found " + suggestions.size() + " suggestion groups...");
            
            if (completed >= totalRequests && !pipelineComplete) {
                pipelineComplete = true;
                progressBar.setVisibility(View.GONE);
                int totalQueries = 0;
                for (List<RisingQueryAdapter.RisingItem> list : nicheDataMap.values()) {
                    totalQueries += list.size();
                }
                statusText.setText("✅ Complete! Keyword: \"" + currentKeyword + "\" | " + suggestions.size() + " groups | " + totalQueries + " real-time queries");
            }
        });
    }

    private void updateUI(String baseKeyword) {
        SuggestionAdapter.SuggestionItem targetItem = null;
        for (SuggestionAdapter.SuggestionItem item : suggestions) {
            if (item.suggestion.equalsIgnoreCase(baseKeyword)) {
                targetItem = item;
                break;
            }
        }
        
        if (targetItem == null) {
            targetItem = new SuggestionAdapter.SuggestionItem(baseKeyword);
            suggestions.add(targetItem);
            // Sort by number of related queries (most popular first)
            suggestions.sort((a, b) -> Integer.compare(b.risingQueries.size(), a.risingQueries.size()));
        }
        
        synchronized (nicheDataMap) {
            List<RisingQueryAdapter.RisingItem> allItems = nicheDataMap.getOrDefault(baseKeyword, new ArrayList<>());
            // Deduplicate
            Set<String> seen = new HashSet<>();
            List<RisingQueryAdapter.RisingItem> uniqueItems = new ArrayList<>();
            for (RisingQueryAdapter.RisingItem item : allItems) {
                if (!seen.contains(item.query)) {
                    seen.add(item.query);
                    uniqueItems.add(item);
                }
            }
            // Sort by interest descending
            uniqueItems.sort((a, b) -> Integer.parseInt(b.interest) - Integer.parseInt(a.interest));
            targetItem.risingQueries.clear();
            targetItem.risingQueries.addAll(uniqueItems);
        }
        
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onCopyClick(SuggestionAdapter.SuggestionItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 Keyword: ").append(currentKeyword).append("\n");
        sb.append("🎥 Base Query: ").append(item.suggestion).append("\n\n");
        sb.append("📈 Real-Time Related Queries:\n");
        
        if (item.risingQueries.isEmpty()) {
            sb.append("(No related queries found)");
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
        if (suggestions.isEmpty()) { Toast.makeText(this, "No data to copy", Toast.LENGTH_SHORT).show(); return; }
        
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("🎯 KEYWORD: ").append(currentKeyword).append("\n");
        sb.append("═══════════════════════════════════════\n\n");
        
        for (SuggestionAdapter.SuggestionItem item : suggestions) {
            sb.append("───────────────────────────────────────\n");
            sb.append("🎥 Base Query: ").append(item.suggestion).append("\n");
            sb.append("───────────────────────────────────────\n");
            
            if (item.risingQueries.isEmpty()) {
                sb.append("(No related queries found)\n\n");
            } else {
                sb.append("📈 Real-Time Related Queries:\n");
                sb.append("Query\tInterest\tIncrease\n");
                for (RisingQueryAdapter.RisingItem rq : item.risingQueries) {
                    sb.append(rq.query).append("\t").append(rq.interest).append("\t").append(rq.percent).append("\n");
                }
                sb.append("\n");
            }
        }
        
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("all", sb.toString()));
        Toast.makeText(this, "📋 Copied all data for \"" + currentKeyword + "\"!", Toast.LENGTH_SHORT).show();
    }
}
