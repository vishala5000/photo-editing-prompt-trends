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
    private TextView statusText, tvActiveNiche;
    private EditText etSearch;
    private OkHttpClient client = new OkHttpClient();
    private Set<String> youtubeSuggestions = new HashSet<>();
    private Map<String, List<RisingQueryAdapter.RisingItem>> risingQueriesMap = new ConcurrentHashMap<>();
    private int step1Count = 0, step2Count = 0, totalStep1 = 0;
    private boolean step1Complete = false;
    private String currentNiche = "photo editing prompt";
    private boolean allExpanded = false;

    // UNIVERSAL suffixes that work for ANY niche
    private final String[] UNIVERSAL_SUFFIXES = {
        "", " for beginners", " tutorial", " tips", " ideas", " 2026",
        " easy", " advanced", " at home", " professional", " viral",
        " trending", " how to", " best", " top", " step by step",
        " for youtube", " aesthetic", " cinematic", " creative",
        " for boys", " for girls", " for couples", " for kids",
        " for adults", " for seniors", " quick", " simple",
        " no equipment", " budget", " premium", " free"
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

        // Preset buttons
        setupPresetButton(R.id.btnPreset1, "photo editing prompt");
        setupPresetButton(R.id.btnPreset2, "cooking recipes");
        setupPresetButton(R.id.btnPreset3, "fitness workout");
        setupPresetButton(R.id.btnPreset4, "gaming");
        setupPresetButton(R.id.btnPreset5, "travel vlog");
        setupPresetButton(R.id.btnPreset6, "makeup tutorial");
        setupPresetButton(R.id.btnPreset7, "digital art");

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SuggestionAdapter(suggestions, this);
        recyclerView.setAdapter(adapter);

        btnCopyAll.setOnClickListener(v -> copyAll());
        btnExpandAll.setOnClickListener(v -> toggleExpandAll());
        btnRefresh.setOnClickListener(v -> startPipeline());
        swipe.setOnRefreshListener(() -> { startPipeline(); swipe.setRefreshing(false); });

        // Start with default niche
        etSearch.setText("photo editing prompt");
        startPipeline();
    }

    private void setupPresetButton(int id, String niche) {
        findViewById(id).setOnClickListener(v -> {
            etSearch.setText(niche);
            startPipeline();
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
        String niche = etSearch.getText().toString().trim();
        if (niche.isEmpty()) {
            Toast.makeText(this, "⚠️ Please enter a niche keyword", Toast.LENGTH_SHORT).show();
            return;
        }
        
        currentNiche = niche;
        tvActiveNiche.setText("🎯 Active Niche: " + currentNiche);
        
        progressBar.setVisibility(View.VISIBLE);
        suggestions.clear();
        youtubeSuggestions.clear();
        risingQueriesMap.clear();
        adapter.notifyDataSetChanged();
        step1Count = 0;
        step2Count = 0;
        step1Complete = false;
        allExpanded = false;

        // Generate universal keyword variations
        String[] keywordsToSearch = generateUniversalVariations(niche);
        totalStep1 = keywordsToSearch.length;

        statusText.setText("🎥 STEP 1: Fetching YouTube suggestions for \"" + niche + "\" (" + totalStep1 + " variations)...");

        ExecutorService executor = Executors.newFixedThreadPool(12);
        for (String keyword : keywordsToSearch) {
            executor.submit(() -> fetchYouTubeSuggestions(keyword));
        }
        executor.shutdown();
    }

    private String[] generateUniversalVariations(String niche) {
        List<String> variations = new ArrayList<>();
        variations.add(niche); // The base keyword itself
        
        for (String suffix : UNIVERSAL_SUFFIXES) {
            variations.add(niche + suffix);
        }
        
        // Add "how to" variations
        variations.add("how to " + niche);
        variations.add("best " + niche);
        variations.add(niche + " ideas");
        variations.add(niche + " tips");
        variations.add(niche + " tricks");
        variations.add(niche + " hacks");
        variations.add(niche + " challenge");
        
        return variations.toArray(new String[0]);
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
                public void onFailure(Call call, IOException e) { checkStep1Complete(); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) { checkStep1Complete(); return; }
                        String body = response.body().string();
                        int start = body.indexOf("[");
                        int end = body.lastIndexOf("]");
                        if (start == -1 || end == -1) { checkStep1Complete(); return; }
                        body = body.substring(start, end + 1);
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() < 2) { checkStep1Complete(); return; }
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
                    } catch (Exception e) {} finally { checkStep1Complete(); }
                }
            });
        } catch (Exception e) { checkStep1Complete(); }
    }

    private synchronized void checkStep1Complete() {
        step1Count++;
        if (step1Count >= totalStep1 && !step1Complete) {
            step1Complete = true;
            runOnUiThread(() -> {
                statusText.setText("✅ STEP 1 Complete! Found " + youtubeSuggestions.size() + " YouTube suggestions for \"" + currentNiche + "\". Starting STEP 2...");
                populateSuggestionsList();
                startStep2();
            });
        } else {
            runOnUiThread(() -> {
                statusText.setText("🎥 STEP 1: " + step1Count + "/" + totalStep1 + " | Found " + youtubeSuggestions.size() + " suggestions for \"" + currentNiche + "\"");
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
            statusText.setText("📈 STEP 2: " + step2Count + "/" + youtubeSuggestions.size() + " | \"" + currentNiche + "\" | " + risingQueriesMap.size() + " with rising queries");
            if (step2Count >= youtubeSuggestions.size()) {
                progressBar.setVisibility(View.GONE);
                statusText.setText("✅ Complete! Niche: \"" + currentNiche + "\" | " + suggestions.size() + " suggestions, " + risingQueriesMap.size() + " with rising queries");
            }
        });
    }

    @Override
    public void onCopyClick(SuggestionAdapter.SuggestionItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 Niche: ").append(currentNiche).append("\n");
        sb.append("🎥 YouTube Suggestion: ").append(item.suggestion).append("\n\n");
        sb.append("📈 Rising Queries (Past 7 Days):\n");
        
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
        sb.append("═══════════════════════════════════════\n");
        sb.append("🎯 NICHE: ").append(currentNiche).append("\n");
        sb.append("═══════════════════════════════════════\n\n");
        
        for (SuggestionAdapter.SuggestionItem item : suggestions) {
            sb.append("───────────────────────────────────────\n");
            sb.append("🎥 YouTube Suggestion: ").append(item.suggestion).append("\n");
            sb.append("───────────────────────────────────────\n");
            
            if (item.risingQueries.isEmpty()) {
                sb.append("(No rising queries found)\n\n");
            } else {
                sb.append("📈 Rising Queries:\n");
                sb.append("Query\tInterest\tIncrease\n");
                for (RisingQueryAdapter.RisingItem rq : item.risingQueries) {
                    sb.append(rq.query).append("\t").append(rq.interest).append("\t").append(rq.percent).append("\n");
                }
                sb.append("\n");
            }
        }
        
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("all", sb.toString()));
        Toast.makeText(this, "📋 Copied all " + suggestions.size() + " suggestions for \"" + currentNiche + "\"!", Toast.LENGTH_SHORT).show();
    }
}
