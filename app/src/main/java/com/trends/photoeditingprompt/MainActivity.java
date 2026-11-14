package com.trends.photoeditingprompt;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private List<String> queries = new ArrayList<>();
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
        statusText.setText("🔍 Fetching Google Trends - Rising Queries (Global, Last Week)...");
        queries.clear();
        adapter.notifyDataSetChanged();

        String url = "https://trends.google.com/trending/rss?geo=&hl=en-US";
        Request request = new Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("⚠️ Offline mode: Showing seed keywords. Error: " + e.getMessage());
                    loadFallbackData();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        statusText.setText("⚠️ HTTP " + response.code() + ". Showing seed keywords.");
                        loadFallbackData();
                    });
                    return;
                }
                String xml = response.body().string();
                Document doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
                Elements items = doc.select("item");

                final List<String> parsed = new ArrayList<>();
                for (Element item : items) {
                    String title = item.select("title").text();
                    String traffic = item.select("ht|approx_traffic", "http://www.google.com/trends/trends").text();
                    if (!title.isEmpty()) {
                        String entry = title + (traffic.isEmpty() ? "" : " 🔥" + traffic);
                        if (!parsed.contains(entry)) parsed.add(entry);
                    }
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    queries.addAll(getSeedKeywords());
                    queries.add("──────── LIVE RISING QUERIES ────────");
                    queries.addAll(parsed.isEmpty() ? Collections.singletonList("No live data. Showing seeds.") : parsed);
                    adapter.notifyDataSetChanged();
                    statusText.setText("✅ Found " + parsed.size() + " rising queries (Global • Last Week)");
                });
            }
        });
    }

    private void loadFallbackData() {
        queries.addAll(getSeedKeywords());
        adapter.notifyDataSetChanged();
    }

    private List<String> getSeedKeywords() {
        return Arrays.asList(
            "📸 photo editing prompt ideas",
            "🤖 AI photo editing prompts",
            "✨ best photo editing prompts 2026",
            "🎨 midjourney photo editing prompt",
            "🌊 stable diffusion photo prompt",
            "🖌️ photoshop generative fill prompts",
            "💡 lightroom AI edit prompts",
            "📷 photo retouching prompt examples",
            "🎞️ vintage photo editing prompt",
            "🎬 cinematic photo editing prompt",
            "👤 portrait editing AI prompt",
            "🛍️ product photo editing prompt"
        );
    }

    private void copyAll() {
        if (queries.isEmpty()) {
            Toast.makeText(this, "No data to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String q : queries) sb.append(q).append("\n");
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("queries", sb.toString()));
        Toast.makeText(this, "📋 Copied " + queries.size() + " queries!", Toast.LENGTH_SHORT).show();
    }
}
