package com.example.soccerexplorer;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BuscadorFragment extends Fragment {

    private static final String SECTION_COMPETITIONS = "Competiciones";
    private static final String SECTION_TEAMS = "Equipos";
    private static final String SECTION_NATIONAL_TEAMS = "Selecciones";

    private TextInputEditText etSearch;
    private ProgressBar pbSearch;
    private TextView tvSearchStatus;
    private View searchRoot;

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
        searchRoot = view.findViewById(R.id.searchRoot);

        RecyclerView rvSearchResults = view.findViewById(R.id.rvSearchResults);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BuscadorAdapter();
        rvSearchResults.setAdapter(adapter);

        adapter.setOnItemClickListener(item -> {
            if (SECTION_TEAMS.equals(item.section) || SECTION_NATIONAL_TEAMS.equals(item.section)) {
                if (item.competitionId != null) {
                    Intent intent = new Intent(requireContext(), TeamDetailsActivity.class);
                    intent.putExtra(TeamDetailsActivity.EXTRA_COMPETITION_CODE, item.competitionId);
                    intent.putExtra(TeamDetailsActivity.EXTRA_TEAM_NAME, item.name);
                    startActivity(intent);
                }
            }
        });

        configurarBuscador();
        configurarCierreTeclado();
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

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            ocultarTeclado();
            etSearch.clearFocus();
            return false;
        });
    }

    private void configurarCierreTeclado() {
        searchRoot.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (etSearch.hasFocus()) {
                    etSearch.clearFocus();
                    ocultarTeclado();
                }
            }
            return false;
        });
    }

    private void ocultarTeclado() {
        if (!isAdded() || getContext() == null || getActivity() == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null && getActivity().getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
        }
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
        final Map<String, TeamItem> uniqueTeamsByName = new LinkedHashMap<>();

        for (CompetitionItem competition : competitionItems) {
            boolean isInternationalCompetition = esCompeticionInternacional(competition.name);

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
                            TeamItem newItem = new TeamItem(
                                    teamId,
                                    teamName,
                                    competition.name,
                                    isInternationalCompetition,
                                    competition.id
                            );

                            String key = construirClaveUnicaEquipo(teamName);
                            TeamItem existing = uniqueTeamsByName.get(key);
                            if (existing == null) {
                                uniqueTeamsByName.put(key, newItem);
                            } else if (existing.isInternationalCompetition && !newItem.isInternationalCompetition) {
                                uniqueTeamsByName.put(key, newItem);
                            }
                        });

                        done[0]++;
                        if (done[0] >= total) {
                            teams.clear();
                            teams.addAll(uniqueTeamsByName.values());
                            cargarSelecciones();
                        }
                    })
                    .addOnFailureListener(e -> {
                        done[0]++;
                        if (done[0] >= total) {
                            teams.clear();
                            teams.addAll(uniqueTeamsByName.values());
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
                        nationalTeams.add(new TeamItem(
                                id,
                                nombre,
                                getString(R.string.search_national_team_subtitle),
                                false,
                                null
                        ));
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

        List<CompetitionItem> filteredCompetitions = filtrarCompeticionesPorPrefijo(query);
        List<TeamItem> filteredTeams = filtrarEquiposPorPrefijo(teams, query);
        List<TeamItem> filteredNationalTeams = filtrarEquiposPorPrefijo(nationalTeams, query);

        List<BuscadorAdapter.SearchItem> items = new ArrayList<>();
        agregarSeccionCompeticiones(items, filteredCompetitions);
        agregarSeccionEquipos(items, filteredTeams);
        agregarSeccionSelecciones(items, filteredNationalTeams);

        adapter.updateItems(items);

        if (query.isEmpty()) {
            if (items.isEmpty()) {
                mostrarEstado(getString(R.string.search_status_hint));
            } else {
                ocultarEstado();
            }
            return;
        }

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
            if (query.isEmpty() || normalizeText(item.name).contains(query)) {
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
            if (query.isEmpty() || normalizeText(item.name).contains(query)) {
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
                    i == 0,
                    item.id
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
                    i == 0,
                    item.competitionId
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
                    i == 0,
                    item.competitionId
            ));
        }
    }

    private boolean esCompeticionInternacional(@NonNull String competitionName) {
        String normalized = normalizeText(competitionName);

        return normalized.contains("champions")
                || normalized.contains("europa league")
                || normalized.contains("conference league")
                || normalized.contains("supercopa de europa")
                || normalized.contains("libertadores")
                || normalized.contains("sudamericana")
                || normalized.contains("mundial")
                || normalized.contains("fifa")
                || normalized.contains("uefa")
                || normalized.contains("conmebol")
                || normalized.contains("concacaf")
                || normalized.contains("afc")
                || normalized.contains("caf")
                || normalized.contains("nations league")
                || normalized.contains("copa america")
                || normalized.contains("eurocopa")
                || normalized.contains("european championship")
                || normalized.contains("world cup");
    }

    @NonNull
    private String construirClaveUnicaEquipo(@NonNull String teamName) {
        String canonical = normalizarNombreEquipo(teamName);
        if (canonical.isEmpty()) {
            canonical = normalizeText(teamName);
        }
        return canonical;
    }

    @NonNull
    private String normalizarNombreEquipo(@NonNull String rawName) {
        String normalized = normalizeText(rawName)
                .replace('.', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        normalized = normalized
                .replaceAll("\\b(fc|cf|sc|ac|afc|cfc|cd|ud|sad)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.equals("ajax amsterdam")) {
            return "ajax";
        }

        if (normalized.startsWith("arsenal ")) {
            return "arsenal";
        }

        return normalized;
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
        final boolean isInternationalCompetition;
        @Nullable
        final String competitionId;

        TeamItem(@NonNull String id,
                 @NonNull String name,
                 @NonNull String subtitle,
                 boolean isInternationalCompetition,
                 @Nullable String competitionId) {
            this.id = id;
            this.name = name;
            this.subtitle = subtitle;
            this.isInternationalCompetition = isInternationalCompetition;
            this.competitionId = competitionId;
        }
    }
}
