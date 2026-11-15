package com.trends.photoeditingprompt;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;

public class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.VH> {
    public static class VideoInfo {
        public String title;
        public long views;
        
        public VideoInfo(String t, long v) {
            title = t;
            views = v;
        }
        
        public boolean passesRules() {
            return views < 50000;
        }
    }
    
    public static class SuggestionItem {
        public String suggestion;
        public List<RisingQueryAdapter.RisingItem> risingQueries = new ArrayList<>();
        public boolean isExpanded = false;
        public List<VideoInfo> checkedVideos = new ArrayList<>();
        public boolean videoCheckPassed = false;
        public boolean videoCheckDone = false;
        public String checkStatus = "⏳ Checking videos...";
        
        public SuggestionItem(String s) { suggestion = s; }
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
        TextView tvSuggestion, tvVideoStats;
        Button btnCopy;
        LinearLayout layoutRising;
        RecyclerView rvRising;
        
        public VH(View v) {
            super(v);
            ivExpand = v.findViewById(R.id.ivExpand);
            tvSuggestion = v.findViewById(R.id.tvSuggestion);
            tvVideoStats = v.findViewById(R.id.tvVideoStats);
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
        holder.tvVideoStats.setText(item.checkStatus);
        
        if (item.videoCheckDone) {
            if (item.videoCheckPassed) {
                holder.tvVideoStats.setTextColor(0xFF2E7D32);
            } else {
                holder.tvVideoStats.setTextColor(0xFFC62828);
            }
        } else {
            holder.tvVideoStats.setTextColor(0xFF757575);
        }
        
        boolean hasRising = !item.risingQueries.isEmpty();
        holder.itemView.setOnClickListener(v -> {
            if (hasRising) {
                item.isExpanded = !item.isExpanded;
                holder.layoutRising.setVisibility(item.isExpanded ? View.VISIBLE : View.GONE);
                holder.ivExpand.setRotation(item.isExpanded ? 180 : 0);
            }
        });
        
        holder.btnCopy.setOnClickListener(v -> {
            if (copyListener != null) {
                copyListener.onCopyClick(item);
            }
        });
        
        if (hasRising) {
            holder.rvRising.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
            holder.rvRising.setAdapter(new RisingQueryAdapter(item.risingQueries));
            holder.rvRising.setNestedScrollingEnabled(false);
        }
        
        holder.ivExpand.setVisibility(hasRising ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public int getItemCount() { return items.size(); }
}
