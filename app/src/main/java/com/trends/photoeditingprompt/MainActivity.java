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

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private List<QueryAdapter.QueryItem> allQueries = new ArrayList<>();
    private List<QueryAdapter.QueryItem> filteredQueries = new ArrayList<>();
    private QueryAdapter adapter;
    private ProgressBar progressBar;
    private TextView statusText;
    private EditText etSearch;
    private OkHttpClient client = new OkHttpClient();
    private Set<String> seenQueries = new HashSet<>();
    private Set<String> youtubeSuggestions = new HashSet<>();
    private String currentFilter = "all";
    private int step1Count = 0, step2Count = 0;
    private boolean step1Complete = false;

    private final String[] KEYWORD_VARIATIONS = {
        "photo editing prompt for boy", "photo editing prompt for girl",
        "photo editing prompt for couple", "photo editing prompt for baby",
        "photo editing prompt for newborn", "photo editing prompt for family",
        "photo editing prompt for wedding", "photo editing prompt for bride",
        "photo editing prompt for birthday", "photo editing prompt for portrait",
        "photo editing prompt for selfie", "photo editing prompt for influencer",
        "photo editing prompt for model", "photo editing prompt for professional headshot",
        "photo editing prompt for face retouching", "photo editing prompt for skin smoothing",
        "photo editing prompt for cinematic portrait", "photo editing prompt for vintage portrait",
        "photo editing prompt for man", "photo editing prompt for woman",
        "photo editing prompt for child", "photo editing prompt for group photo",
        "photo editing prompt for linkedin profile", "photo editing prompt for dating profile",
        "photo editing prompt for passport photo", "photo editing prompt for teeth whitening",
        "photo editing prompt for eye enhancement", "photo editing prompt for hair color",
        "photo editing prompt for background removal", "photo editing prompt for aesthetic selfie",
        "photo editing prompt for maternity", "photo editing prompt for graduation",
        "photo editing prompt for senior pictures", "photo editing prompt for toddler",
        "photo editing prompt for friends", "photo editing prompt for groom",
        "photo editing prompt for corporate headshot", "photo editing prompt for dramatic lighting",
        "photo editing prompt for glowing skin", "photo editing prompt for human portrait"
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
        Button btnCopyTSV = findViewById(R.id.btnCopyTSV);
        Button btnCopyCSV = findViewById(R.id.btnCopyCSV);
        Button btnRefresh = findViewById(R.id.btnRefresh);
        Button btnFilterAll = findViewById(R.id.btnFilterAll);
        Button btnFilterYT = findViewById(R.id.btnFilterYT);
        Button btnFilterGG = findViewById(R.id.btnFilterGG);
        Button btnFilterTR = findViewById(R.id.btnFilterTR);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new QueryAdapter(filteredQueries);
        recyclerView.setAdapter(adapter);

        btnCopyTSV.setOnClickListener(v -> copyAsTSV());
        btnCopyCSV.setOnClickListener(v -> copyAsCSV());
        btnRefresh.setOnClickListener(v -> startPipeline());
        swipe.setOnRefreshListener(() -> { startPipeline(); swipe.setRefreshing(false); });
        
        btnFilterAll.setOnClickListener(v -> { currentFilter = "all"; applyFilter(); });
        btnFilterYT.setOnClickListener(v -> { currentFilter = "youtube"; applyFilter(); });
        btnFilterGG.setOnClickListener(v -> { currentFilter = "google"; applyFilter(); });
        btnFilterTR.setOnClickListener(v -> { currentFilter = "trends"; applyFilter(); });

        startPipeline();
    }

    private void startPipeline() {
        progressBar.setVisibility(View.VISIBLE);
        allQueries.clear();
        filteredQueries.clear();
        seenQueries.clear();
        youtubeSuggestions.clear();
        adapter.notifyDataSetChanged();
        step1Count = 0;
        step2Count = 0;
        step1Complete = false;

        String customKeyword = etSearch.getText().toString().trim();
        String[] keywordsToSearch = customKeyword.isEmpty() ? 
            KEYWORD_VARIATIONS : generateHumanVariations(customKeyword);

        statusText.setText("🎥 STEP 1: Fetching YouTube suggestions from " + keywordsToSearch.length + " keywords...");

        // STEP 1: Get YouTube suggestions
        ExecutorService executor = Executors.newFixedThreadPool(12);
        for (String keyword : keywordsToSearch) {
            executor.submit(() -> fetchYouTubeSuggestions(keyword));
        }
        executor.shutdown();
    }

    private String[] generateHumanVariations(String base) {
        String[] suffixes = {
            " for boy", " for girl", " for couple", " for baby", " for family",
            " for wedding", " for birthday", " for portrait", " for selfie",
            " for influencer", " for model", " for professional headshot",
            " for face retouching", " for skin smoothing", " cinematic portrait",
            " vintage portrait", " aesthetic selfie", " for linkedin profile",
            " for dating profile", " for group photo"
        };
        String[] result = new String[suffixes.length];
        for (int i = 0; i < suffixes.length; i++) {
            result[i] = base + suffixes[i];
        }
        return result;
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
                        JSONArray suggestions = arr.getJSONArray(1);
                        
                        for (int i = 0; i < suggestions.length(); i++) {
                            Object item = suggestions.get(i);
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
        if (step1Count >= KEYWORD_VARIATIONS.length && !step1Complete) {
            step1Complete = true;
            runOnUiThread(() -> {
                statusText.setText("✅ STEP 1 Complete! Found " + youtubeSuggestions.size() + " YouTube suggestions. Starting STEP 2...");
                startStep2();
            });
        } else {
            runOnUiThread(() -> {
                statusText.setText("🎥 STEP 1: " + step1Count + "/" + KEYWORD_VARIATIONS.length + " keywords queried... " + youtubeSuggestions.size() + " suggestions so far");
            });
        }
    }

    private void startStep2() {
        statusText.setText("📈 STEP 2: Searching " + youtubeSuggestions.size() + " YouTube suggestions in Google Trends...");
        
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
            
            // Use CORS proxy to fetch Google Trends data
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
                        
                        // Strip Google's anti-XSSI prefix
                        if (contents.startsWith(")]}'\n")) {
                            contents = contents.substring(5);
                        }
                        
                        JSONObject root = new JSONObject(contents);
                        JSONObject defaultObj = root.getJSONObject("default");
                        JSONArray rankedList = defaultObj.getJSONArray("rankedList");
                        
                        // Index 1 is RISING queries
                        if (rankedList.length() > 1) {
                            JSONObject risingObj = rankedList.getJSONObject(1);
                            JSONArray rankedKeywords = risingObj.getJSONArray("rankedKeyword");
                            
                            for (int i = 0; i < rankedKeywords.length(); i++) {
                                JSONObject kw = rankedKeywords.getJSONObject(i);
                                String q = kw.getString("query");
                                String interest = kw.optString("value", "0");
                                String percent = kw.optString("formattedValue", "+0%");
                                
                                String fullQuery = keyword + " → " + q;
                                if (seenQueries.contains(fullQuery)) continue;
                                seenQueries.add(fullQuery);
                                
                                QueryAdapter.QueryItem qi = new QueryAdapter.QueryItem(
                                    fullQuery, interest, percent, "trends"
                                );
                                
                                runOnUiThread(() -> {
                                    allQueries.add(qi);
                                    step2Count++;
                                    sortAndFilter();
                                });
                            }
                        }
                    } catch (Exception e) {} finally { checkStep2Complete(); }
                }
            });
        } catch (Exception e) { checkStep2Complete(); }
    }

    private synchronized void checkStep2Complete() {
        step2Count++;
        runOnUiThread(() -> {
            statusText.setText("📈 STEP 2: " + step2Count + "/" + youtubeSuggestions.size() + " YouTube suggestions searched in Google Trends... " + allQueries.size() + " rising queries found");
            if (step2Count >= youtubeSuggestions.size()) {
                progressBar.setVisibility(View.GONE);
                statusText.setText("✅ Pipeline Complete! Found " + allQueries.size() + " rising queries from " + youtubeSuggestions.size() + " YouTube suggestions");
            }
        });
    }

    private void sortAndFilter() {
        allQueries.sort((a, b) -> {
            try {
                String aVal = a.percent.replace("+", "").replace("%", "").replace(",", "");
                String bVal = b.percent.replace("+", "").replace("%", "").replace(",", "");
                if (aVal.equals("Breakout")) return -1;
                if (bVal.equals("Breakout")) return 1;
                int aInt = Integer.parseInt(aVal);
                int bInt = Integer.parseInt(bVal);
                return bInt - aInt;
            } catch (Exception e) {
                return 0;
            }
        });
        applyFilter();
    }

    private void applyFilter() {
        filteredQueries.clear();
        for (QueryAdapter.QueryItem item : allQueries) {
            if ("all".equals(currentFilter) || currentFilter.equals(item.source)) {
                filteredQueries.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void copyAsTSV() {
        if (filteredQueries.isEmpty()) { Toast.makeText(this, "No data", Toast.LENGTH_SHORT).show(); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("YouTube Suggestion → Rising Query\tSearch Interest\tIncrease\n");
        for (QueryAdapter.QueryItem q : filteredQueries) {
            sb.append(q.query).append("\t").append(q.interest).append("\t").append(q.percent).append("\n");
        }
        copyToClipboard(sb.toString(), "TSV");
    }

    private void copyAsCSV() {
        if (filteredQueries.isEmpty()) { Toast.makeText(this, "No data", Toast.LENGTH_SHORT).show(); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("YouTube Suggestion → Rising Query,Search Interest,Increase\n");
        for (QueryAdapter.QueryItem q : filteredQueries) {
            sb.append("\"").append(q.query.replace("\"", "\"\"")).append("\",").append(q.interest).append(",").append(q.percent).append("\n");
        }
        copyToClipboard(sb.toString(), "CSV");
    }

    private void copyToClipboard(String text, String format) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText(format.toLowerCase(), text));
        Toast.makeText(this, "📋 Copied " + filteredQueries.size() + " rising queries!", Toast.LENGTH_SHORT).show();
    }
}
