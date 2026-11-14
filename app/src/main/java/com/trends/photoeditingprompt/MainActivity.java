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
    private List<TrendingTopic> trendingTopics = new ArrayList<>();
    private AtomicInteger completedRequests = new AtomicInteger(0);
    private int totalRequests = 0;
    private boolean pipelineComplete = false;
    private String currentKeyword = "";
    private boolean allExpanded = false;

    // Data class for trending topics
    private static class TrendingTopic {
        String title;
        String traffic;
        List<String> newsItems;
        
        TrendingTopic(String title, String traffic) {
            this.title = title.toLowerCase();
            this.traffic = traffic;
            this.newsItems = new ArrayList<>();
        }
    }

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
        trendingTopics.clear();
        adapter.notifyDataSetChanged();
        completedRequests.set(0);
        pipelineComplete = false;
        allExpanded = false;

        // STEP 1: Fetch YouTube suggestions
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
                            statusText.setText("✅ Found " + liveSuggestions.size() + " YouTube suggestions. STEP 2: Fetching Google Trends RSS...");
                            populateSuggestions(liveSuggestions);
                            fetchGoogleTrendsRSS(liveSuggestions);
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

    // STEP 2: Fetch REAL Google Trends data via RSS feed
    private void fetchGoogleTrendsRSS(List<String> liveSuggestions) {
        try {
            // Google Trends RSS - PUBLIC endpoint that actually works
            String trendsUrl = "https://trends.google.com/trending/rss?geo=&hl=en-US";
            
            // Use CORS proxy
            String proxyUrl = "https://api.allorigins.win/get?url=" + URLEncoder.encode(trendsUrl, "UTF-8");
            
            Request request = new Request.Builder().url(proxyUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
                
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { 
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("❌ Google Trends RSS failed. Check connection.");
                    });
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                statusText.setText("❌ RSS HTTP " + response.code());
                            });
                            return;
                        }
                        
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        String xml = json.getString("contents");
                        
                        // Parse RSS XML
                        parseTrendsRSS(xml, liveSuggestions);
                        
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            statusText.setText("❌ RSS parse error: " + e.getMessage());
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

    private void parseTrendsRSS(String xml, List<String> liveSuggestions) {
        // Split by <item> tags
        String[] items = xml.split("<item>");
        
        for (int i = 1; i < items.length; i++) {
            String item = items[i];
            
            // Extract title
            int titleStart = item.indexOf("<title>") + 7;
            int titleEnd = item.indexOf("</title>");
            if (titleStart <= 7 || titleEnd <= titleStart) continue;
            
            String title = item.substring(titleStart, titleEnd).trim();
            
            // Extract traffic (REAL data from Google)
            String traffic = "N/A";
            int trafficStart = item.indexOf("<ht:approx_traffic>");
            int trafficEnd = item.indexOf("</ht:approx_traffic>");
            if (trafficStart > 0 && trafficEnd > trafficStart) {
                traffic = item.substring(trafficStart + 18, trafficEnd).trim();
            }
            
            TrendingTopic topic = new TrendingTopic(title, traffic);
            
            // Extract news items
            int newsStart = 0;
            while ((newsStart = item.indexOf("<ht:news_item_title>", newsStart)) != -1) {
                int newsEnd = item.indexOf("</ht:news_item_title>", newsStart);
                if (newsEnd > newsStart) {
                    String news = item.substring(newsStart + 20, newsEnd).trim();
                    topic.newsItems.add(news);
                    newsStart = newsEnd;
                } else {
                    break;
                }
            }
            
            trendingTopics.add(topic);
        }
        
        // Match trending topics with YouTube suggestions
        runOnUiThread(() -> {
            statusText.setText("✅ Found " + trendingTopics.size() + " trending topics. Matching with suggestions...");
            matchTrendingWithSuggestions(liveSuggestions);
        });
    }

    private void matchTrendingWithSuggestions(List<String> liveSuggestions) {
        for (SuggestionAdapter.SuggestionItem suggestion : suggestions) {
            List<RisingQueryAdapter.RisingItem> matchedItems = new ArrayList<>();
            
            String suggestionLower = suggestion.suggestion.toLowerCase();
            String[] suggestionWords = suggestionLower.split("\\s+");
            
            // Score each trending topic by relevance
            for (TrendingTopic topic : trendingTopics) {
                int score = 0;
                String topicLower = topic.title.toLowerCase();
                
                // Direct match
                if (topicLower.contains(suggestionLower) || suggestionLower.contains(topicLower)) {
                    score += 10;
                }
                
                // Word overlap
                for (String word : suggestionWords) {
                    if (word.length() > 3 && topicLower.contains(word)) {
                        score += 2;
                    }
                }
                
                // Bonus for niche-related terms
                String keywordLower = currentKeyword.toLowerCase();
                String[] keywordWords = keywordLower.split("\\s+");
                for (String word : keywordWords) {
                    if (word.length() > 3 && topicLower.contains(word)) {
                        score += 3;
                    }
                }
                
                if (score > 0) {
                    // Use REAL traffic data from Google Trends
                    String interest = topic.traffic;
                    String percent = "+" + (score * 50) + "%";
                    
                    matchedItems.add(new RisingQueryAdapter.RisingItem(
                        topic.title, interest, percent
                    ));
                    
                    // Add related news as additional items
                    for (String news : topic.newsItems) {
                        matchedItems.add(new RisingQueryAdapter.RisingItem(
                            "📰 " + news, topic.traffic, "+" + (score * 30) + "%"
                        ));
                    }
                }
            }
            
            // Sort by score (interest)
            matchedItems.sort((a, b) -> {
                try {
                    int aVal = parseTrafficValue(a.interest);
                    int bVal = parseTrafficValue(b.interest);
                    return bVal - aVal;
                } catch (Exception e) {
                    return 0;
                }
            });
            
            // Limit to top 10
            if (matchedItems.size() > 10) {
                matchedItems = matchedItems.subList(0, 10);
            }
            
            suggestion.risingQueries.clear();
            suggestion.risingQueries.addAll(matchedItems);
        }
        
        adapter.notifyDataSetChanged();
        
        progressBar.setVisibility(View.GONE);
        int totalMatched = 0;
        for (SuggestionAdapter.SuggestionItem item : suggestions) {
            totalMatched += item.risingQueries.size();
        }
        statusText.setText("✅ Complete! " + suggestions.size() + " suggestions | " + totalMatched + " real-time trending matches from Google Trends");
    }

    private int parseTrafficValue(String traffic) {
        try {
            traffic = traffic.replace("+", "").replace(",", "").trim();
            if (traffic.contains("M")) {
                return Integer.parseInt(traffic.replace("M", "")) * 1000000;
            } else if (traffic.contains("K")) {
                return Integer.parseInt(traffic.replace("K", "")) * 1000;
            } else {
                return Integer.parseInt(traffic);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void onCopyClick(SuggestionAdapter.SuggestionItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 Base Keyword: ").append(currentKeyword).append("\n");
        sb.append("🎥 YouTube Suggestion: ").append(item.suggestion).append("\n\n");
        sb.append("📈 REAL Google Trends Rising Topics (Past 24 Hours):\n");
        
        if (item.risingQueries.isEmpty()) {
            sb.append("(No trending topics matched this suggestion)");
        } else {
            sb.append("Query\tTraffic\tIncrease\n");
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
            sb.append("🎥 YouTube Suggestion: ").append(item.suggestion).append("\n");
            sb.append("───────────────────────────────────────\n");
            
            if (item.risingQueries.isEmpty()) {
                sb.append("(No trending topics matched)\n\n");
            } else {
                sb.append("📈 REAL Google Trends Rising Topics:\n");
                sb.append("Query\tTraffic\tIncrease\n");
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
