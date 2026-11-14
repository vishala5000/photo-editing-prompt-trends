package com.trends.photoeditingprompt;
import android.view.*;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class RisingQueryAdapter extends RecyclerView.Adapter<RisingQueryAdapter.VH> {
    public static class RisingItem {
        public String query, interest, percent;
        public RisingItem(String q, String i, String p) { query=q; interest=i; percent=p; }
    }
    private List<RisingItem> items;
    public RisingQueryAdapter(List<RisingItem> items) { this.items = items; }

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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rising_query, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        RisingItem item = items.get(position);
        holder.tvQuery.setText(item.query);
        holder.tvInterest.setText(item.interest);
        holder.tvPercent.setText(item.percent);
        
        if (item.percent.startsWith("+")) {
            holder.tvPercent.setTextColor(0xFF4CAF50);
        } else if (item.percent.startsWith("-")) {
            holder.tvPercent.setTextColor(0xFFE57373);
        } else {
            holder.tvPercent.setTextColor(0xFF9E9E9E);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }
}
