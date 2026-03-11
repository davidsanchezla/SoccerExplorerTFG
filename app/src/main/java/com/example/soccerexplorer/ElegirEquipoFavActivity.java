package com.example.soccerexplorer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElegirEquipoFavActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;

    private TextInputLayout tilLiga;
    private AutoCompleteTextView actvLiga;
    private RecyclerView rvEquipos;
    private ProgressBar pbEquipos;
    private TextView tvEquiposEmpty;
    private MaterialButton btnGuardarEquipo;

    private final List<LigaItem> ligas = new ArrayList<>();
    private EquipoAdapter equipoAdapter;

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
        tilLiga = findViewById(R.id.tilLiga);
        actvLiga = findViewById(R.id.actvLiga);
        rvEquipos = findViewById(R.id.rvEquipos);
        pbEquipos = findViewById(R.id.pbEquipos);
        tvEquiposEmpty = findViewById(R.id.tvEquiposEmpty);
        btnGuardarEquipo = findViewById(R.id.btnGuardarEquipo);
    }

    private void configurarRecyclerEquipos() {
        rvEquipos.setLayoutManager(new LinearLayoutManager(this));
        equipoAdapter = new EquipoAdapter(equipo -> {
            equipoSeleccionado = equipo;
            equipoAdapter.actualizarEquipoSeleccionadoId(equipo.id);
            actualizarEstadoBotonGuardar();
        });
        rvEquipos.setAdapter(equipoAdapter);
    }

    private void configurarListeners() {
        actvLiga.setOnItemClickListener((parent, view, position, id) -> {
            LigaItem liga = (LigaItem) parent.getItemAtPosition(position);
            alSeleccionarLiga(liga);
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
                        tilLiga.setError(getString(R.string.fav_team_empty_leagues));
                        tvEquiposEmpty.setText(R.string.fav_team_empty_leagues);
                        tvEquiposEmpty.setVisibility(View.VISIBLE);
                        rvEquipos.setVisibility(View.GONE);
                        return;
                    }

                    tilLiga.setError(null);
                    ArrayAdapter<LigaItem> leagueAdapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            ligas
                    );
                    actvLiga.setAdapter(leagueAdapter);

                    LigaItem primeraLiga = ligas.get(0);
                    actvLiga.setText(primeraLiga.nombre, false);
                    alSeleccionarLiga(primeraLiga);
                })
                .addOnFailureListener(e -> {
                    tilLiga.setError(getString(R.string.fav_team_error_leagues));
                    tvEquiposEmpty.setText(R.string.fav_team_error_leagues);
                    tvEquiposEmpty.setVisibility(View.VISIBLE);
                    rvEquipos.setVisibility(View.GONE);
                });
    }

    private void alSeleccionarLiga(@NonNull LigaItem liga) {
        ligaSeleccionada = liga;
        equipoSeleccionado = null;
        equipoAdapter.actualizarEquipoSeleccionadoId(null);
        actualizarEstadoBotonGuardar();
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
                        equipos.add(new EquipoItem(equipoId, equipoNombre, escudoUrl));
                    });

                    equipos.sort((a, b) -> a.nombre.compareToIgnoreCase(b.nombre));
                    equipoAdapter.actualizarEquipos(equipos);
                    mostrarCargaEquipos(false);

                    if (equipos.isEmpty()) {
                        tvEquiposEmpty.setText(R.string.fav_team_empty_teams);
                        tvEquiposEmpty.setVisibility(View.VISIBLE);
                        rvEquipos.setVisibility(View.GONE);
                        return;
                    }

                    tvEquiposEmpty.setVisibility(View.GONE);
                    rvEquipos.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    equipoAdapter.actualizarEquipos(new ArrayList<>());
                    mostrarCargaEquipos(false);
                    tvEquiposEmpty.setText(R.string.fav_team_error_teams);
                    tvEquiposEmpty.setVisibility(View.VISIBLE);
                    rvEquipos.setVisibility(View.GONE);
                });
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
        @Nullable
        final String escudoUrl;

        EquipoItem(@NonNull String id, @NonNull String nombre, @Nullable String escudoUrl) {
            this.id = id;
            this.nombre = nombre;
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
        private String selectedEquipoId;

        EquipoAdapter(@NonNull OnEquipoClickListener onEquipoClickListener) {
            this.onEquipoClickListener = onEquipoClickListener;
        }

        void actualizarEquipos(@NonNull List<EquipoItem> nuevosEquipos) {
            equipos.clear();
            equipos.addAll(nuevosEquipos);
            notifyDataSetChanged();
        }

        void actualizarEquipoSeleccionadoId(@Nullable String selectedEquipoId) {
            this.selectedEquipoId = selectedEquipoId;
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

            Object modeloEscudo = construirModeloSolicitudEscudo(equipo.escudoUrl);
            Glide.with(holder.itemView.getContext())
                    .load(modeloEscudo)
                    .placeholder(R.mipmap.ic_launcher_round)
                    .error(R.mipmap.ic_launcher_round)
                    .into(holder.ivEscudo);

            boolean selected = equipo.id.equals(selectedEquipoId);
            holder.ivSelected.setVisibility(selected ? View.VISIBLE : View.GONE);
            int backgroundColor = ContextCompat.getColor(
                    holder.itemView.getContext(),
                    selected ? R.color.background_tertiary : R.color.background_secondary
            );
            holder.itemContainer.setBackgroundColor(backgroundColor);

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
            final TextView tvNombreEquipo;
            final ImageView ivSelected;

            EquipoViewHolder(@NonNull View itemView) {
                super(itemView);
                itemContainer = itemView.findViewById(R.id.itemContainer);
                ivEscudo = itemView.findViewById(R.id.ivEscudo);
                tvNombreEquipo = itemView.findViewById(R.id.tvNombreEquipo);
                ivSelected = itemView.findViewById(R.id.ivSelected);
            }
        }
    }
    // endregion
}
