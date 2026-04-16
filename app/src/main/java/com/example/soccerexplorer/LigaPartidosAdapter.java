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

public class LigaPartidosAdapter extends RecyclerView.Adapter<LigaPartidosAdapter.PartidoViewHolder> {

    private final List<MatchItem> items = new ArrayList<>();

    void actualizarItems(@NonNull List<MatchItem> nuevosItems) {
        items.clear();
        items.addAll(nuevosItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PartidoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_liga_partido, parent, false);
        return new PartidoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PartidoViewHolder holder, int position) {
        MatchItem item = items.get(position);

        holder.tvMeta.setText(item.meta);
        holder.tvMeta.setVisibility(item.meta == null || item.meta.trim().isEmpty() ? View.GONE : View.VISIBLE);

        holder.tvHomeTeam.setText(item.homeTeam);
        holder.tvAwayTeam.setText(item.awayTeam);
        holder.tvScoreOrTime.setText(item.scoreOrTime);
        holder.tvStatus.setText(item.status);

        Glide.with(holder.itemView.getContext())
                .load(item.homeLogo)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .into(holder.ivHomeLogo);

        Glide.with(holder.itemView.getContext())
                .load(item.awayLogo)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .into(holder.ivAwayLogo);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class PartidoViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMeta;
        final ImageView ivHomeLogo;
        final TextView tvHomeTeam;
        final TextView tvScoreOrTime;
        final TextView tvAwayTeam;
        final ImageView ivAwayLogo;
        final TextView tvStatus;

        PartidoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMeta = itemView.findViewById(R.id.tvLigaPartidoMeta);
            ivHomeLogo = itemView.findViewById(R.id.ivLigaPartidoHomeLogo);
            tvHomeTeam = itemView.findViewById(R.id.tvLigaPartidoHome);
            tvScoreOrTime = itemView.findViewById(R.id.tvLigaPartidoScore);
            tvAwayTeam = itemView.findViewById(R.id.tvLigaPartidoAway);
            ivAwayLogo = itemView.findViewById(R.id.ivLigaPartidoAwayLogo);
            tvStatus = itemView.findViewById(R.id.tvLigaPartidoStatus);
        }
    }

    static class MatchItem {
        final String meta;
        final String homeTeam;
        final String awayTeam;
        final String scoreOrTime;
        final String status;
        final String homeLogo;
        final String awayLogo;
        final boolean live;
        final long kickoffEpochMs;

        MatchItem(@NonNull String meta,
                  @NonNull String homeTeam,
                  @NonNull String awayTeam,
                  @NonNull String scoreOrTime,
                  @NonNull String status,
                  String homeLogo,
                  String awayLogo,
                  boolean live,
                  long kickoffEpochMs) {
            this.meta = meta;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.scoreOrTime = scoreOrTime;
            this.status = status;
            this.homeLogo = homeLogo;
            this.awayLogo = awayLogo;
            this.live = live;
            this.kickoffEpochMs = kickoffEpochMs;
        }
    }
}
