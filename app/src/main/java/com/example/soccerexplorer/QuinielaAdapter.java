package com.example.soccerexplorer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuinielaAdapter extends RecyclerView.Adapter<QuinielaAdapter.MatchViewHolder> {

    interface OnPickSelectedListener {
        void onPickSelected(@NonNull String matchId, @Nullable String pick);
    }

    private final List<MatchRow> items = new ArrayList<>();
    private final Map<String, String> pronosticos = new HashMap<>();
    private final OnPickSelectedListener onPickSelectedListener;

    private boolean editable = false;
    private boolean mostrarResultados = false;

    QuinielaAdapter(@NonNull OnPickSelectedListener onPickSelectedListener) {
        this.onPickSelectedListener = onPickSelectedListener;
    }

    void actualizarItems(@NonNull List<MatchRow> nuevosItems,
                        @NonNull Map<String, String> pronosticosActuales,
                        boolean editable,
                        boolean mostrarResultados) {
        this.items.clear();
        this.items.addAll(nuevosItems);
        this.pronosticos.clear();
        this.pronosticos.putAll(pronosticosActuales);
        this.editable = editable;
        this.mostrarResultados = mostrarResultados;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quiniela_partido, parent, false);
        return new MatchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MatchViewHolder holder, int position) {
        MatchRow item = items.get(position);

        holder.tvMeta.setText(item.meta);
        holder.ivHomeLogo.setContentDescription(item.homeTeam);
        holder.ivAwayLogo.setContentDescription(item.awayTeam);

        Glide.with(holder.itemView.getContext())
                .load(item.homeLogoUrl)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .into(holder.ivHomeLogo);

        Glide.with(holder.itemView.getContext())
                .load(item.awayLogoUrl)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .into(holder.ivAwayLogo);

        String pick = pronosticos.get(item.matchId);

        holder.rgPronostico.setOnCheckedChangeListener(null);
        holder.rgPronostico.clearCheck();
        if ("1".equals(pick)) {
            holder.rbPickHome.setChecked(true);
        } else if ("X".equals(pick)) {
            holder.rbPickDraw.setChecked(true);
        } else if ("2".equals(pick)) {
            holder.rbPickAway.setChecked(true);
        }

        holder.rbPickHome.setEnabled(editable);
        holder.rbPickDraw.setEnabled(editable);
        holder.rbPickAway.setEnabled(editable);

        if (editable) {
            holder.rgPronostico.setOnCheckedChangeListener((group, checkedId) -> {
                String nuevoPick = mapCheckedIdToPick(checkedId);
                if (nuevoPick == null) {
                    pronosticos.remove(item.matchId);
                } else {
                    pronosticos.put(item.matchId, nuevoPick);
                }
                onPickSelectedListener.onPickSelected(item.matchId, nuevoPick);
            });
        }

        if (mostrarResultados && item.resultadoFinal != null && item.resultadoMarcador != null) {
            boolean acierto = item.resultadoFinal.equals(pick);
            String resultadoEstado = holder.itemView.getContext().getString(
                    acierto ? R.string.quiniela_result_hit : R.string.quiniela_result_miss
            );
            String text = holder.itemView.getContext().getString(
                    R.string.quiniela_result_line,
                    item.resultadoMarcador,
                    resultadoEstado
            );
            holder.tvResultado.setVisibility(View.VISIBLE);
            holder.tvResultado.setText(text);
        } else {
            holder.tvResultado.setVisibility(View.GONE);
            holder.tvResultado.setText("");
        }
    }

    @Nullable
    private String mapCheckedIdToPick(int checkedId) {
        if (checkedId == R.id.rbPickHome) {
            return "1";
        }
        if (checkedId == R.id.rbPickDraw) {
            return "X";
        }
        if (checkedId == R.id.rbPickAway) {
            return "2";
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class MatchViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivHomeLogo;
        final ImageView ivAwayLogo;
        final TextView tvMeta;
        final RadioGroup rgPronostico;
        final RadioButton rbPickHome;
        final RadioButton rbPickDraw;
        final RadioButton rbPickAway;
        final TextView tvResultado;

        MatchViewHolder(@NonNull View itemView) {
            super(itemView);
            ivHomeLogo = itemView.findViewById(R.id.ivQuinielaHomeLogo);
            ivAwayLogo = itemView.findViewById(R.id.ivQuinielaAwayLogo);
            tvMeta = itemView.findViewById(R.id.tvQuinielaMeta);
            rgPronostico = itemView.findViewById(R.id.rgPronostico);
            rbPickHome = itemView.findViewById(R.id.rbPickHome);
            rbPickDraw = itemView.findViewById(R.id.rbPickDraw);
            rbPickAway = itemView.findViewById(R.id.rbPickAway);
            tvResultado = itemView.findViewById(R.id.tvQuinielaResult);
        }
    }

    static class MatchRow {
        final String matchId;
        final String homeTeam;
        final String awayTeam;
        final String meta;
        @Nullable
        final String homeLogoUrl;
        @Nullable
        final String awayLogoUrl;
        @Nullable
        final String resultadoFinal;
        @Nullable
        final String resultadoMarcador;

        MatchRow(@NonNull String matchId,
                 @NonNull String homeTeam,
                 @NonNull String awayTeam,
                 @NonNull String meta,
                 @Nullable String homeLogoUrl,
                 @Nullable String awayLogoUrl,
                 @Nullable String resultadoFinal,
                 @Nullable String resultadoMarcador) {
            this.matchId = matchId;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.meta = meta;
            this.homeLogoUrl = homeLogoUrl;
            this.awayLogoUrl = awayLogoUrl;
            this.resultadoFinal = resultadoFinal;
            this.resultadoMarcador = resultadoMarcador;
        }
    }
}
