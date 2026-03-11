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

import java.util.ArrayList;
import java.util.List;

public class NoticiasAdapter extends RecyclerView.Adapter<NoticiasAdapter.NoticiaViewHolder> {

    private final List<NoticiaItem> noticias = new ArrayList<>();

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

        Glide.with(holder.itemView.getContext())
                .load(noticia.imagenUrl)
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .into(holder.ivNoticia);
    }

    @Override
    public int getItemCount() {
        return noticias.size();
    }

    static class NoticiaViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivNoticia;
        final TextView tvTitulo;
        final TextView tvFuente;

        NoticiaViewHolder(@NonNull View itemView) {
            super(itemView);
            ivNoticia = itemView.findViewById(R.id.ivNoticia);
            tvTitulo = itemView.findViewById(R.id.tvNoticiaTitulo);
            tvFuente = itemView.findViewById(R.id.tvNoticiaFuente);
        }
    }

    static class NoticiaItem {
        final String titulo;
        final String fuenteYFecha;
        @Nullable
        final String imagenUrl;

        NoticiaItem(@NonNull String titulo, @NonNull String fuenteYFecha, @Nullable String imagenUrl) {
            this.titulo = titulo;
            this.fuenteYFecha = fuenteYFecha;
            this.imagenUrl = imagenUrl;
        }
    }
}
