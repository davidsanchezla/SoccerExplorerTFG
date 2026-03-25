package com.example.soccerexplorer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class BuscadorAdapter extends RecyclerView.Adapter<BuscadorAdapter.SearchViewHolder> {

    static class SearchItem {
        final String section;
        final String name;
        final String subtitle;
        final boolean showSectionHeader;
        @Nullable
        final String competitionId;

        SearchItem(@NonNull String section,
                   @NonNull String name,
                   @NonNull String subtitle,
                   boolean showSectionHeader,
                   @Nullable String competitionId) {
            this.section = section;
            this.name = name;
            this.subtitle = subtitle;
            this.showSectionHeader = showSectionHeader;
            this.competitionId = competitionId;
        }
    }

    public interface OnItemClickListener {
        void onItemClick(@NonNull SearchItem item);
    }

    private final List<SearchItem> items = new ArrayList<>();
    @Nullable
    private OnItemClickListener onItemClickListener;

    void updateItems(@NonNull List<SearchItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @NonNull
    @Override
    public SearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new SearchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchViewHolder holder, int position) {
        SearchItem item = items.get(position);
        holder.tvSearchName.setText(item.name);
        holder.tvSearchSubtitle.setText(item.subtitle);

        if (item.showSectionHeader) {
            holder.tvSearchSectionTitle.setVisibility(View.VISIBLE);
            holder.tvSearchSectionTitle.setText(item.section);
        } else {
            holder.tvSearchSectionTitle.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SearchViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSearchSectionTitle;
        final TextView tvSearchName;
        final TextView tvSearchSubtitle;

        SearchViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSearchSectionTitle = itemView.findViewById(R.id.tvSearchSectionTitle);
            tvSearchName = itemView.findViewById(R.id.tvSearchName);
            tvSearchSubtitle = itemView.findViewById(R.id.tvSearchSubtitle);
        }
    }
}
