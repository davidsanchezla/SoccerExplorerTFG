package com.example.soccerexplorer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class LigaEstadisticasAdapter extends RecyclerView.Adapter<LigaEstadisticasAdapter.StatsViewHolder> {

    private final List<ScorerItem> items = new ArrayList<>();

    void actualizarItems(@NonNull List<ScorerItem> nuevosItems) {
        items.clear();
        items.addAll(nuevosItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StatsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_liga_estadistica, parent, false);
        return new StatsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StatsViewHolder holder, int position) {
        ScorerItem item = items.get(position);

        holder.tvRank.setText(String.valueOf(position + 1));
        holder.tvPlayerName.setText(item.playerName);
        holder.tvTeamName.setText(item.teamName);

        String assistsText = item.assists >= 0
                ? holder.itemView.getContext().getString(R.string.liga_assists_short, item.assists)
                : holder.itemView.getContext().getString(R.string.liga_assists_short_unknown);
        holder.tvNumbers.setText(
                holder.itemView.getContext().getString(
                        R.string.liga_scorer_numbers_format,
                        item.goals,
                        assistsText,
                        item.playedMatches
                )
        );

        Glide.with(holder.itemView.getContext())
                .load(item.teamLogo)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .into(holder.ivTeamLogo);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class StatsViewHolder extends RecyclerView.ViewHolder {
        final TextView tvRank;
        final TextView tvPlayerName;
        final TextView tvTeamName;
        final TextView tvNumbers;
        final ImageView ivTeamLogo;

        StatsViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvLigaScorerRank);
            tvPlayerName = itemView.findViewById(R.id.tvLigaScorerPlayer);
            tvTeamName = itemView.findViewById(R.id.tvLigaScorerTeam);
            tvNumbers = itemView.findViewById(R.id.tvLigaScorerNumbers);
            ivTeamLogo = itemView.findViewById(R.id.ivLigaScorerLogo);
        }
    }

    static class ScorerItem {
        final String playerName;
        final String teamName;
        final String teamLogo;
        final int goals;
        final int assists;
        final int playedMatches;

        ScorerItem(@NonNull String playerName,
                   @NonNull String teamName,
                   String teamLogo,
                   int goals,
                   int assists,
                   int playedMatches) {
            this.playerName = playerName;
            this.teamName = teamName;
            this.teamLogo = teamLogo;
            this.goals = goals;
            this.assists = assists;
            this.playedMatches = playedMatches;
        }
    }
}
