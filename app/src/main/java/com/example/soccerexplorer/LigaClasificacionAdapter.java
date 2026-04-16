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

public class LigaClasificacionAdapter extends RecyclerView.Adapter<LigaClasificacionAdapter.ClasificacionViewHolder> {

    private final List<StandingItem> items = new ArrayList<>();

    void actualizarItems(@NonNull List<StandingItem> nuevosItems) {
        items.clear();
        items.addAll(nuevosItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ClasificacionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_liga_clasificacion, parent, false);
        return new ClasificacionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClasificacionViewHolder holder, int position) {
        StandingItem item = items.get(position);

        holder.tvPosition.setText(String.valueOf(item.position));
        holder.tvTeam.setText(item.teamName);
        holder.tvPoints.setText(holder.itemView.getContext().getString(R.string.liga_points_short, item.points));
        holder.tvStats.setText(
                holder.itemView.getContext().getString(
                        R.string.liga_table_stats_format,
                        item.played,
                        item.won,
                        item.draw,
                        item.lost,
                        item.goalDifference
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

    static class ClasificacionViewHolder extends RecyclerView.ViewHolder {
        final TextView tvPosition;
        final ImageView ivTeamLogo;
        final TextView tvTeam;
        final TextView tvPoints;
        final TextView tvStats;

        ClasificacionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPosition = itemView.findViewById(R.id.tvLigaTablePosition);
            ivTeamLogo = itemView.findViewById(R.id.ivLigaTableLogo);
            tvTeam = itemView.findViewById(R.id.tvLigaTableTeam);
            tvPoints = itemView.findViewById(R.id.tvLigaTablePoints);
            tvStats = itemView.findViewById(R.id.tvLigaTableStats);
        }
    }

    static class StandingItem {
        final int position;
        final String teamName;
        final String teamLogo;
        final int points;
        final int played;
        final int won;
        final int draw;
        final int lost;
        final int goalDifference;

        StandingItem(int position,
                     @NonNull String teamName,
                     String teamLogo,
                     int points,
                     int played,
                     int won,
                     int draw,
                     int lost,
                     int goalDifference) {
            this.position = position;
            this.teamName = teamName;
            this.teamLogo = teamLogo;
            this.points = points;
            this.played = played;
            this.won = won;
            this.draw = draw;
            this.lost = lost;
            this.goalDifference = goalDifference;
        }
    }
}
