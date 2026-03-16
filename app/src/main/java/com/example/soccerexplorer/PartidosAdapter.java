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

public class PartidosAdapter extends RecyclerView.Adapter<PartidosAdapter.PartidoViewHolder> {

    private final List<PartidoItem> partidos = new ArrayList<>();

    void actualizarPartidos(@NonNull List<PartidoItem> nuevosPartidos) {
        partidos.clear();
        partidos.addAll(nuevosPartidos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PartidoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_partido, parent, false);
        return new PartidoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PartidoViewHolder holder, int position) {
        PartidoItem item = partidos.get(position);
        holder.tvHomeTeam.setText(item.homeTeam);
        holder.tvAwayTeam.setText(item.awayTeam);
        holder.tvScoreOrTime.setText(item.scoreOrTime);
        holder.tvMatchStatus.setText(item.status);

        if (item.round == null || item.round.trim().isEmpty()) {
            holder.tvMatchRound.setVisibility(View.GONE);
        } else {
            holder.tvMatchRound.setVisibility(View.VISIBLE);
            holder.tvMatchRound.setText(item.round);
        }

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
        return partidos.size();
    }

    static class PartidoViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMatchRound;
        final ImageView ivHomeLogo;
        final TextView tvHomeTeam;
        final TextView tvScoreOrTime;
        final TextView tvAwayTeam;
        final ImageView ivAwayLogo;
        final TextView tvMatchStatus;

        PartidoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMatchRound = itemView.findViewById(R.id.tvMatchRound);
            ivHomeLogo = itemView.findViewById(R.id.ivHomeLogo);
            tvHomeTeam = itemView.findViewById(R.id.tvHomeTeam);
            tvScoreOrTime = itemView.findViewById(R.id.tvScoreOrTime);
            tvAwayTeam = itemView.findViewById(R.id.tvAwayTeam);
            ivAwayLogo = itemView.findViewById(R.id.ivAwayLogo);
            tvMatchStatus = itemView.findViewById(R.id.tvMatchStatus);
        }
    }

    static class PartidoItem {
        final String round;
        final String homeTeam;
        final String awayTeam;
        final String scoreOrTime;
        final String status;
        final String homeLogo;
        final String awayLogo;
        final boolean live;
        final String sortTime;

        PartidoItem(String round,
                    String homeTeam,
                    String awayTeam,
                    String scoreOrTime,
                    String status,
                    String homeLogo,
                    String awayLogo,
                    boolean live,
                    String sortTime) {
            this.round = round;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.scoreOrTime = scoreOrTime;
            this.status = status;
            this.homeLogo = homeLogo;
            this.awayLogo = awayLogo;
            this.live = live;
            this.sortTime = sortTime;
        }
    }
}
