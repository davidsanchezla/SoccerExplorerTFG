package com.example.soccerexplorer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class QuinielaHistorialAdapter extends RecyclerView.Adapter<QuinielaHistorialAdapter.ViewHolder> {

    private final List<QuinielaItem> items = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private OnSelectionChangedListener selectionListener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(Set<String> selectedIds);
    }

    public static class QuinielaItem {
        public final String id;
        public final String tituloJornada;
        public final String semanaId;
        public final String ligaId;
        public final int aciertos;
        public final int totalPartidos;
        public final int xpGanada;
        public final boolean bonusPleno;
        public final List<PartidoItem> partidos;

        public QuinielaItem(String id, String tituloJornada, String semanaId, String ligaId,
                           int aciertos, int totalPartidos, int xpGanada, boolean bonusPleno,
                           List<PartidoItem> partidos) {
            this.id = id;
            this.tituloJornada = tituloJornada;
            this.semanaId = semanaId;
            this.ligaId = ligaId;
            this.aciertos = aciertos;
            this.totalPartidos = totalPartidos;
            this.xpGanada = xpGanada;
            this.bonusPleno = bonusPleno;
            this.partidos = partidos;
        }
    }

    public static class PartidoItem {
        public final String matchId;
        public final String homeTeam;
        public final String awayTeam;
        public final String homeLogo;
        public final String awayLogo;
        public final String pronostico;
        public final int homeScore;
        public final int awayScore;
        public final String resultadoMarcador;
        public final boolean esAcierto;

        public PartidoItem(String matchId, String homeTeam, String awayTeam,
                          String homeLogo, String awayLogo,
                          String pronostico, int homeScore, int awayScore,
                          String resultadoMarcador, boolean esAcierto) {
            this.matchId = matchId;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.homeLogo = homeLogo;
            this.awayLogo = awayLogo;
            this.pronostico = pronostico;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.resultadoMarcador = resultadoMarcador;
            this.esAcierto = esAcierto;
        }
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    public void submitList(List<QuinielaItem> newItems) {
        items.clear();
        items.addAll(newItems);
        selectedIds.clear();
        for (QuinielaItem item : newItems) {
            selectedIds.add(item.id);
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(new HashSet<>(selectedIds));
        }
    }

    public List<QuinielaItem> getSelectedItems() {
        List<QuinielaItem> selected = new ArrayList<>();
        for (QuinielaItem item : items) {
            if (selectedIds.contains(item.id)) {
                selected.add(item);
            }
        }
        return selected;
    }

    public List<QuinielaItem> getAllItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quiniela_historial, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuinielaItem item = items.get(position);
        holder.bind(item, selectedIds.contains(item.id));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final TextView tvJornada;
        private final TextView tvSemana;
        private final TextView tvLiga;
        private final TextView tvAciertos;
        private final TextView tvXp;
        private final TextView tvBonus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            tvJornada = itemView.findViewById(R.id.tvHistorialJornada);
            tvSemana = itemView.findViewById(R.id.tvHistorialSemana);
            tvLiga = itemView.findViewById(R.id.tvHistorialLiga);
            tvAciertos = itemView.findViewById(R.id.tvHistorialAciertos);
            tvXp = itemView.findViewById(R.id.tvHistorialXp);
            tvBonus = itemView.findViewById(R.id.tvHistorialBonus);

            cardView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    QuinielaItem item = items.get(position);
                    if (selectedIds.contains(item.id)) {
                        selectedIds.remove(item.id);
                    } else {
                        selectedIds.add(item.id);
                    }
                    cardView.setChecked(selectedIds.contains(item.id));
                    if (selectionListener != null) {
                        selectionListener.onSelectionChanged(new HashSet<>(selectedIds));
                    }
                }
            });
        }

        void bind(QuinielaItem item, boolean isSelected) {
            tvJornada.setText(item.tituloJornada);
            tvSemana.setText(item.semanaId);
            tvLiga.setText(item.ligaId);
            tvAciertos.setText(String.format(Locale.getDefault(),
                    "%d / %d aciertos", item.aciertos, item.totalPartidos));
            tvXp.setText(String.format(Locale.getDefault(), "+%d XP", item.xpGanada));
            tvBonus.setVisibility(item.bonusPleno ? View.VISIBLE : View.GONE);
            cardView.setChecked(isSelected);
        }
    }
}
