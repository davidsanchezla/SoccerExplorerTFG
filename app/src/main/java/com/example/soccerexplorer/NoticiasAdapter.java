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

public class NoticiasAdapter extends RecyclerView.Adapter<NoticiasAdapter.NoticiaViewHolder> {

    interface OnNoticiaClickListener {
        void onAbrirNoticia(@NonNull NoticiaItem noticia);
    }

    private final List<NoticiaItem> noticias = new ArrayList<>();
    private final OnNoticiaClickListener onNoticiaClickListener;

    NoticiasAdapter(@NonNull OnNoticiaClickListener onNoticiaClickListener) {
        this.onNoticiaClickListener = onNoticiaClickListener;
    }

    void actualizarNoticias(@NonNull List<NoticiaItem> nuevasNoticias) {
        noticias.clear();
        noticias.addAll(nuevasNoticias);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NoticiaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_noticia, parent, false);
        return new NoticiaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoticiaViewHolder holder, int position) {
        NoticiaItem noticia = noticias.get(position);
        holder.tvTitulo.setText(noticia.titulo);
        holder.tvFuente.setText(noticia.fuenteYFecha);
        boolean tieneUrl = noticia.url != null && !noticia.url.trim().isEmpty();

        Glide.with(holder.itemView.getContext())
                .load(noticia.imagenUrl)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .into(holder.ivNoticia);

        holder.btnVerMas.setEnabled(tieneUrl);
        holder.btnVerMas.setAlpha(tieneUrl ? 1f : 0.55f);
        holder.btnVerMas.setOnClickListener(v -> onNoticiaClickListener.onAbrirNoticia(noticia));
        holder.itemView.setOnClickListener(v -> onNoticiaClickListener.onAbrirNoticia(noticia));
    }

    @Override
    public int getItemCount() {
        return noticias.size();
    }

    static class NoticiaViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivNoticia;
        final TextView tvTitulo;
        final TextView tvFuente;
        final MaterialButton btnVerMas;

        NoticiaViewHolder(@NonNull View itemView) {
            super(itemView);
            ivNoticia = itemView.findViewById(R.id.ivNoticia);
            tvTitulo = itemView.findViewById(R.id.tvNoticiaTitulo);
            tvFuente = itemView.findViewById(R.id.tvNoticiaFuente);
            btnVerMas = itemView.findViewById(R.id.btnVerMas);
        }
    }

    static class NoticiaItem {
        final String titulo;
        final String fuenteYFecha;
        @Nullable
        final String imagenUrl;
        @Nullable
        final String url;

        NoticiaItem(
                @NonNull String titulo,
                @NonNull String fuenteYFecha,
                @Nullable String imagenUrl,
                @Nullable String url
        ) {
            this.titulo = titulo;
            this.fuenteYFecha = fuenteYFecha;
            this.imagenUrl = imagenUrl;
            this.url = url;
        }
    }
}
