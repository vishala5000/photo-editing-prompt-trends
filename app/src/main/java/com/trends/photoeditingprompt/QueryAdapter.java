package com.trends.photoeditingprompt;
import android.view.*;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class QueryAdapter extends RecyclerView.Adapter<QueryAdapter.VH> {
    private List<String> items;
    public QueryAdapter(List<String> items) { this.items = items; }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tv;
        public VH(View v) { super(v); tv = v.findViewById(android.R.id.text1); }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        holder.tv.setText(items.get(position));
        holder.tv.setTextSize(15);
        holder.tv.setPadding(24, 18, 24, 18);
        
        if (items.get(position).contains("──")) {
            holder.tv.setTypeface(null, android.graphics.Typeface.BOLD);
            holder.tv.setTextColor(android.graphics.Color.parseColor("#1976D2"));
        } else {
            holder.tv.setTypeface(null, android.graphics.Typeface.NORMAL);
            holder.tv.setTextColor(android.graphics.Color.parseColor("#212121"));
        }
    }

    @Override
    public int getItemCount() { return items.size(); }
}
