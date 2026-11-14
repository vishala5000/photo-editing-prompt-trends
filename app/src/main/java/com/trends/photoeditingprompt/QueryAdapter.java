package com.trends.photoeditingprompt;
import android.view.*;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class QueryAdapter extends RecyclerView.Adapter<QueryAdapter.VH> {
    public static class QueryItem {
        public String query, interest, percent, source;
        public QueryItem(String q, String i, String p, String s) { 
            query=q; interest=i; percent=p; source=s; 
        }
    }
    private List<QueryItem> items;
    public QueryAdapter(List<QueryItem> items) { this.items = items; }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvSource, tvQuery, tvInterest, tvPercent;
        public VH(View v) {
            super(v);
            tvSource = v.findViewById(R.id.tvSource);
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
        
        // Source icon
        if ("youtube".equals(item.source)) {
            holder.tvSource.setText("🎥");
        } else if ("google".equals(item.source)) {
            holder.tvSource.setText("🔍");
        } else if ("trends".equals(item.source)) {
            holder.tvSource.setText("📈");
        } else {
            holder.tvSource.setText("•");
        }
        
        holder.tvQuery.setText(item.query);
        holder.tvInterest.setText(item.interest);
        holder.tvPercent.setText(item.percent);
        
        // Color coding based on increase percentage
        if (item.percent.startsWith("+")) {
            try {
                int percentVal = Integer.parseInt(item.percent.replace("+", "").replace("%", ""));
                if (percentVal >= 200) {
                    holder.tvPercent.setTextColor(0xFF1B5E20);
                } else if (percentVal >= 100) {
                    holder.tvPercent.setTextColor(0xFF2E7D32);
                } else if (percentVal >= 50) {
                    holder.tvPercent.setTextColor(0xFF4CAF50);
                } else {
                    holder.tvPercent.setTextColor(0xFF81C784);
                }
            } catch (Exception e) {
                holder.tvPercent.setTextColor(0xFF4CAF50);
            }
        } else if (item.percent.startsWith("-")) {
            holder.tvPercent.setTextColor(0xFFE57373);
        } else {
            holder.tvPercent.setTextColor(0xFF9E9E9E);
        }
        
        // Alternate row colors
        if (position % 2 == 0) {
            holder.itemView.setBackgroundColor(0xFFF5F5F5);
        } else {
            holder.itemView.setBackgroundColor(0xFFFFFFFF);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }
}
