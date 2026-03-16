package com.example.soccerexplorer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PartidosFragment extends Fragment {

    private static final String API_BASE = "https://api.football-data.org/v4";
    private static final int CURRENT_SEASON = 2025;

    private static final String ESTADO_LIVE = "IN_PLAY";
    private static final String ESTADO_PAUSED = "PAUSED";
    private static final String ESTADO_SCHEDULED = "SCHEDULED";
    private static final String ESTADO_TIMED = "TIMED";

    private static final Map<String, LigaInfo> LIGAS = new LinkedHashMap<>();

    static {
        LIGAS.put("LaLiga EA Sports", new LigaInfo("PD", 38, "PD"));
        LIGAS.put("Premier League", new LigaInfo("PL", 38, "PL"));
        LIGAS.put("Ligue 1", new LigaInfo("FL1", 34, "FL1"));
        LIGAS.put("Bundesliga", new LigaInfo("BL1", 34, "BL1"));
        LIGAS.put("Serie A", new LigaInfo("SA", 38, "SA"));
    }

    private MaterialAutoCompleteTextView actvLeague;
    private MaterialAutoCompleteTextView actvRound;
    private ProgressBar pbMatches;
    private TextView tvMatchesStatus;
    private RecyclerView rvMatches;

    private PartidosAdapter partidosAdapter;
    private ExecutorService executorService;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private final List<String> leagueNames = new ArrayList<>(LIGAS.keySet());
    private final List<String> currentRoundItems = new ArrayList<>();

    @Nullable
    private String selectedLeagueName;
    @Nullable
    private LigaInfo selectedLeagueInfo;
    private int selectedMatchday = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_partidos, container, false);

        actvLeague = view.findViewById(R.id.actvLeague);
        actvRound = view.findViewById(R.id.actvRound);
        pbMatches = view.findViewById(R.id.pbMatches);
        tvMatchesStatus = view.findViewById(R.id.tvMatchesStatus);
        rvMatches = view.findViewById(R.id.rvMatches);

        partidosAdapter = new PartidosAdapter();
        rvMatches.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMatches.setAdapter(partidosAdapter);

        executorService = Executors.newSingleThreadExecutor();
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        configurarCombos();
        seleccionarLigaInicialDesdeFavorito();

        return view;
    }

    private void configurarCombos() {
        ArrayAdapter<String> leagueAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                leagueNames
        );
        actvLeague.setAdapter(leagueAdapter);

        actvLeague.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= leagueNames.size()) {
                return;
            }
            seleccionarLiga(leagueNames.get(position));
        });

        actvRound.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= currentRoundItems.size()) {
                return;
            }
            String item = currentRoundItems.get(position);
            int matchday = parseMatchday(item);
            if (matchday <= 0 || selectedLeagueInfo == null) {
                return;
            }
            selectedMatchday = matchday;
            cargarPartidos(selectedLeagueInfo);
        });
    }

    private void seleccionarLigaInicialDesdeFavorito() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            seleccionarLigaPorDefecto();
            return;
        }

        firestore.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) {
                        return;
                    }
                    String ligaId = documentSnapshot.getString("equipoFavoritoLigaId");
                    String leagueName = encontrarLigaPorCodigo(ligaId);
                    if (leagueName == null) {
                        seleccionarLigaPorDefecto();
                        return;
                    }
                    actvLeague.setText(leagueName, false);
                    seleccionarLiga(leagueName);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    seleccionarLigaPorDefecto();
                });
    }

    private void seleccionarLigaPorDefecto() {
        if (leagueNames.isEmpty()) {
            return;
        }
        String initial = leagueNames.get(0);
        actvLeague.setText(initial, false);
        seleccionarLiga(initial);
    }

    private void seleccionarLiga(@NonNull String leagueName) {
        selectedLeagueName = leagueName;
        selectedLeagueInfo = LIGAS.get(leagueName);
        selectedMatchday = 1;

        partidosAdapter.actualizarPartidos(new ArrayList<>());
        actvRound.setText("", false);
        currentRoundItems.clear();

        if (selectedLeagueInfo == null) {
            mostrarEstado(getString(R.string.matches_error_load_league));
            return;
        }

        cargarJornadasYSeleccionarActual(selectedLeagueInfo);
    }

    private void cargarJornadasYSeleccionarActual(@NonNull LigaInfo ligaInfo) {
        mostrarCarga(true);
        ocultarEstado();

        executorService.execute(() -> {
            ApiResult competitionResult = ejecutarGet("/competitions/" + ligaInfo.apiCode);
            if (!competitionResult.ok) {
                publicarError(resultadoApiToMessage(competitionResult));
                return;
            }

            try {
                JSONObject root = new JSONObject(competitionResult.body);
                JSONObject currentSeason = root.optJSONObject("currentSeason");
                int currentMatchday = currentSeason != null ? currentSeason.optInt("currentMatchday", 1) : 1;
                if (currentMatchday <= 0) {
                    currentMatchday = 1;
                }

                List<String> rounds = new ArrayList<>();
                for (int i = 1; i <= ligaInfo.maxMatchday; i++) {
                    rounds.add(getString(R.string.matches_round_item, i));
                }

                if (currentMatchday > ligaInfo.maxMatchday) {
                    currentMatchday = ligaInfo.maxMatchday;
                }

                int defaultMatchday = currentMatchday;
                publicarJornadasYPartidos(ligaInfo, rounds, defaultMatchday);
            } catch (Exception e) {
                publicarError(getString(R.string.matches_error_parse));
            }
        });
    }

    private void publicarJornadasYPartidos(@NonNull LigaInfo ligaInfo,
                                           @NonNull List<String> rounds,
                                           int defaultMatchday) {
        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(() -> {
            if (!isAdded() || selectedLeagueInfo == null || !selectedLeagueInfo.apiCode.equals(ligaInfo.apiCode)) {
                return;
            }

            currentRoundItems.clear();
            currentRoundItems.addAll(rounds);

            ArrayAdapter<String> roundAdapter = new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    currentRoundItems
            );
            actvRound.setAdapter(roundAdapter);

            selectedMatchday = defaultMatchday;
            String defaultRoundItem = getString(R.string.matches_round_item, selectedMatchday);
            actvRound.setText(defaultRoundItem, false);

            cargarPartidos(ligaInfo);
        });
    }

    private void cargarPartidos(@NonNull LigaInfo ligaInfo) {
        mostrarCarga(true);
        ocultarEstado();

        executorService.execute(() -> {
            ApiResult matchdayResult = ejecutarGet(
                    "/competitions/" + ligaInfo.apiCode + "/matches?season=" + CURRENT_SEASON + "&matchday=" + selectedMatchday
            );

            if (!matchdayResult.ok) {
                publicarError(resultadoApiToMessage(matchdayResult));
                return;
            }

            ApiResult liveResult = ejecutarGet(
                    "/competitions/" + ligaInfo.apiCode + "/matches?season=" + CURRENT_SEASON + "&status=IN_PLAY,PAUSED"
            );

            try {
                List<PartidosAdapter.PartidoItem> matchdayMatches = parsePartidos(matchdayResult.body, selectedMatchday);
                List<PartidosAdapter.PartidoItem> liveMatches = liveResult.ok
                        ? parsePartidos(liveResult.body, selectedMatchday)
                        : new ArrayList<>();

                List<PartidosAdapter.PartidoItem> merged = new ArrayList<>();
                Map<String, PartidosAdapter.PartidoItem> unique = new LinkedHashMap<>();

                for (PartidosAdapter.PartidoItem item : liveMatches) {
                    unique.put(buildKey(item), item);
                }
                for (PartidosAdapter.PartidoItem item : matchdayMatches) {
                    String key = buildKey(item);
                    if (!unique.containsKey(key)) {
                        unique.put(key, item);
                    }
                }

                merged.addAll(unique.values());
                Collections.sort(merged, (a, b) -> {
                    if (a.live && !b.live) {
                        return -1;
                    }
                    if (!a.live && b.live) {
                        return 1;
                    }
                    return a.sortTime.compareToIgnoreCase(b.sortTime);
                });

                publicarPartidos(merged);
            } catch (Exception e) {
                publicarError(getString(R.string.matches_error_parse));
            }
        });
    }

    @NonNull
    private List<PartidosAdapter.PartidoItem> parsePartidos(@NonNull String body, int matchdayFallback) throws Exception {
        List<PartidosAdapter.PartidoItem> partidos = new ArrayList<>();

        JSONObject root = new JSONObject(body);
        JSONArray matches = root.optJSONArray("matches");
        if (matches == null) {
            return partidos;
        }

        for (int i = 0; i < matches.length(); i++) {
            JSONObject item = matches.optJSONObject(i);
            if (item == null) {
                continue;
            }

            JSONObject homeTeamObj = item.optJSONObject("homeTeam");
            JSONObject awayTeamObj = item.optJSONObject("awayTeam");
            JSONObject scoreObj = item.optJSONObject("score");
            JSONObject fullTimeObj = scoreObj != null ? scoreObj.optJSONObject("fullTime") : null;

            String homeTeam = homeTeamObj != null ? homeTeamObj.optString("name", "-") : "-";
            String awayTeam = awayTeamObj != null ? awayTeamObj.optString("name", "-") : "-";
            String homeLogo = homeTeamObj != null ? homeTeamObj.optString("crest", null) : null;
            String awayLogo = awayTeamObj != null ? awayTeamObj.optString("crest", null) : null;

            int matchday = item.optInt("matchday", matchdayFallback);
            String round = getString(R.string.matches_round_item, matchday);

            String statusCode = item.optString("status", "");
            String utcDate = item.optString("utcDate", "");

            boolean live = ESTADO_LIVE.equals(statusCode) || ESTADO_PAUSED.equals(statusCode);
            boolean scheduled = ESTADO_SCHEDULED.equals(statusCode) || ESTADO_TIMED.equals(statusCode);

            String scoreOrTime;
            String statusText;
            String sortTime;

            LocalDateTime localDateTime = parseUtcToMadrid(utcDate);
            sortTime = localDateTime != null
                    ? localDateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm", Locale.getDefault()))
                    : "999999999999";

            if (scheduled) {
                if (localDateTime != null) {
                    scoreOrTime = localDateTime.format(DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.getDefault()));
                } else {
                    scoreOrTime = "--:--";
                }
                statusText = getString(R.string.matches_status_upcoming);
            } else {
                Integer homeGoals = fullTimeObj != null && !fullTimeObj.isNull("home") ? fullTimeObj.optInt("home") : null;
                Integer awayGoals = fullTimeObj != null && !fullTimeObj.isNull("away") ? fullTimeObj.optInt("away") : null;

                String h = homeGoals == null ? "-" : String.valueOf(homeGoals);
                String a = awayGoals == null ? "-" : String.valueOf(awayGoals);
                scoreOrTime = h + " - " + a;

                if (live) {
                    statusText = getString(R.string.matches_status_live);
                } else {
                    statusText = traducirEstado(statusCode);
                }
            }

            partidos.add(new PartidosAdapter.PartidoItem(
                    round,
                    homeTeam,
                    awayTeam,
                    scoreOrTime,
                    statusText,
                    homeLogo,
                    awayLogo,
                    live,
                    sortTime
            ));
        }

        return partidos;
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

    @Nullable
    private LocalDateTime parseUtcToMadrid(@Nullable String utcDate) {
        if (utcDate == null || utcDate.trim().isEmpty()) {
            return null;
        }
        try {
            Instant instant = Instant.parse(utcDate);
            return LocalDateTime.ofInstant(instant, ZoneId.of("Europe/Madrid"));
        } catch (Exception e) {
            return null;
        }
    }

    private int parseMatchday(@NonNull String roundText) {
        String onlyDigits = roundText.replaceAll("[^0-9]", "");
        if (onlyDigits.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(onlyDigits);
        } catch (Exception e) {
            return -1;
        }
    }

    @Nullable
    private String encontrarLigaPorCodigo(@Nullable String ligaIdFirestore) {
        if (ligaIdFirestore == null || ligaIdFirestore.trim().isEmpty()) {
            return null;
        }

        for (Map.Entry<String, LigaInfo> entry : LIGAS.entrySet()) {
            if (entry.getValue().firestoreCode.equalsIgnoreCase(ligaIdFirestore.trim())) {
                return entry.getKey();
            }
        }
        return null;
    }

    @NonNull
    private String buildKey(@NonNull PartidosAdapter.PartidoItem item) {
        return item.round + "_" + item.homeTeam + "_" + item.awayTeam;
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

    private void publicarPartidos(@NonNull List<PartidosAdapter.PartidoItem> partidos) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) {
                return;
            }
            mostrarCarga(false);
            if (partidos.isEmpty()) {
                mostrarEstado(getString(R.string.matches_empty));
                partidosAdapter.actualizarPartidos(new ArrayList<>());
                return;
            }
            ocultarEstado();
            partidosAdapter.actualizarPartidos(partidos);
        });
    }

    private void publicarError(@NonNull String message) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) {
                return;
            }
            mostrarCarga(false);
            mostrarEstado(message);
            partidosAdapter.actualizarPartidos(new ArrayList<>());
        });
    }

    @NonNull
    private String resultadoApiToMessage(@NonNull ApiResult result) {
        if (result.code == 401 || result.code == 403) {
            return getString(R.string.matches_error_invalid_api_key);
        }
        if (result.code == 429) {
            return getString(R.string.matches_error_rate_limit);
        }
        if (result.code == 500) {
            return getString(R.string.matches_error_network);
        }
        return getString(R.string.matches_error_api, result.code);
    }

    private void mostrarCarga(boolean loading) {
        pbMatches.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            tvMatchesStatus.setVisibility(View.GONE);
        }
    }

    private void mostrarEstado(@NonNull String message) {
        tvMatchesStatus.setText(message);
        tvMatchesStatus.setVisibility(View.VISIBLE);
    }

    private void ocultarEstado() {
        tvMatchesStatus.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        super.onDestroyView();
    }

    private static class LigaInfo {
        final String apiCode;
        final int maxMatchday;
        final String firestoreCode;

        LigaInfo(@NonNull String apiCode, int maxMatchday, @NonNull String firestoreCode) {
            this.apiCode = apiCode;
            this.maxMatchday = maxMatchday;
            this.firestoreCode = firestoreCode;
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
