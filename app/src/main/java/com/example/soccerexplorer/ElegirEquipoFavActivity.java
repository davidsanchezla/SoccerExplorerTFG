package com.example.soccerexplorer;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ElegirEquipoFavActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;

    private TextInputLayout tilBuscarEquipo;
    private TextInputEditText etBuscarEquipo;
    private ChipGroup grupoChipsLigas;
    private RecyclerView rvEquipos;
    private ProgressBar pbEquipos;
    private TextView tvEquiposEmpty;
    private TextView tvResumenSeleccion;
    private MaterialButton btnGuardarEquipo;

    private final List<LigaItem> ligas = new ArrayList<>();
    private final List<EquipoItem> equiposLigaActual = new ArrayList<>();
    private EquipoAdapter equipoAdapter;

    private boolean bloqueandoEventoChipLiga;

    @Nullable
    private LigaItem ligaSeleccionada;
    @Nullable
    private EquipoItem equipoSeleccionado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_elegir_equipo_fav);

        // region Inicializacion
        firestore = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
        // endregion

        // region UI
        vincularVistas();
        configurarRecyclerEquipos();
        configurarListeners();
        // endregion

        // region Datos
        cargarLigas();
        // endregion
    }

    // region Configuracion UI
    private void vincularVistas() {
        tilBuscarEquipo = findViewById(R.id.tilBuscarEquipo);
        etBuscarEquipo = findViewById(R.id.etBuscarEquipo);
        grupoChipsLigas = findViewById(R.id.grupoChipsLigas);
        rvEquipos = findViewById(R.id.rvEquipos);
        pbEquipos = findViewById(R.id.pbEquipos);
        tvEquiposEmpty = findViewById(R.id.tvEquiposEmpty);
        tvResumenSeleccion = findViewById(R.id.tvResumenSeleccion);
        btnGuardarEquipo = findViewById(R.id.btnGuardarEquipo);
    }

    private void configurarRecyclerEquipos() {
        rvEquipos.setLayoutManager(new GridLayoutManager(this, 2));
        equipoAdapter = new EquipoAdapter(equipo -> {
            equipoSeleccionado = equipo;
            equipoAdapter.actualizarEquipoSeleccionadoId(equipo.id);
            actualizarEstadoBotonGuardar();
        });
        rvEquipos.setAdapter(equipoAdapter);
    }

    private void configurarListeners() {
        grupoChipsLigas.setOnCheckedStateChangeListener((group, idsMarcados) -> {
            if (bloqueandoEventoChipLiga || idsMarcados.isEmpty()) {
                return;
            }

            Chip chipLiga = group.findViewById(idsMarcados.get(0));
            if (chipLiga == null) {
                return;
            }

            Object tag = chipLiga.getTag();
            if (tag instanceof LigaItem) {
                alSeleccionarLiga((LigaItem) tag);
            }
        });

        etBuscarEquipo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                aplicarFiltroEquipos();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnGuardarEquipo.setOnClickListener(v -> guardarEquipoFavorito());
    }
    // endregion

    // region Carga de datos
    private void cargarLigas() {
        firestore.collection("competiciones")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    ligas.clear();

                    queryDocumentSnapshots.getDocuments().forEach(documentSnapshot -> {
                        String ligaId = documentSnapshot.getId();
                        String ligaNombre = documentSnapshot.getString("nombre");
                        if (ligaNombre == null || ligaNombre.trim().isEmpty()) {
                            ligaNombre = ligaId;
                        }
                        ligas.add(new LigaItem(ligaId, ligaNombre));
                    });

                    ligas.sort((a, b) -> a.nombre.compareToIgnoreCase(b.nombre));

                    if (ligas.isEmpty()) {
                        tilBuscarEquipo.setError(getString(R.string.fav_team_empty_leagues));
                        tvEquiposEmpty.setText(R.string.fav_team_empty_leagues);
                        tvEquiposEmpty.setVisibility(View.VISIBLE);
                        rvEquipos.setVisibility(View.GONE);
                        return;
                    }

                    tilBuscarEquipo.setError(null);
                    crearChipsLigas();

                    LigaItem primeraLiga = ligas.get(0);
                    marcarChipLigaSeleccionada(primeraLiga.id);
                    alSeleccionarLiga(primeraLiga);
                })
                .addOnFailureListener(e -> {
                    tilBuscarEquipo.setError(getString(R.string.fav_team_error_leagues));
                    tvEquiposEmpty.setText(R.string.fav_team_error_leagues);
                    tvEquiposEmpty.setVisibility(View.VISIBLE);
                    rvEquipos.setVisibility(View.GONE);
                });
    }

    private void alSeleccionarLiga(@NonNull LigaItem liga) {
        ligaSeleccionada = liga;
        equipoSeleccionado = null;
        equiposLigaActual.clear();
        equipoAdapter.actualizarEquipos(new ArrayList<>());
        equipoAdapter.actualizarEquipoSeleccionadoId(null);
        actualizarEstadoBotonGuardar();
        etBuscarEquipo.setText(null);
        cargarEquiposLiga(liga.id);
    }

    private void cargarEquiposLiga(@NonNull String ligaId) {
        mostrarCargaEquipos(true);

        firestore.collection("competiciones")
                .document(ligaId)
                .collection("equipos")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<EquipoItem> equipos = new ArrayList<>();

                    queryDocumentSnapshots.getDocuments().forEach(documentSnapshot -> {
                        String equipoId = documentSnapshot.getId();
                        String equipoNombre = documentSnapshot.getString("equipo");
                        if (equipoNombre == null || equipoNombre.trim().isEmpty()) {
                            equipoNombre = documentSnapshot.getString("nombre");
                        }
                        if (equipoNombre == null || equipoNombre.trim().isEmpty()) {
                            equipoNombre = equipoId;
                        }
                        String escudoUrl = documentSnapshot.getString("escudo");
                        String nombreLiga = ligaSeleccionada != null ? ligaSeleccionada.nombre : "";
                        equipos.add(new EquipoItem(equipoId, equipoNombre, nombreLiga, escudoUrl));
                    });

                    equipos.sort((a, b) -> a.nombre.compareToIgnoreCase(b.nombre));
                    equiposLigaActual.clear();
                    equiposLigaActual.addAll(equipos);
                    mostrarCargaEquipos(false);
                    aplicarFiltroEquipos();
                })
                .addOnFailureListener(e -> {
                    equiposLigaActual.clear();
                    equipoAdapter.actualizarEquipos(new ArrayList<>());
                    mostrarCargaEquipos(false);
                    tvEquiposEmpty.setText(R.string.fav_team_error_teams);
                    tvEquiposEmpty.setVisibility(View.VISIBLE);
                    rvEquipos.setVisibility(View.GONE);
                });
    }

    private void crearChipsLigas() {
        grupoChipsLigas.removeAllViews();

        for (LigaItem liga : ligas) {
            Chip chipLiga = new Chip(this);
            chipLiga.setId(View.generateViewId());
            chipLiga.setText(liga.nombre);
            chipLiga.setTag(liga);
            chipLiga.setCheckable(true);
            chipLiga.setCheckedIconVisible(false);
            chipLiga.setChipBackgroundColorResource(R.color.chip_liga_fondo);
            chipLiga.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_liga_texto));
            chipLiga.setChipStrokeColorResource(R.color.chip_liga_borde);
            chipLiga.setChipStrokeWidth(1f);
            grupoChipsLigas.addView(chipLiga);
        }
    }

    private void marcarChipLigaSeleccionada(@NonNull String ligaId) {
        for (int i = 0; i < grupoChipsLigas.getChildCount(); i++) {
            View child = grupoChipsLigas.getChildAt(i);
            if (!(child instanceof Chip)) {
                continue;
            }

            Chip chip = (Chip) child;
            Object tag = chip.getTag();
            if (!(tag instanceof LigaItem)) {
                continue;
            }

            LigaItem liga = (LigaItem) tag;
            if (liga.id.equals(ligaId)) {
                bloqueandoEventoChipLiga = true;
                grupoChipsLigas.check(chip.getId());
                bloqueandoEventoChipLiga = false;
                break;
            }
        }
    }

    private void aplicarFiltroEquipos() {
        String textoBusqueda = obtenerTextoBusqueda();
        String textoBusquedaNormalizado = textoBusqueda.toLowerCase(Locale.ROOT);

        List<EquipoItem> equiposFiltrados = new ArrayList<>();
        for (EquipoItem equipo : equiposLigaActual) {
            if (textoBusquedaNormalizado.isEmpty()
                    || equipo.nombre.toLowerCase(Locale.ROOT).contains(textoBusquedaNormalizado)
                    || equipo.liga.toLowerCase(Locale.ROOT).contains(textoBusquedaNormalizado)) {
                equiposFiltrados.add(equipo);
            }
        }

        equipoAdapter.actualizarEquipos(equiposFiltrados);

        if (equiposLigaActual.isEmpty()) {
            tvEquiposEmpty.setText(R.string.fav_team_empty_teams);
            tvEquiposEmpty.setVisibility(View.VISIBLE);
            rvEquipos.setVisibility(View.GONE);
            return;
        }

        if (equiposFiltrados.isEmpty()) {
            tvEquiposEmpty.setText(R.string.fav_team_search_empty);
            tvEquiposEmpty.setVisibility(View.VISIBLE);
            rvEquipos.setVisibility(View.GONE);
            return;
        }

        tvEquiposEmpty.setVisibility(View.GONE);
        rvEquipos.setVisibility(View.VISIBLE);
    }

    @NonNull
    private String obtenerTextoBusqueda() {
        Editable editable = etBuscarEquipo.getText();
        if (editable == null) {
            return "";
        }
        return editable.toString().trim();
    }
    // endregion

    // region Guardado favorito
    private void guardarEquipoFavorito() {
        if (ligaSeleccionada == null) {
            Toast.makeText(this, R.string.fav_team_error_select_league, Toast.LENGTH_SHORT).show();
            return;
        }

        if (equipoSeleccionado == null) {
            Toast.makeText(this, R.string.fav_team_error_select_team, Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            navegarALogin();
            return;
        }

        btnGuardarEquipo.setEnabled(false);

        Map<String, Object> update = new HashMap<>();
        update.put("equipoFavoritoId", equipoSeleccionado.id);
        update.put("equipoFavoritoNombre", equipoSeleccionado.nombre);
        update.put("equipoFavoritoEscudo", equipoSeleccionado.escudoUrl);
        update.put("equipoFavoritoLigaId", ligaSeleccionada.id);
        update.put("onboardingCompleted", true);
        update.put("updatedAt", FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(currentUser.getUid())
                .set(update, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.fav_team_saved, Toast.LENGTH_SHORT).show();
                    navegarAPrincipal();
                })
                .addOnFailureListener(e -> {
                    btnGuardarEquipo.setEnabled(true);
                    Toast.makeText(this, R.string.fav_team_error_save, Toast.LENGTH_LONG).show();
                });
    }
    // endregion

    // region Estado UI
    private void mostrarCargaEquipos(boolean loading) {
        pbEquipos.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            rvEquipos.setVisibility(View.GONE);
            tvEquiposEmpty.setVisibility(View.GONE);
        }
    }

    private void actualizarEstadoBotonGuardar() {
        btnGuardarEquipo.setEnabled(ligaSeleccionada != null && equipoSeleccionado != null);

        if (equipoSeleccionado == null) {
            tvResumenSeleccion.setText(R.string.fav_team_selection_none);
            tvResumenSeleccion.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            return;
        }

        tvResumenSeleccion.setText(getString(R.string.fav_team_selection_one, equipoSeleccionado.nombre));
        tvResumenSeleccion.setTextColor(ContextCompat.getColor(this, R.color.primary));
    }
    // endregion

    // region Navegacion
    private void navegarAPrincipal() {
        Intent intent = new Intent(ElegirEquipoFavActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void navegarALogin() {
        Intent intent = new Intent(ElegirEquipoFavActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
    // endregion

    // region Modelos
    private static class LigaItem {
        final String id;
        final String nombre;

        LigaItem(@NonNull String id, @NonNull String nombre) {
            this.id = id;
            this.nombre = nombre;
        }

        @NonNull
        @Override
        public String toString() {
            return nombre;
        }
    }

    private static class EquipoItem {
        final String id;
        final String nombre;
        final String liga;
        @Nullable
        final String escudoUrl;

        EquipoItem(@NonNull String id, @NonNull String nombre, @NonNull String liga, @Nullable String escudoUrl) {
            this.id = id;
            this.nombre = nombre;
            this.liga = liga;
            this.escudoUrl = escudoUrl;
        }
    }
    // endregion

    // region Adaptador
    private static class EquipoAdapter extends RecyclerView.Adapter<EquipoAdapter.EquipoViewHolder> {

        interface OnEquipoClickListener {
            void onEquipoClick(@NonNull EquipoItem equipo);
        }

        private final List<EquipoItem> equipos = new ArrayList<>();
        private final OnEquipoClickListener onEquipoClickListener;

        @Nullable
        private String idEquipoSeleccionado;

        EquipoAdapter(@NonNull OnEquipoClickListener onEquipoClickListener) {
            this.onEquipoClickListener = onEquipoClickListener;
        }

        void actualizarEquipos(@NonNull List<EquipoItem> nuevosEquipos) {
            equipos.clear();
            equipos.addAll(nuevosEquipos);
            notifyDataSetChanged();
        }

        void actualizarEquipoSeleccionadoId(@Nullable String idEquipoSeleccionado) {
            this.idEquipoSeleccionado = idEquipoSeleccionado;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public EquipoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_equipo_fav, parent, false);
            return new EquipoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull EquipoViewHolder holder, int position) {
            EquipoItem equipo = equipos.get(position);

            holder.tvNombreEquipo.setText(equipo.nombre);
            holder.tvLigaEquipo.setText(equipo.liga);

            Object modeloEscudo = construirModeloSolicitudEscudo(equipo.escudoUrl);
            holder.pbEscudoCarga.setVisibility(View.VISIBLE);
            holder.ivEscudo.setImageDrawable(null);
            holder.ivEscudo.setVisibility(View.INVISIBLE);
            Glide.with(holder.itemView.getContext())
                    .load(modeloEscudo)
                    .error(android.R.drawable.ic_menu_report_image)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(
                                @Nullable GlideException e,
                                Object model,
                                Target<Drawable> target,
                                boolean isFirstResource
                        ) {
                            holder.pbEscudoCarga.setVisibility(View.GONE);
                            holder.ivEscudo.setVisibility(View.VISIBLE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(
                                Drawable resource,
                                Object model,
                                Target<Drawable> target,
                                DataSource dataSource,
                                boolean isFirstResource
                        ) {
                            holder.pbEscudoCarga.setVisibility(View.GONE);
                            holder.ivEscudo.setVisibility(View.VISIBLE);
                            return false;
                        }
                    })
                    .into(holder.ivEscudo);

            boolean seleccionado = equipo.id.equals(idEquipoSeleccionado);
            holder.ivSelected.setVisibility(seleccionado ? View.VISIBLE : View.GONE);
            holder.itemContainer.setBackgroundResource(
                    seleccionado ? R.drawable.bg_tarjeta_equipo_seleccionada : R.drawable.bg_tarjeta_equipo
            );

            holder.itemView.setOnClickListener(v -> onEquipoClickListener.onEquipoClick(equipo));
        }

        @Nullable
        private Object construirModeloSolicitudEscudo(@Nullable String escudoUrl) {
            if (escudoUrl == null) {
                return null;
            }

            String normalizedUrl = escudoUrl.trim();
            if (normalizedUrl.isEmpty()) {
                return null;
            }

            String schemePrefix = "https://";
            int atIndex = normalizedUrl.indexOf('@');

            if (normalizedUrl.startsWith(schemePrefix)
                    && atIndex > schemePrefix.length()
                    && normalizedUrl.contains("@raw.githubusercontent.com/")) {
                String token = normalizedUrl.substring(schemePrefix.length(), atIndex);
                String cleanUrl = schemePrefix + normalizedUrl.substring(atIndex + 1);
                String basicAuth = Base64.encodeToString(
                        ("x-access-token:" + token).getBytes(),
                        Base64.NO_WRAP
                );

                return new GlideUrl(
                        cleanUrl,
                        new LazyHeaders.Builder()
                                .addHeader("Authorization", "Basic " + basicAuth)
                                .addHeader("User-Agent", "SoccerExplorer")
                                .build()
                );
            }

            return normalizedUrl;
        }

        @Override
        public int getItemCount() {
            return equipos.size();
        }

        static class EquipoViewHolder extends RecyclerView.ViewHolder {
            final View itemContainer;
            final ImageView ivEscudo;
            final ProgressBar pbEscudoCarga;
            final TextView tvNombreEquipo;
            final TextView tvLigaEquipo;
            final ImageView ivSelected;

            EquipoViewHolder(@NonNull View itemView) {
                super(itemView);
                itemContainer = itemView.findViewById(R.id.itemContainer);
                ivEscudo = itemView.findViewById(R.id.ivEscudo);
                pbEscudoCarga = itemView.findViewById(R.id.pbEscudoCarga);
                tvNombreEquipo = itemView.findViewById(R.id.tvNombreEquipo);
                tvLigaEquipo = itemView.findViewById(R.id.tvLigaEquipo);
                ivSelected = itemView.findViewById(R.id.ivSelected);
            }
        }
    }
    // endregion
}
