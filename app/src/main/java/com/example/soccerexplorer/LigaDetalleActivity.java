package com.example.soccerexplorer;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LigaDetalleActivity extends AppCompatActivity {

    public static final String EXTRA_COMPETITION_NAME = "extra_competition_name";
    public static final String EXTRA_COMPETITION_CODE = "extra_competition_code";
    public static final String EXTRA_SELECTED_DATE = "extra_selected_date";

    private static final String API_BASE = "https://api.football-data.org/v4";
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Madrid");
    private static final Locale APP_LOCALE = new Locale("es", "ES");
    private static final DateTimeFormatter MATCH_META_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", APP_LOCALE);

    private static final Map<String, List<Integer>> jornadasCache = new HashMap<>();
    private static final long CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L;

    private static final String ESTADO_LIVE = "IN_PLAY";
    private static final String ESTADO_PAUSED = "PAUSED";
    private static final String ESTADO_SCHEDULED = "SCHEDULED";
    private static final String ESTADO_TIMED = "TIMED";

    private MaterialToolbar toolbar;
    private TextView tvLeagueTitle;
    private View layoutMatchdayFilter;
    private Spinner spinnerMatchday;
    private TabLayout tabLayout;
    private TextView tvSubtitle;
    private View pbLoading;
    private TextView tvStatus;
    private RecyclerView recyclerView;

    private LigaPartidosAdapter partidosAdapter;
    private LigaClasificacionAdapter clasificacionAdapter;
    private LigaEstadisticasAdapter estadisticasAdapter;

    private ExecutorService executorService;

    private final List<Integer> jornadasDisponibles = new ArrayList<>();
    private JornadaSpinnerAdapter jornadaAdapter;

    private boolean bloqueandoEventoJornada = true;
    private int selectedMatchday = -1;

    @NonNull
    private String selectedCompetitionName = "";
    @NonNull
    private String selectedCompetitionCode = "";

    private Section currentSection = Section.MATCHES;

    private int requestSequence = 0;
    private volatile int activeRequestId = 0;

    private android.content.SharedPreferences prefsJornadas;
    private long cacheTimestamp = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_liga_detalle);

        toolbar = findViewById(R.id.toolbarLigaDetalle);
        tvLeagueTitle = findViewById(R.id.tvLigaDetalleLeagueTitle);
        layoutMatchdayFilter = findViewById(R.id.layoutLigaMatchdayFilter);
        spinnerMatchday = findViewById(R.id.spinnerJornadaDetalle);
        tabLayout = findViewById(R.id.tabLayoutLigaDetalle);
        tvSubtitle = findViewById(R.id.tvLigaDetalleSubtitle);
        pbLoading = findViewById(R.id.pbLigaDetalle);
        tvStatus = findViewById(R.id.tvLigaDetalleStatus);
        recyclerView = findViewById(R.id.rvLigaDetalle);

        setSupportActionBar(toolbar);
        toolbar.setTitle("");
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        partidosAdapter = new LigaPartidosAdapter();
        clasificacionAdapter = new LigaClasificacionAdapter();
        estadisticasAdapter = new LigaEstadisticasAdapter();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(partidosAdapter);

        executorService = Executors.newSingleThreadExecutor();

        prefsJornadas = getSharedPreferences("jornadas_cache", MODE_PRIVATE);

        leerExtrasIniciales();
        actualizarCabecera();
        configurarTabs();
        configurarSpinnerJornadas();
        actualizarVisibilidadFiltroJornada();
        actualizarSubtitulo();
        cargarJornadasIniciales();
    }

    private void leerExtrasIniciales() {
        String codeExtra = getIntent().getStringExtra(EXTRA_COMPETITION_CODE);
        String nameExtra = getIntent().getStringExtra(EXTRA_COMPETITION_NAME);

        if (codeExtra != null) {
            selectedCompetitionCode = codeExtra.trim();
        }
        if (selectedCompetitionCode.isEmpty()) {
            selectedCompetitionCode = "PD";
        }

        if (nameExtra != null && !nameExtra.trim().isEmpty()) {
            selectedCompetitionName = nameExtra.trim();
        }
        if (selectedCompetitionName.isEmpty()) {
            selectedCompetitionName = selectedCompetitionCode;
        }
    }

    private void configurarTabs() {
        tabLayout.removeAllTabs();
        tabLayout.addTab(tabLayout.newTab().setText(R.string.liga_tab_matches), true);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.liga_tab_standings));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.liga_tab_stats));
        currentSection = Section.MATCHES;

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentSection = sectionFromPosition(tab.getPosition());
                actualizarVisibilidadFiltroJornada();
                actualizarSubtitulo();
                cargarDatosActuales();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // no-op
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                currentSection = sectionFromPosition(tab.getPosition());
                actualizarVisibilidadFiltroJornada();
                actualizarSubtitulo();
                cargarDatosActuales();
            }
        });
    }

    private void configurarSpinnerJornadas() {
        jornadaAdapter = new JornadaSpinnerAdapter(new ArrayList<>());
        spinnerMatchday.setAdapter(jornadaAdapter);

        spinnerMatchday.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (bloqueandoEventoJornada || position < 0 || position >= jornadasDisponibles.size()) {
                    return;
                }
                int jornada = jornadasDisponibles.get(position);
                if (jornada == selectedMatchday) {
                    return;
                }
                selectedMatchday = jornada;
                actualizarSubtitulo();
                if (currentSection == Section.MATCHES) {
                    cargarDatosActuales();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });
    }

    private void cargarJornadasIniciales() {
        final int requestId = ++requestSequence;
        activeRequestId = requestId;

        mostrarCarga(true);
        ocultarEstado();

        if (executorService == null) {
            return;
        }

        executorService.execute(() -> {
            if (requestId != activeRequestId) {
                return;
            }

            int jornadaObjetivo = obtenerJornadaObjetivo(selectedCompetitionCode);
            List<Integer> jornadas = obtenerJornadasDesdeCache(selectedCompetitionCode);

            if (jornadas != null && !jornadas.isEmpty()) {
                if (jornadaObjetivo > 0 && !jornadas.contains(jornadaObjetivo)) {
                    jornadas.add(jornadaObjetivo);
                    Collections.sort(jornadas);
                }

                final List<Integer> jornadasFinal = new ArrayList<>(jornadas);
                int jornadaInicial = seleccionarJornadaInicial(jornadas, jornadaObjetivo);
                final int jornadaInicialFinal = jornadaInicial;

                runOnUiThread(() -> {
                    if (!esRequestVigente(requestId)) {
                        return;
                    }
                    selectedMatchday = jornadaInicialFinal;
                    actualizarOpcionesJornadas(jornadasFinal, jornadaInicialFinal);
                    actualizarSubtitulo();
                    cargarDatosActuales();
                });
                return;
            }

            ApiResult allMatchesResult = ejecutarGet("/competitions/" + selectedCompetitionCode + "/matches");
            if (!allMatchesResult.ok && jornadaObjetivo <= 0) {
                publicarError(resultadoApiToMessage(allMatchesResult), requestId);
                return;
            }

            try {
                jornadas = allMatchesResult.ok
                        ? extraerJornadasDisponibles(allMatchesResult.body)
                        : new ArrayList<>();
            } catch (Exception e) {
                jornadas = new ArrayList<>();
            }

            if (!jornadas.isEmpty()) {
                guardarJornadasEnCache(selectedCompetitionCode, jornadas);
            }

            if (jornadaObjetivo > 0 && !jornadas.contains(jornadaObjetivo)) {
                jornadas.add(jornadaObjetivo);
                Collections.sort(jornadas);
            }

            int jornadaInicial = seleccionarJornadaInicial(jornadas, jornadaObjetivo);
            final List<Integer> jornadasFinal = new ArrayList<>(jornadas);
            final int jornadaInicialFinal = jornadaInicial;

            runOnUiThread(() -> {
                if (!esRequestVigente(requestId)) {
                    return;
                }

                if (jornadasFinal.isEmpty() || jornadaInicialFinal <= 0) {
                    mostrarCarga(false);
                    mostrarEstado(getString(R.string.liga_matchday_not_found));
                    return;
                }

                selectedMatchday = jornadaInicialFinal;
                actualizarOpcionesJornadas(jornadasFinal, jornadaInicialFinal);
                actualizarSubtitulo();
                cargarDatosActuales();
            });
        });
    }

    @Nullable
    private List<Integer> obtenerJornadasDesdeCache(@NonNull String competitionCode) {
        if (jornadasCache.containsKey(competitionCode)) {
            return new ArrayList<>(jornadasCache.get(competitionCode));
        }

        String cachedJson = prefsJornadas.getString("jornadas_" + competitionCode, null);
        long cachedTime = prefsJornadas.getLong("timestamp_" + competitionCode, 0);

        if (cachedJson != null && cachedTime > 0) {
            long elapsed = System.currentTimeMillis() - cachedTime;
            if (elapsed < CACHE_EXPIRY_MS) {
                try {
                    String[] parts = cachedJson.split(",");
                    List<Integer> cached = new ArrayList<>();
                    for (String p : parts) {
                        int val = Integer.parseInt(p.trim());
                        cached.add(val);
                    }
                    jornadasCache.put(competitionCode, cached);
                    return cached;
                } catch (NumberFormatException e) {
                    // invalid cache
                }
            }
        }

        return null;
    }

    private void guardarJornadasEnCache(@NonNull String competitionCode, @NonNull List<Integer> jornadas) {
        jornadasCache.put(competitionCode, new ArrayList<>(jornadas));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < jornadas.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(jornadas.get(i));
        }

        prefsJornadas.edit()
                .putString("jornadas_" + competitionCode, sb.toString())
                .putLong("timestamp_" + competitionCode, System.currentTimeMillis())
                .apply();
    }

    private void actualizarOpcionesJornadas(@NonNull List<Integer> jornadas, int jornadaSeleccionada) {
        jornadasDisponibles.clear();
        jornadasDisponibles.addAll(jornadas);

        List<String> labels = new ArrayList<>();
        for (Integer jornada : jornadasDisponibles) {
            labels.add(getString(R.string.liga_matchday_item, jornada));
        }

        bloqueandoEventoJornada = true;
        jornadaAdapter.clear();
        jornadaAdapter.addAll(labels);
        jornadaAdapter.notifyDataSetChanged();

        int selectedIndex = 0;
        for (int i = 0; i < jornadasDisponibles.size(); i++) {
            if (jornadasDisponibles.get(i) == jornadaSeleccionada) {
                selectedIndex = i;
                break;
            }
        }
        spinnerMatchday.setSelection(selectedIndex, false);
        bloqueandoEventoJornada = false;
    }

    private int seleccionarJornadaInicial(@NonNull List<Integer> jornadas, int jornadaObjetivo) {
        if (jornadas.isEmpty()) {
            return -1;
        }
        if (jornadaObjetivo > 0 && jornadas.contains(jornadaObjetivo)) {
            return jornadaObjetivo;
        }
        return jornadas.get(jornadas.size() - 1);
    }

    @NonNull
    private Section sectionFromPosition(int position) {
        if (position == 1) {
            return Section.STANDINGS;
        }
        if (position == 2) {
            return Section.STATS;
        }
        return Section.MATCHES;
    }

    private void actualizarCabecera() {
        tvLeagueTitle.setText(selectedCompetitionName);
    }

    private void actualizarVisibilidadFiltroJornada() {
        layoutMatchdayFilter.setVisibility(currentSection == Section.MATCHES ? View.VISIBLE : View.GONE);
    }

    private void actualizarSubtitulo() {
        if (currentSection == Section.MATCHES) {
            if (selectedMatchday > 0) {
                tvSubtitle.setText(getString(
                        R.string.liga_matchday_subtitle_format,
                        selectedMatchday,
                        selectedCompetitionName
                ));
            } else {
                tvSubtitle.setText(getString(R.string.liga_matchday_not_found));
            }
            return;
        }

        if (currentSection == Section.STANDINGS) {
            tvSubtitle.setText(getString(
                    R.string.liga_standings_subtitle_format,
                    selectedCompetitionName
            ));
            return;
        }

        tvSubtitle.setText(getString(
                R.string.liga_stats_subtitle_format,
                selectedCompetitionName
        ));
    }

    private void cargarDatosActuales() {
        if (executorService == null) {
            return;
        }

        final int requestId = ++requestSequence;
        activeRequestId = requestId;

        mostrarCarga(true);
        ocultarEstado();

        if (currentSection == Section.MATCHES) {
            recyclerView.setAdapter(partidosAdapter);
            partidosAdapter.actualizarItems(new ArrayList<>());
            if (selectedMatchday <= 0) {
                mostrarCarga(false);
                mostrarEstado(getString(R.string.liga_matchday_not_found));
                return;
            }
            cargarPartidosJornada(requestId);
            return;
        }

        if (currentSection == Section.STANDINGS) {
            recyclerView.setAdapter(clasificacionAdapter);
            clasificacionAdapter.actualizarItems(new ArrayList<>());
            cargarClasificacion(requestId);
            return;
        }

        recyclerView.setAdapter(estadisticasAdapter);
        estadisticasAdapter.actualizarItems(new ArrayList<>());
        cargarEstadisticas(requestId);
    }

    private void cargarPartidosJornada(int requestId) {
        executorService.execute(() -> {
            if (requestId != activeRequestId) {
                return;
            }

            ApiResult result = ejecutarGet(
                    "/competitions/" + selectedCompetitionCode + "/matches?matchday=" + selectedMatchday
            );
            if (!result.ok) {
                publicarError(resultadoApiToMessage(result), requestId);
                return;
            }

            List<LigaPartidosAdapter.MatchItem> partidos;
            try {
                partidos = parsePartidosJornada(result.body);
            } catch (Exception e) {
                publicarError(getString(R.string.liga_error_parse_matches), requestId);
                return;
            }

            runOnUiThread(() -> {
                if (!esRequestVigente(requestId)) {
                    return;
                }

                mostrarCarga(false);
                if (partidos.isEmpty()) {
                    mostrarEstado(getString(R.string.liga_matches_empty_matchday, selectedMatchday));
                    partidosAdapter.actualizarItems(new ArrayList<>());
                    return;
                }

                ocultarEstado();
                partidosAdapter.actualizarItems(partidos);
            });
        });
    }

    private void cargarClasificacion(int requestId) {
        executorService.execute(() -> {
            if (requestId != activeRequestId) {
                return;
            }

            ApiResult result = ejecutarGet(
                    "/competitions/" + selectedCompetitionCode + "/standings"
            );
            if (!result.ok) {
                publicarError(resultadoApiToMessage(result), requestId);
                return;
            }

            List<LigaClasificacionAdapter.StandingItem> standings;
            try {
                standings = parseClasificacion(result.body);
            } catch (Exception e) {
                publicarError(getString(R.string.liga_error_parse_standings), requestId);
                return;
            }

            runOnUiThread(() -> {
                if (!esRequestVigente(requestId)) {
                    return;
                }

                mostrarCarga(false);
                if (standings.isEmpty()) {
                    mostrarEstado(getString(R.string.liga_standings_empty));
                    clasificacionAdapter.actualizarItems(new ArrayList<>());
                    return;
                }

                ocultarEstado();
                clasificacionAdapter.actualizarItems(standings);
            });
        });
    }

    private void cargarEstadisticas(int requestId) {
        executorService.execute(() -> {
            if (requestId != activeRequestId) {
                return;
            }

            ApiResult result = ejecutarGet(
                    "/competitions/" + selectedCompetitionCode + "/scorers"
            );
            if (!result.ok) {
                publicarError(resultadoApiToMessage(result), requestId);
                return;
            }

            List<LigaEstadisticasAdapter.ScorerItem> scorers;
            try {
                scorers = parseEstadisticas(result.body);
            } catch (Exception e) {
                publicarError(getString(R.string.liga_error_parse_stats), requestId);
                return;
            }

            runOnUiThread(() -> {
                if (!esRequestVigente(requestId)) {
                    return;
                }

                mostrarCarga(false);
                if (scorers.isEmpty()) {
                    mostrarEstado(getString(R.string.liga_stats_empty));
                    estadisticasAdapter.actualizarItems(new ArrayList<>());
                    return;
                }

                ocultarEstado();
                estadisticasAdapter.actualizarItems(scorers);
            });
        });
    }

    @NonNull
    private List<Integer> extraerJornadasDisponibles(@NonNull String body) throws Exception {
        Set<Integer> jornadasSet = new TreeSet<>();

        JSONObject root = new JSONObject(body);
        JSONArray matches = root.optJSONArray("matches");
        if (matches == null) {
            return new ArrayList<>();
        }

        for (int i = 0; i < matches.length(); i++) {
            JSONObject item = matches.optJSONObject(i);
            if (item == null) {
                continue;
            }
            int matchday = item.optInt("matchday", -1);
            if (matchday > 0) {
                jornadasSet.add(matchday);
            }
        }

        return new ArrayList<>(jornadasSet);
    }

    @NonNull
    private List<LigaPartidosAdapter.MatchItem> parsePartidosJornada(@NonNull String body) throws Exception {
        List<LigaPartidosAdapter.MatchItem> out = new ArrayList<>();

        JSONObject root = new JSONObject(body);
        JSONArray matches = root.optJSONArray("matches");
        if (matches == null) {
            return out;
        }

        for (int i = 0; i < matches.length(); i++) {
            JSONObject item = matches.optJSONObject(i);
            if (item == null) {
                continue;
            }

            JSONObject homeTeamObj = item.optJSONObject("homeTeam");
            JSONObject awayTeamObj = item.optJSONObject("awayTeam");
            JSONObject scoreObj = item.optJSONObject("score");

            String homeTeam = homeTeamObj != null ? homeTeamObj.optString("name", "-") : "-";
            String awayTeam = awayTeamObj != null ? awayTeamObj.optString("name", "-") : "-";
            String homeLogo = homeTeamObj != null ? homeTeamObj.optString("crest", null) : null;
            String awayLogo = awayTeamObj != null ? awayTeamObj.optString("crest", null) : null;

            String statusCode = item.optString("status", "");
            String utcDate = item.optString("utcDate", "");
            LocalDateTime dateTime = parseUtcToMadrid(utcDate);
            long kickoffEpochMs = parseUtcToEpochMs(utcDate);

            boolean live = ESTADO_LIVE.equals(statusCode) || ESTADO_PAUSED.equals(statusCode);
            boolean scheduled = ESTADO_SCHEDULED.equals(statusCode) || ESTADO_TIMED.equals(statusCode);

            String meta = dateTime == null ? "" : dateTime.format(MATCH_META_FORMAT);

            String scoreOrTime;
            String statusText;
            if (scheduled) {
                scoreOrTime = dateTime == null
                        ? "-:-"
                        : dateTime.format(DateTimeFormatter.ofPattern("HH:mm", APP_LOCALE));
                statusText = getString(R.string.matches_status_upcoming);
            } else {
                Integer homeGoals = leerGoles(scoreObj, "home");
                Integer awayGoals = leerGoles(scoreObj, "away");

                String homeGoalsText = homeGoals == null ? "-" : String.valueOf(homeGoals);
                String awayGoalsText = awayGoals == null ? "-" : String.valueOf(awayGoals);
                scoreOrTime = homeGoalsText + " - " + awayGoalsText;

                if (live) {
                    statusText = getString(R.string.matches_status_live);
                } else {
                    statusText = traducirEstado(statusCode);
                }
            }

            out.add(new LigaPartidosAdapter.MatchItem(
                    meta,
                    homeTeam,
                    awayTeam,
                    scoreOrTime,
                    statusText,
                    homeLogo,
                    awayLogo,
                    live,
                    kickoffEpochMs
            ));
        }

        Collections.sort(out, (a, b) -> {
            if (a.live && !b.live) {
                return -1;
            }
            if (!a.live && b.live) {
                return 1;
            }
            return Long.compare(a.kickoffEpochMs, b.kickoffEpochMs);
        });

        return out;
    }

    @Nullable
    private Integer leerGoles(@Nullable JSONObject scoreObj, @NonNull String side) {
        if (scoreObj == null) {
            return null;
        }

        String[] scoreKeys = {"fullTime", "regularTime", "halfTime"};
        for (String key : scoreKeys) {
            JSONObject block = scoreObj.optJSONObject(key);
            if (block == null || block.isNull(side)) {
                continue;
            }
            return block.optInt(side);
        }
        return null;
    }

    @NonNull
    private List<LigaClasificacionAdapter.StandingItem> parseClasificacion(@NonNull String body) throws Exception {
        List<LigaClasificacionAdapter.StandingItem> out = new ArrayList<>();

        JSONObject root = new JSONObject(body);
        JSONArray standings = root.optJSONArray("standings");
        if (standings == null) {
            return out;
        }

        JSONArray selectedTable = null;
        for (int i = 0; i < standings.length(); i++) {
            JSONObject standingObj = standings.optJSONObject(i);
            if (standingObj == null) {
                continue;
            }
            JSONArray table = standingObj.optJSONArray("table");
            if (table == null || table.length() == 0) {
                continue;
            }

            String type = standingObj.optString("type", "");
            if ("TOTAL".equalsIgnoreCase(type)) {
                selectedTable = table;
                break;
            }
            if (selectedTable == null) {
                selectedTable = table;
            }
        }

        if (selectedTable == null) {
            return out;
        }

        for (int i = 0; i < selectedTable.length(); i++) {
            JSONObject row = selectedTable.optJSONObject(i);
            if (row == null) {
                continue;
            }

            JSONObject teamObj = row.optJSONObject("team");
            String teamName = teamObj != null ? teamObj.optString("name", "-") : "-";
            String teamLogo = teamObj != null ? teamObj.optString("crest", null) : null;

            int position = row.optInt("position", i + 1);
            int points = row.optInt("points", 0);
            int played = row.optInt("playedGames", 0);
            int won = row.optInt("won", 0);
            int draw = row.optInt("draw", 0);
            int lost = row.optInt("lost", 0);
            int gd = row.optInt("goalDifference", 0);

            out.add(new LigaClasificacionAdapter.StandingItem(
                    position,
                    teamName,
                    teamLogo,
                    points,
                    played,
                    won,
                    draw,
                    lost,
                    gd
            ));
        }

        Collections.sort(out, (a, b) -> Integer.compare(a.position, b.position));
        return out;
    }

    @NonNull
    private List<LigaEstadisticasAdapter.ScorerItem> parseEstadisticas(@NonNull String body) throws Exception {
        List<LigaEstadisticasAdapter.ScorerItem> out = new ArrayList<>();

        JSONObject root = new JSONObject(body);
        JSONArray scorers = root.optJSONArray("scorers");
        if (scorers == null) {
            return out;
        }

        for (int i = 0; i < scorers.length(); i++) {
            JSONObject item = scorers.optJSONObject(i);
            if (item == null) {
                continue;
            }

            JSONObject playerObj = item.optJSONObject("player");
            JSONObject teamObj = item.optJSONObject("team");

            String playerName = playerObj != null ? playerObj.optString("name", "-") : "-";
            String teamName = teamObj != null ? teamObj.optString("name", "-") : "-";
            String teamLogo = teamObj != null ? teamObj.optString("crest", null) : null;

            int goals = item.optInt("goals", 0);
            int assists = item.isNull("assists") ? -1 : item.optInt("assists", -1);
            int playedMatches = item.optInt("playedMatches", 0);

            out.add(new LigaEstadisticasAdapter.ScorerItem(
                    playerName,
                    teamName,
                    teamLogo,
                    goals,
                    assists,
                    playedMatches
            ));
        }

        Collections.sort(out, (a, b) -> Integer.compare(b.goals, a.goals));
        return out;
    }

    private int obtenerJornadaObjetivo(@NonNull String competitionCode) {
        ApiResult scheduled = ejecutarGet("/competitions/" + competitionCode + "/matches?status=SCHEDULED,TIMED");
        if (!scheduled.ok) {
            return -1;
        }

        int candidataBase = seleccionarJornadaConMasPartidos(scheduled.body, -1L);
        if (candidataBase <= 0) {
            return -1;
        }

        ApiResult started = ejecutarGet("/competitions/" + competitionCode + "/matches?status=IN_PLAY,PAUSED,FINISHED,SUSPENDED");
        if (!started.ok) {
            return candidataBase;
        }

        long nowMs = System.currentTimeMillis();
        int nextAfterNow = seleccionarJornadaConMasPartidos(scheduled.body, nowMs);
        if (nextAfterNow > 0) {
            return nextAfterNow;
        }

        return candidataBase;
    }

    private int seleccionarJornadaConMasPartidos(@NonNull String body, long minKickoffEpochMsExclusive) {
        try {
            JSONObject root = new JSONObject(body);
            JSONArray matchesArray = root.optJSONArray("matches");
            if (matchesArray == null || matchesArray.length() == 0) {
                return -1;
            }

            List<MatchdayCount> stats = new ArrayList<>();
            for (int i = 0; i < matchesArray.length(); i++) {
                JSONObject obj = matchesArray.optJSONObject(i);
                if (obj == null) {
                    continue;
                }

                int matchday = obj.optInt("matchday", -1);
                if (matchday <= 0) {
                    continue;
                }

                long kickoff = parseUtcToEpochMs(obj.optString("utcDate", ""));
                if (kickoff <= minKickoffEpochMsExclusive) {
                    continue;
                }

                MatchdayCount found = null;
                for (MatchdayCount current : stats) {
                    if (current.matchday == matchday) {
                        found = current;
                        break;
                    }
                }
                if (found == null) {
                    found = new MatchdayCount(matchday);
                    stats.add(found);
                }

                found.count++;
                if (found.minKickoffEpochMs <= 0L || kickoff < found.minKickoffEpochMs) {
                    found.minKickoffEpochMs = kickoff;
                }
            }

            MatchdayCount best = null;
            for (MatchdayCount current : stats) {
                if (best == null) {
                    best = current;
                    continue;
                }

                if (current.count > best.count) {
                    best = current;
                    continue;
                }

                if (current.count == best.count
                        && current.minKickoffEpochMs > 0
                        && (best.minKickoffEpochMs <= 0 || current.minKickoffEpochMs < best.minKickoffEpochMs)) {
                    best = current;
                }
            }

            return best == null ? -1 : best.matchday;
        } catch (Exception ignored) {
            return -1;
        }
    }

    @Nullable
    private LocalDateTime parseUtcToMadrid(@Nullable String utcDate) {
        if (utcDate == null || utcDate.trim().isEmpty()) {
            return null;
        }

        try {
            Instant instant = Instant.parse(utcDate);
            return LocalDateTime.ofInstant(instant, APP_ZONE);
        } catch (Exception e) {
            return null;
        }
    }

    private long parseUtcToEpochMs(@Nullable String utcDate) {
        if (utcDate == null || utcDate.trim().isEmpty()) {
            return 0L;
        }

        try {
            return Instant.parse(utcDate).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    @NonNull
    private String traducirEstado(@NonNull String status) {
        switch (status) {
            case "FINISHED":
                return getString(R.string.matches_status_finished);
            case "POSTPONED":
                return getString(R.string.matches_status_postponed);
            case "SUSPENDED":
                return getString(R.string.matches_status_suspended);
            case "CANCELLED":
                return getString(R.string.matches_status_cancelled);
            case "PAUSED":
                return getString(R.string.matches_status_paused);
            case "IN_PLAY":
                return getString(R.string.matches_status_live);
            default:
                return status;
        }
    }

    private void publicarError(@NonNull String message, int requestId) {
        runOnUiThread(() -> {
            if (!esRequestVigente(requestId)) {
                return;
            }
            mostrarCarga(false);
            mostrarEstado(message);
        });
    }

    private boolean esRequestVigente(int requestId) {
        return !isFinishing() && !isDestroyed() && requestId == activeRequestId;
    }

    @NonNull
    private ApiResult ejecutarGet(@NonNull String pathAndQuery) {
        if (BuildConfig.FOOTBALL_DATA_API_KEY == null || BuildConfig.FOOTBALL_DATA_API_KEY.trim().isEmpty()) {
            return ApiResult.error(401, "missing-key");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_BASE + pathAndQuery);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("X-Auth-Token", BuildConfig.FOOTBALL_DATA_API_KEY);
            connection.setRequestProperty("User-Agent", "SoccerExplorer-Android");
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            InputStream inputStream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = leerInputStream(inputStream);

            if (code >= 200 && code < 300) {
                return ApiResult.ok(code, body);
            }
            return ApiResult.error(code, body);
        } catch (Exception e) {
            return ApiResult.error(500, e.getMessage() == null ? "" : e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private String leerInputStream(@Nullable InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    @NonNull
    private String resultadoApiToMessage(@NonNull ApiResult result) {
        if (result.code == 401 && "missing-key".equalsIgnoreCase(result.body)) {
            return getString(R.string.liga_error_missing_api_key);
        }
        if (result.code == 401 || result.code == 403) {
            return getString(R.string.liga_error_invalid_api_key);
        }
        if (result.code == 404) {
            return getString(R.string.liga_error_not_available_competition);
        }
        if (result.code == 429) {
            return getString(R.string.liga_error_rate_limit);
        }
        if (result.code == 500) {
            return getString(R.string.liga_error_network);
        }
        return getString(R.string.liga_error_api_generic, result.code);
    }

    private void mostrarCarga(boolean loading) {
        pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            tvStatus.setVisibility(View.GONE);
        }
    }

    private void mostrarEstado(@NonNull String message) {
        tvStatus.setText(message);
        tvStatus.setVisibility(View.VISIBLE);
    }

    private void ocultarEstado() {
        tvStatus.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        activeRequestId = -1;
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        super.onDestroy();
    }

    private enum Section {
        MATCHES,
        STANDINGS,
        STATS
    }

    private static class MatchdayCount {
        final int matchday;
        int count;
        long minKickoffEpochMs;

        MatchdayCount(int matchday) {
            this.matchday = matchday;
            this.count = 0;
            this.minKickoffEpochMs = 0L;
        }
    }

    private class JornadaSpinnerAdapter extends ArrayAdapter<String> {

        JornadaSpinnerAdapter(@NonNull List<String> items) {
            super(LigaDetalleActivity.this, android.R.layout.simple_spinner_item, items);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(
                        ContextCompat.getColor(LigaDetalleActivity.this, R.color.text_primary)
                );
            }
            return view;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = super.getDropDownView(position, convertView, parent);
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                textView.setTextColor(ContextCompat.getColor(LigaDetalleActivity.this, R.color.text_primary));
                textView.setBackgroundColor(
                        ContextCompat.getColor(LigaDetalleActivity.this, R.color.background_secondary)
                );
            }
            return view;
        }
    }

    private static class ApiResult {
        final boolean ok;
        final int code;
        @NonNull
        final String body;

        private ApiResult(boolean ok, int code, @NonNull String body) {
            this.ok = ok;
            this.code = code;
            this.body = body;
        }

        static ApiResult ok(int code, @NonNull String body) {
            return new ApiResult(true, code, body);
        }

        static ApiResult error(int code, @NonNull String body) {
            return new ApiResult(false, code, body);
        }
    }
}
