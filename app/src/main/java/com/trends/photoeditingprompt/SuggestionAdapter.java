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
        public long uploaded;
        public String channelName;
        public long subscribers;
        public VideoInfo(String t, long v, long u, String c, long s) {
            title=t; views=v; uploaded=u; channelName=c; subscribers=s;
        }
    }
    
    public static class SuggestionItem {
        public String suggestion;
        public List<RisingQueryAdapter.RisingItem> risingQueries;
        public boolean isExpanded = false;
        public boolean passedValidation = false;
        public String validationMessage = "";
        public List<VideoInfo> checkedVideos = new ArrayList<>();
        
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
        TextView tvSuggestion, tvValidation, tvVideoDetails;
        Button btnCopy;
        LinearLayout layoutRising;
        RecyclerView rvRising;
        ImageView ivExpand;
        
        public VH(View v) {
            super(v);
            tvSuggestion = v.findViewById(R.id.tvSuggestion);
            tvValidation = v.findViewById(R.id.tvValidation);
            tvVideoDetails = v.findViewById(R.id.tvVideoDetails);
            btnCopy = v.findViewById(R.id.btnCopy);
            layoutRising = v.findViewById(R.id.layoutRising);
            rvRising = v.findViewById(R.id.rvRising);
            ivExpand = v.findViewById(R.id.ivExpand);
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
        
        // Show validation status
        if (item.passedValidation) {
            holder.tvValidation.setText("✅ Passed validation (" + item.checkedVideos.size() + " videos checked)");
            holder.tvValidation.setTextColor(0xFF2E7D32);
        } else {
            holder.tvValidation.setText("❌ " + item.validationMessage);
            holder.tvValidation.setTextColor(0xFFC62828);
        }
        
        // Show video details
        if (!item.checkedVideos.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < item.checkedVideos.size(); i++) {
                VideoInfo v = item.checkedVideos.get(i);
                long daysAgo = (System.currentTimeMillis() - v.uploaded) / (1000 * 60 * 60 * 24);
                sb.append((i+1) + ". ").append(v.title).append("\n");
                sb.append("   👁 ").append(formatNumber(v.views)).append(" views");
                sb.append(" • 📅 ").append(daysAgo).append(" days ago");
                sb.append(" • 👤 ").append(v.channelName);
                sb.append(" (").append(formatNumber(v.subscribers)).append(" subs)\n");
            }
            holder.tvVideoDetails.setText(sb.toString());
            holder.tvVideoDetails.setVisibility(View.VISIBLE);
        } else {
            holder.tvVideoDetails.setVisibility(View.GONE);
        }
        
        // Expand/Collapse logic
        holder.itemView.setOnClickListener(v -> {
            if (item.passedValidation && !item.risingQueries.isEmpty()) {
                item.isExpanded = !item.isExpanded;
                holder.layoutRising.setVisibility(item.isExpanded ? View.VISIBLE : View.GONE);
                holder.ivExpand.setRotation(item.isExpanded ? 180 : 0);
            }
        });
        
        // Copy button (only for passed suggestions)
        holder.btnCopy.setVisibility(item.passedValidation ? View.VISIBLE : View.GONE);
        holder.btnCopy.setOnClickListener(v -> {
            if (copyListener != null) {
                copyListener.onCopyClick(item);
            }
        });
        
        // Rising queries RecyclerView
        if (item.passedValidation && item.risingQueries.size() > 0) {
            holder.rvRising.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
            holder.rvRising.setAdapter(new RisingQueryAdapter(item.risingQueries));
            holder.rvRising.setNestedScrollingEnabled(false);
        }
        
        holder.ivExpand.setVisibility(item.passedValidation && !item.risingQueries.isEmpty() ? View.VISIBLE : View.INVISIBLE);
    }
    
    private String formatNumber(long num) {
        if (num >= 1000000) return String.format("%.1fM", num / 1000000.0);
        if (num >= 1000) return String.format("%.1fK", num / 1000.0);
        return String.valueOf(num);
    }

    @Override
    public int getItemCount() { return items.size(); }
}
