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

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private List<QueryAdapter.QueryItem> queries = new ArrayList<>();
    private QueryAdapter adapter;
    private ProgressBar progressBar;
    private TextView statusText;
    private OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        SwipeRefreshLayout swipe = findViewById(R.id.swipeRefresh);
        Button btnCopy = findViewById(R.id.btnCopyAll);
        Button btnRefresh = findViewById(R.id.btnRefresh);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new QueryAdapter(queries);
        recyclerView.setAdapter(adapter);

        btnCopy.setOnClickListener(v -> copyAll());
        btnRefresh.setOnClickListener(v -> fetchTrends());
        swipe.setOnRefreshListener(() -> { fetchTrends(); swipe.setRefreshing(false); });

        fetchTrends();
    }

    private void fetchTrends() {
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("🔍 Fetching YouTube Rising Queries: 'photo editing prompt' (Global, 7 Days)...");
        queries.clear();
        adapter.notifyDataSetChanged();

        try {
            String keyword = "photo editing prompt";
            String jsonPayload = String.format(
                "{\"comparisonItem\":[{\"keyword\":\"%s\",\"geo\":\"\",\"time\":\"now 7-d\"}],\"category\":0,\"property\":\"youtube\"}",
                keyword
            );
            String encodedPayload = URLEncoder.encode(jsonPayload, "UTF-8");
            String url = "https://trends.google.com/trends/api/explore?hl=en-US&tz=0&req=" + encodedPayload;

            Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("⚠️ Network error. Showing fallback data.");
                        loadFallbackData();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        runOnUiThread(() -> {
                            statusText.setText("⚠️ HTTP " + response.code() + ". Showing fallback data.");
                            loadFallbackData();
                        });
                        return;
                    }
                    
                    String body = response.body().string();
                    // Strip Google's anti-XSSI prefix
                    if (body.startsWith(")]}'\n")) {
                        body = body.substring(5);
                    }
                    
                    try {
                        JSONObject root = new JSONObject(body);
                        JSONObject defaultObj = root.getJSONObject("default");
                        JSONArray rankedList = defaultObj.getJSONArray("rankedList");
                        
                        // Index 1 is RISING queries (Index 0 is TOP)
                        JSONObject risingObj = rankedList.getJSONObject(1);
                        JSONArray rankedKeywords = risingObj.getJSONArray("rankedKeyword");
                        
                        final List<QueryAdapter.QueryItem> parsed = new ArrayList<>();
                        for (int i = 0; i < rankedKeywords.length(); i++) {
                            JSONObject kw = rankedKeywords.getJSONObject(i);
                            String q = kw.getString("query");
                            String interest = kw.optString("value", "0"); // e.g., "12" or "Breakout"
                            String percent = kw.optString("formattedValue", "+0%"); // e.g., "+250%"
                            parsed.add(new QueryAdapter.QueryItem(q, interest, percent));
                        }
                        
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            queries.addAll(parsed);
                            adapter.notifyDataSetChanged();
                            statusText.setText("✅ Found " + parsed.size() + " rising queries (YouTube • Global • 7 Days)");
                        });
                    } catch (JSONException e) {
                        runOnUiThread(() -> {
                            statusText.setText("⚠️ Parse error. Showing fallback data.");
                            loadFallbackData();
                        });
                    }
                }
            });
        } catch (Exception e) {
            loadFallbackData();
        }
    }

    private void loadFallbackData() {
        progressBar.setVisibility(View.GONE);
        queries.clear();
        queries.add(new QueryAdapter.QueryItem("diwali chatgpt prompt", "5", "+250%"));
        queries.add(new QueryAdapter.QueryItem("diwali prompt", "12", "+200%"));
        queries.add(new QueryAdapter.QueryItem("ai diwali photo editing", "3", "+150%"));
        queries.add(new QueryAdapter.QueryItem("chat gpt trend photo prompt", "3", "+80%"));
        queries.add(new QueryAdapter.QueryItem("instagram photo editing", "7", "+10%"));
        queries.add(new QueryAdapter.QueryItem("photo editing chatgpt", "94", "+4%"));
        queries.add(new QueryAdapter.QueryItem("best prompt for photo editing", "1", "+3%"));
        queries.add(new QueryAdapter.QueryItem("chatgpt prompt for photo editing", "14", "+3%"));
        queries.add(new QueryAdapter.QueryItem("couple photo editing prompt", "17", "+2%"));
        queries.add(new QueryAdapter.QueryItem("gemini ai photo editing", "9", "-3%"));
        queries.add(new QueryAdapter.QueryItem("ai photo editing prompt", "36", "-10%"));
        queries.add(new QueryAdapter.QueryItem("trending photo editing prompt", "22", "-4%"));
        adapter.notifyDataSetChanged();
    }

    private void copyAll() {
        if (queries.isEmpty()) {
            Toast.makeText(this, "No data to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Query\tSearch Interest\tIncrease\n");
        for (QueryAdapter.QueryItem q : queries) {
            sb.append(q.query).append("\t").append(q.interest).append("\t").append(q.percent).append("\n");
        }
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("queries", sb.toString()));
        Toast.makeText(this, "📋 Copied " + queries.size() + " queries to clipboard!", Toast.LENGTH_SHORT).show();
    }
}
