package com.trends.photoeditingprompt;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;

public class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.VH> {
    public static class SuggestionItem {
        public String suggestion;
        public List<RisingQueryAdapter.RisingItem> risingQueries;
        public boolean isExpanded = false;
        
        public SuggestionItem(String s) {
            suggestion = s;
            risingQueries = new ArrayList<>();
        }
    }
    
    private List<SuggestionItem> items;
    private OnCopyClickListener copyListener;
    
    public interface OnCopyClickListener {
        void onCopyClick(SuggestionItem item);
    }
    
    public SuggestionAdapter(List<SuggestionItem> items, OnCopyClickListener listener) {
        this.items = items;
        this.copyListener = listener;
    }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView ivExpand;
        TextView tvSuggestion;
        Button btnCopy;
        LinearLayout layoutRising;
        RecyclerView rvRising;
        
        public VH(View v) {
            super(v);
            ivExpand = v.findViewById(R.id.ivExpand);
            tvSuggestion = v.findViewById(R.id.tvSuggestion);
            btnCopy = v.findViewById(R.id.btnCopy);
            layoutRising = v.findViewById(R.id.layoutRising);
            rvRising = v.findViewById(R.id.rvRising);
        }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_suggestion, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        SuggestionItem item = items.get(position);
        
        holder.tvSuggestion.setText(item.suggestion);
        
        // Expand/Collapse logic
        holder.itemView.setOnClickListener(v -> {
            item.isExpanded = !item.isExpanded;
            holder.layoutRising.setVisibility(item.isExpanded ? View.VISIBLE : View.GONE);
            holder.ivExpand.setRotation(item.isExpanded ? 180 : 0);
        });
        
        // Copy button
        holder.btnCopy.setOnClickListener(v -> {
            if (copyListener != null) {
                copyListener.onCopyClick(item);
            }
        });
        
        // Rising queries RecyclerView
        if (item.risingQueries.size() > 0) {
            holder.rvRising.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
            holder.rvRising.setAdapter(new RisingQueryAdapter(item.risingQueries));
            holder.rvRising.setNestedScrollingEnabled(false);
        }
        
        // Show expand arrow only if has rising queries
        holder.ivExpand.setVisibility(item.risingQueries.size() > 0 ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public int getItemCount() { return items.size(); }
}
