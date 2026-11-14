package com.trends.photoeditingprompt;
import android.view.*;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class QueryAdapter extends RecyclerView.Adapter<QueryAdapter.VH> {
    public static class QueryItem {
        public String query, interest, percent;
        public QueryItem(String q, String i, String p) { query=q; interest=i; percent=p; }
    }
    private List<QueryItem> items;
    public QueryAdapter(List<QueryItem> items) { this.items = items; }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvQuery, tvInterest, tvPercent;
        public VH(View v) {
            super(v);
            tvQuery = v.findViewById(R.id.tvQuery);
            tvInterest = v.findViewById(R.id.tvInterest);
            tvPercent = v.findViewById(R.id.tvPercent);
        }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_query, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        QueryItem item = items.get(position);
        holder.tvQuery.setText(item.query);
        holder.tvInterest.setText(item.interest);
        holder.tvPercent.setText(item.percent);
        
        if (item.percent.contains("Breakout") || item.percent.contains("+")) {
            holder.tvPercent.setTextColor(0xFF4CAF50); // Green for positive
        } else {
            holder.tvPercent.setTextColor(0xFF9E9E9E); // Gray for negative/neutral
        }
    }

    @Override
    public int getItemCount() { return items.size(); }
}
