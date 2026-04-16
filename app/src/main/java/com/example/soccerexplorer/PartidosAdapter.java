package com.example.soccerexplorer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class PartidosAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_MATCH = 1;
    private static final int TYPE_FOOTER = 2;

    private final List<RowItem> items = new ArrayList<>();

    @Nullable
    private OnLeagueFooterClickListener onLeagueFooterClickListener;

    interface OnLeagueFooterClickListener {
        void onLeagueFooterClick(@NonNull String competitionName, @NonNull String competitionCode);
    }

    void setOnLeagueFooterClickListener(@Nullable OnLeagueFooterClickListener listener) {
        this.onLeagueFooterClickListener = listener;
    }

    void actualizarItems(@NonNull List<RowItem> nuevosItems) {
        items.clear();
        items.addAll(nuevosItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View headerView = inflater.inflate(R.layout.item_competicion_header, parent, false);
            return new HeaderViewHolder(headerView);
        }
        if (viewType == TYPE_FOOTER) {
            View footerView = inflater.inflate(R.layout.item_competicion_footer, parent, false);
            return new FooterViewHolder(footerView);
        }
        View matchView = inflater.inflate(R.layout.item_partido, parent, false);
        return new PartidoViewHolder(matchView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RowItem rowItem = items.get(position);

        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvCompetitionHeader.setText(rowItem.competitionName);
            return;
        }

        if (holder instanceof FooterViewHolder) {
            FooterViewHolder footerHolder = (FooterViewHolder) holder;
            String competitionName = rowItem.competitionName == null ? "" : rowItem.competitionName;
            String competitionCode = rowItem.competitionCode == null ? "" : rowItem.competitionCode;

            footerHolder.btnViewStandings.setText(
                    footerHolder.itemView.getContext().getString(
                            R.string.matches_view_standings_button,
                            competitionName
                    )
            );
            footerHolder.btnViewStandings.setOnClickListener(v -> {
                if (onLeagueFooterClickListener == null
                        || competitionName.trim().isEmpty()
                        || competitionCode.trim().isEmpty()) {
                    return;
                }
                onLeagueFooterClickListener.onLeagueFooterClick(competitionName, competitionCode);
            });
            return;
        }

        PartidoItem item = rowItem.partido;
        if (item == null) {
            return;
        }

        PartidoViewHolder partidoHolder = (PartidoViewHolder) holder;

        partidoHolder.tvHomeTeam.setText(item.homeTeam);
        partidoHolder.tvAwayTeam.setText(item.awayTeam);
        partidoHolder.tvScoreOrTime.setText(item.scoreOrTime);
        partidoHolder.tvMatchStatus.setText(item.status);

        if (item.round == null || item.round.trim().isEmpty()) {
            partidoHolder.tvMatchRound.setVisibility(View.GONE);
        } else {
            partidoHolder.tvMatchRound.setVisibility(View.VISIBLE);
            partidoHolder.tvMatchRound.setText(item.round);
        }

        Glide.with(partidoHolder.itemView.getContext())
                .load(item.homeLogo)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .into(partidoHolder.ivHomeLogo);

        Glide.with(partidoHolder.itemView.getContext())
                .load(item.awayLogo)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .into(partidoHolder.ivAwayLogo);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvCompetitionHeader;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCompetitionHeader = itemView.findViewById(R.id.tvCompetitionHeader);
        }
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

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        final MaterialButton btnViewStandings;

        FooterViewHolder(@NonNull View itemView) {
            super(itemView);
            btnViewStandings = itemView.findViewById(R.id.btnCompetitionFooter);
        }
    }

    static class RowItem {
        final int type;
        @Nullable
        final String competitionName;
        @Nullable
        final String competitionCode;
        @Nullable
        final PartidoItem partido;

        private RowItem(int type,
                        @Nullable String competitionName,
                        @Nullable String competitionCode,
                        @Nullable PartidoItem partido) {
            this.type = type;
            this.competitionName = competitionName;
            this.competitionCode = competitionCode;
            this.partido = partido;
        }

        @NonNull
        static RowItem header(@NonNull String title) {
            return new RowItem(TYPE_HEADER, title, null, null);
        }

        @NonNull
        static RowItem match(@NonNull PartidoItem partido) {
            return new RowItem(TYPE_MATCH, null, null, partido);
        }

        @NonNull
        static RowItem footer(@NonNull String competitionName, @NonNull String competitionCode) {
            return new RowItem(TYPE_FOOTER, competitionName, competitionCode, null);
        }
    }

    static class PartidoItem {
        final String round;
        final String competitionName;
        final String homeTeam;
        final String awayTeam;
        final String scoreOrTime;
        final String status;
        final String homeLogo;
        final String awayLogo;
        final boolean live;
        final String sortTime;

        PartidoItem(String round,
                    String competitionName,
                    String homeTeam,
                    String awayTeam,
                    String scoreOrTime,
                    String status,
                    String homeLogo,
                    String awayLogo,
                    boolean live,
                    String sortTime) {
            this.round = round;
            this.competitionName = competitionName;
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
