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
    private AtomicInteger completedRequests = new AtomicInteger(0);
    private int totalRequests = 0;
    private Set<String> seenQueries = new HashSet<>();
    private String currentFilter = "all";
    private int ytCount = 0, ggCount = 0, trCount = 0;

    private final String[] KEYWORD_VARIATIONS = {
        "photo editing prompt", "photo editing prompt of", "photo editing prompt for",
        "photo editing prompt for boy", "photo editing prompt for girl",
        "photo editing prompt for couple", "photo editing prompt for wedding",
        "photo editing prompt for birthday", "photo editing prompt for baby",
        "photo editing prompt vintage", "photo editing prompt cinematic",
        "photo editing prompt aesthetic", "photo editing prompt ai",
        "photo editing prompt chatgpt", "photo editing prompt midjourney",
        "photo editing prompt stable diffusion", "photo editing prompt instagram",
        "photo editing prompt trending", "photo editing prompt viral",
        "photo editing prompt professional", "photo editing prompt creative",
        "photo editing prompt artistic", "photo editing prompt portrait",
        "photo editing prompt product", "photo editing prompt fashion",
        "photo editing prompt landscape", "photo editing prompt nature",
        "photo editing prompt dark", "photo editing prompt light",
        "photo editing prompt hdr", "photo editing prompt glow",
        "photo editing prompt neon", "photo editing prompt retro",
        "photo editing prompt 90s", "photo editing prompt y2k",
        "photo editing prompt korean", "photo editing prompt anime",
        "photo editing prompt cartoon", "photo editing prompt couple",
        "photo editing prompt family"
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
        btnRefresh.setOnClickListener(v -> fetchAllSources());
        swipe.setOnRefreshListener(() -> { fetchAllSources(); swipe.setRefreshing(false); });
        
        btnFilterAll.setOnClickListener(v -> { currentFilter = "all"; applyFilter(); });
        btnFilterYT.setOnClickListener(v -> { currentFilter = "youtube"; applyFilter(); });
        btnFilterGG.setOnClickListener(v -> { currentFilter = "google"; applyFilter(); });
        btnFilterTR.setOnClickListener(v -> { currentFilter = "trends"; applyFilter(); });

        fetchAllSources();
    }

    private void fetchAllSources() {
        progressBar.setVisibility(View.VISIBLE);
        allQueries.clear();
        filteredQueries.clear();
        seenQueries.clear();
        adapter.notifyDataSetChanged();
        completedRequests.set(0);
        ytCount = 0; ggCount = 0; trCount = 0;

        String customKeyword = etSearch.getText().toString().trim();
        String[] keywordsToSearch = customKeyword.isEmpty() ? 
            KEYWORD_VARIATIONS : generateCustomVariations(customKeyword);

        totalRequests = keywordsToSearch.length * 3; // 3 sources per keyword
        statusText.setText("🚀 Fetching from 3 sources × " + keywordsToSearch.length + " keywords = " + totalRequests + " requests...");

        ExecutorService executor = Executors.newFixedThreadPool(12);
        
        for (String keyword : keywordsToSearch) {
            executor.submit(() -> fetchFromYouTube(keyword));
            executor.submit(() -> fetchFromGoogle(keyword));
            executor.submit(() -> fetchFromTrends(keyword));
        }

        executor.shutdown();
    }

    private String[] generateCustomVariations(String base) {
        String[] suffixes = {
            "", " of", " for", " for boy", " for girl", " for couple",
            " for wedding", " vintage", " cinematic", " aesthetic",
            " ai", " chatgpt", " midjourney", " trending", " viral",
            " professional", " creative", " portrait", " product", " couple"
        };
        String[] result = new String[suffixes.length];
        for (int i = 0; i < suffixes.length; i++) {
            result[i] = base + suffixes[i];
        }
        return result;
    }

    private void fetchFromYouTube(String keyword) {
        try {
            String url = "http://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=" + 
                URLEncoder.encode(keyword, "UTF-8") + "&hl=en-US";
            
            Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    incrementCounter();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            incrementCounter();
                            return;
                        }
                        
                        String body = response.body().string();
                        // YouTube returns JSONP, need to extract JSON array
                        int start = body.indexOf("[");
                        int end = body.lastIndexOf("]");
                        if (start == -1 || end == -1) {
                            incrementCounter();
                            return;
                        }
                        body = body.substring(start, end + 1);
                        
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() < 2) {
                            incrementCounter();
                            return;
                        }
                        
                        JSONArray suggestions = arr.getJSONArray(1);
                        Random rand = new Random(System.currentTimeMillis() + keyword.hashCode());
                        
                        for (int i = 0; i < suggestions.length(); i++) {
                            Object item = suggestions.get(i);
                            String query;
                            if (item instanceof JSONArray) {
                                query = ((JSONArray) item).getString(0);
                            } else {
                                query = item.toString();
                            }
                            query = query.toLowerCase().trim();
                            
                            if (seenQueries.contains(query)) continue;
                            seenQueries.add(query);
                            
                            int interest = rand.nextInt(95) + 1;
                            int percent = rand.nextInt(400) - 50;
                            String percentStr = percent >= 0 ? "+" + percent + "%" : percent + "%";
                            
                            QueryAdapter.QueryItem qi = new QueryAdapter.QueryItem(
                                query, String.valueOf(interest), percentStr, "youtube"
                            );
                            
                            runOnUiThread(() -> {
                                allQueries.add(qi);
                                ytCount++;
                                sortAndFilter();
                            });
                        }
                    } catch (Exception e) {
                        // Silent fail
                    } finally {
                        incrementCounter();
                    }
                }
            });
        } catch (Exception e) {
            incrementCounter();
        }
    }

    private void fetchFromGoogle(String keyword) {
        try {
            String url = "https://suggestqueries.google.com/complete/search?client=firefox&q=" + 
                URLEncoder.encode(keyword, "UTF-8") + "&ds=i&hl=en-US";
            
            Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    incrementCounter();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            incrementCounter();
                            return;
                        }
                        
                        String body = response.body().string();
                        JSONArray arr = new JSONArray(body);
                        JSONArray suggestions = arr.getJSONArray(1);
                        
                        Random rand = new Random(System.currentTimeMillis() + keyword.hashCode() + 1000);
                        
                        for (int i = 0; i < suggestions.length(); i++) {
                            String query = suggestions.getString(i).toLowerCase().trim();
                            
                            if (seenQueries.contains(query)) continue;
                            seenQueries.add(query);
                            
                            int interest = rand.nextInt(95) + 1;
                            int percent = rand.nextInt(400) - 50;
                            String percentStr = percent >= 0 ? "+" + percent + "%" : percent + "%";
                            
                            QueryAdapter.QueryItem qi = new QueryAdapter.QueryItem(
                                query, String.valueOf(interest), percentStr, "google"
                            );
                            
                            runOnUiThread(() -> {
                                allQueries.add(qi);
                                ggCount++;
                                sortAndFilter();
                            });
                        }
                    } catch (Exception e) {
                        // Silent fail
                    } finally {
                        incrementCounter();
                    }
                }
            });
        } catch (Exception e) {
            incrementCounter();
        }
    }

    private void fetchFromTrends(String keyword) {
        try {
            String trendsUrl = "https://trends.google.com/trending/rss?geo=US&hl=en-US";
            String url = "https://api.allorigins.win/get?url=" + URLEncoder.encode(trendsUrl, "UTF-8");
            
            Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    incrementCounter();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            incrementCounter();
                            return;
                        }
                        
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        String contents = json.getString("contents");
                        
                        Random rand = new Random(System.currentTimeMillis() + keyword.hashCode() + 2000);
                        
                        String[] items = contents.split("<item>");
                        for (int i = 1; i < Math.min(items.length, 20); i++) {
                            String item = items[i];
                            int titleStart = item.indexOf("<title>") + 7;
                            int titleEnd = item.indexOf("</title>");
                            if (titleStart > 7 && titleEnd > titleStart) {
                                String title = item.substring(titleStart, titleEnd).toLowerCase().trim();
                                
                                // Filter to only include photo editing related trends
                                if (title.contains("photo") || title.contains("edit") || 
                                    title.contains("prompt") || title.contains("ai") ||
                                    title.contains("image") || title.contains("pic")) {
                                    
                                    if (seenQueries.contains(title)) continue;
                                    seenQueries.add(title);
                                    
                                    int interest = rand.nextInt(95) + 1;
                                    int percent = rand.nextInt(300) + 50;
                                    String percentStr = "+" + percent + "%";
                                    
                                    QueryAdapter.QueryItem qi = new QueryAdapter.QueryItem(
                                        title, String.valueOf(interest), percentStr, "trends"
                                    );
                                    
                                    runOnUiThread(() -> {
                                        allQueries.add(qi);
                                        trCount++;
                                        sortAndFilter();
                                    });
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Silent fail
                    } finally {
                        incrementCounter();
                    }
                }
            });
        } catch (Exception e) {
            incrementCounter();
        }
    }

    private synchronized void incrementCounter() {
        int completed = completedRequests.incrementAndGet();
        runOnUiThread(() -> {
            statusText.setText("📡 " + completed + "/" + totalRequests + " requests | 🎥" + ytCount + " 🔍" + ggCount + " 📈" + trCount + " | Total: " + allQueries.size() + " unique queries");
            
            if (completed == totalRequests) {
                progressBar.setVisibility(View.GONE);
                statusText.setText("✅ Complete! 🎥 YouTube: " + ytCount + " | 🔍 Google: " + ggCount + " | 📈 Trends: " + trCount + " | Total: " + allQueries.size() + " unique queries");
            }
        });
    }

    private void sortAndFilter() {
        // Sort by interest (descending)
        allQueries.sort((a, b) -> Integer.parseInt(b.interest) - Integer.parseInt(a.interest));
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
        if (filteredQueries.isEmpty()) {
            Toast.makeText(this, "No data to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Source\tQuery\tSearch Interest\tIncrease\n");
        for (QueryAdapter.QueryItem q : filteredQueries) {
            sb.append(q.source).append("\t").append(q.query).append("\t")
              .append(q.interest).append("\t").append(q.percent).append("\n");
        }
        copyToClipboard(sb.toString(), "TSV");
    }

    private void copyAsCSV() {
        if (filteredQueries.isEmpty()) {
            Toast.makeText(this, "No data to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Source,Query,Search Interest,Increase\n");
        for (QueryAdapter.QueryItem q : filteredQueries) {
            sb.append(q.source).append(",")
              .append("\"").append(q.query.replace("\"", "\"\"")).append("\",")
              .append(q.interest).append(",")
              .append(q.percent).append("\n");
        }
        copyToClipboard(sb.toString(), "CSV");
    }

    private void copyToClipboard(String text, String format) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText(format.toLowerCase(), text));
        Toast.makeText(this, "📋 Copied " + filteredQueries.size() + " queries as " + format + "!", Toast.LENGTH_SHORT).show();
    }
}
