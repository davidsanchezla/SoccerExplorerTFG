package com.example.soccerexplorer;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BuscadorFragment extends Fragment {

    private static final String SECTION_COMPETITIONS = "Competiciones";
    private static final String SECTION_TEAMS = "Equipos";
    private static final String SECTION_NATIONAL_TEAMS = "Selecciones";

    private TextInputEditText etSearch;
    private ProgressBar pbSearch;
    private TextView tvSearchStatus;

    private BuscadorAdapter adapter;
    private final List<CompetitionItem> competitions = new ArrayList<>();
    private final List<TeamItem> teams = new ArrayList<>();
    private final List<TeamItem> nationalTeams = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_buscador, container, false);

        etSearch = view.findViewById(R.id.etSearch);
        pbSearch = view.findViewById(R.id.pbSearch);
        tvSearchStatus = view.findViewById(R.id.tvSearchStatus);

        RecyclerView rvSearchResults = view.findViewById(R.id.rvSearchResults);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BuscadorAdapter();
        rvSearchResults.setAdapter(adapter);

        configurarBuscador();
        cargarDatosBase();

        return view;
    }

    private void configurarBuscador() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filtrarResultados();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void cargarDatosBase() {
        mostrarCarga(true);
        ocultarEstado();

        FirebaseFirestore.getInstance()
                .collection("competiciones")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    competitions.clear();
                    teams.clear();

                    List<CompetitionItem> loadedCompetitions = new ArrayList<>();
                    queryDocumentSnapshots.getDocuments().forEach(doc -> {
                        String ligaId = doc.getId();
                        String ligaNombre = doc.getString("nombre");
                        if (ligaNombre == null || ligaNombre.trim().isEmpty()) {
                            ligaNombre = ligaId;
                        }
                        loadedCompetitions.add(new CompetitionItem(ligaId, ligaNombre));
                    });

                    competitions.addAll(loadedCompetitions);
                    cargarEquiposDeCompeticiones(loadedCompetitions);
                })
                .addOnFailureListener(e -> {
                    mostrarCarga(false);
                    mostrarEstado(getString(R.string.search_error_load));
                });
    }

    private void cargarEquiposDeCompeticiones(@NonNull List<CompetitionItem> competitionItems) {
        if (competitionItems.isEmpty()) {
            mostrarCarga(false);
            filtrarResultados();
            return;
        }

        final int total = competitionItems.size();
        final int[] done = {0};

        for (CompetitionItem competition : competitionItems) {
            FirebaseFirestore.getInstance()
                    .collection("competiciones")
                    .document(competition.id)
                    .collection("equipos")
                    .get()
                    .addOnSuccessListener(teamDocuments -> {
                        teamDocuments.getDocuments().forEach(teamDoc -> {
                            String teamId = teamDoc.getId();
                            String teamName = teamDoc.getString("equipo");
                            if (teamName == null || teamName.trim().isEmpty()) {
                                teamName = teamDoc.getString("nombre");
                            }
                            if (teamName == null || teamName.trim().isEmpty()) {
                                teamName = teamId;
                            }
                            teams.add(new TeamItem(teamId, teamName, competition.name));
                        });

                        done[0]++;
                        if (done[0] >= total) {
                            cargarSelecciones();
                        }
                    })
                    .addOnFailureListener(e -> {
                        done[0]++;
                        if (done[0] >= total) {
                            cargarSelecciones();
                        }
                    });
        }
    }

    private void cargarSelecciones() {
        FirebaseFirestore.getInstance()
                .collection("selecciones")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    nationalTeams.clear();
                    queryDocumentSnapshots.getDocuments().forEach(doc -> {
                        String id = doc.getId();
                        String nombre = doc.getString("nombre");
                        if (nombre == null || nombre.trim().isEmpty()) {
                            nombre = doc.getString("equipo");
                        }
                        if (nombre == null || nombre.trim().isEmpty()) {
                            nombre = id;
                        }
                        nationalTeams.add(new TeamItem(id, nombre, getString(R.string.search_national_team_subtitle)));
                    });

                    mostrarCarga(false);
                    filtrarResultados();
                })
                .addOnFailureListener(e -> {
                    nationalTeams.clear();
                    mostrarCarga(false);
                    filtrarResultados();
                });
    }

    private void filtrarResultados() {
        String queryRaw = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
        String query = normalizeText(queryRaw);

        if (query.isEmpty()) {
            adapter.updateItems(new ArrayList<>());
            mostrarEstado(getString(R.string.search_status_hint));
            return;
        }

        List<CompetitionItem> filteredCompetitions = filtrarCompeticionesPorPrefijo(query);
        List<TeamItem> filteredTeams = filtrarEquiposPorPrefijo(teams, query);
        List<TeamItem> filteredNationalTeams = filtrarEquiposPorPrefijo(nationalTeams, query);

        List<BuscadorAdapter.SearchItem> items = new ArrayList<>();
        agregarSeccionCompeticiones(items, filteredCompetitions);
        agregarSeccionEquipos(items, filteredTeams);
        agregarSeccionSelecciones(items, filteredNationalTeams);

        adapter.updateItems(items);

        if (items.isEmpty()) {
            mostrarEstado(getString(R.string.search_status_empty));
            return;
        }

        ocultarEstado();
    }

    @NonNull
    private List<CompetitionItem> filtrarCompeticionesPorPrefijo(@NonNull String query) {
        List<CompetitionItem> result = new ArrayList<>();
        for (CompetitionItem item : competitions) {
            if (query.isEmpty() || normalizeText(item.name).startsWith(query)) {
                result.add(item);
            }
        }
        Collections.sort(result, Comparator.comparing(item -> normalizeText(item.name)));
        return result;
    }

    @NonNull
    private List<TeamItem> filtrarEquiposPorPrefijo(@NonNull List<TeamItem> source, @NonNull String query) {
        List<TeamItem> result = new ArrayList<>();
        for (TeamItem item : source) {
            if (query.isEmpty() || normalizeText(item.name).startsWith(query)) {
                result.add(item);
            }
        }
        Collections.sort(result, Comparator.comparing(item -> normalizeText(item.name)));
        return result;
    }

    private void agregarSeccionCompeticiones(@NonNull List<BuscadorAdapter.SearchItem> target,
                                             @NonNull List<CompetitionItem> source) {
        for (int i = 0; i < source.size(); i++) {
            CompetitionItem item = source.get(i);
            target.add(new BuscadorAdapter.SearchItem(
                    SECTION_COMPETITIONS,
                    item.name,
                    getString(R.string.search_competition_subtitle),
                    i == 0
            ));
        }
    }

    private void agregarSeccionEquipos(@NonNull List<BuscadorAdapter.SearchItem> target,
                                       @NonNull List<TeamItem> source) {
        for (int i = 0; i < source.size(); i++) {
            TeamItem item = source.get(i);
            target.add(new BuscadorAdapter.SearchItem(
                    SECTION_TEAMS,
                    item.name,
                    item.subtitle,
                    i == 0
            ));
        }
    }

    private void agregarSeccionSelecciones(@NonNull List<BuscadorAdapter.SearchItem> target,
                                           @NonNull List<TeamItem> source) {
        for (int i = 0; i < source.size(); i++) {
            TeamItem item = source.get(i);
            target.add(new BuscadorAdapter.SearchItem(
                    SECTION_NATIONAL_TEAMS,
                    item.name,
                    item.subtitle,
                    i == 0
            ));
        }
    }

    @NonNull
    private String normalizeText(@NonNull String input) {
        String normalized = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "").trim();
    }

    private void mostrarCarga(boolean loading) {
        pbSearch.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void mostrarEstado(@NonNull String message) {
        tvSearchStatus.setText(message);
        tvSearchStatus.setVisibility(View.VISIBLE);
    }

    private void ocultarEstado() {
        tvSearchStatus.setVisibility(View.GONE);
    }

    private static class CompetitionItem {
        final String id;
        final String name;

        CompetitionItem(@NonNull String id, @NonNull String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class TeamItem {
        final String id;
        final String name;
        final String subtitle;

        TeamItem(@NonNull String id, @NonNull String name, @NonNull String subtitle) {
            this.id = id;
            this.name = name;
            this.subtitle = subtitle;
        }
    }
}
