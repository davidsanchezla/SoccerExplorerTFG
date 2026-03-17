package com.example.soccerexplorer;

import android.os.Bundle;
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

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Madrid");
    private static final Locale APP_LOCALE = new Locale("es", "ES");

    private static final String ESTADO_LIVE = "IN_PLAY";
    private static final String ESTADO_PAUSED = "PAUSED";
    private static final String ESTADO_SCHEDULED = "SCHEDULED";
    private static final String ESTADO_TIMED = "TIMED";

    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter SORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm", Locale.ROOT);
    private static final DateTimeFormatter MATCH_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", APP_LOCALE);
    private static final long API_QUERY_BUFFER_DAYS = 1L;

    private static final Map<String, LigaInfo> LIGAS = new LinkedHashMap<>();

    static {
        LIGAS.put("Serie A", new LigaInfo("SA"));
        LIGAS.put("Championship", new LigaInfo("ELC"));
        LIGAS.put("Bundesliga", new LigaInfo("BL1"));
        LIGAS.put("Champions League", new LigaInfo("CL"));
        LIGAS.put("Eurocopa", new LigaInfo("EC"));
        LIGAS.put("La Liga", new LigaInfo("PD"));
        LIGAS.put("Premier League", new LigaInfo("PL"));
        LIGAS.put("Brasileirao", new LigaInfo("BSA"));
        LIGAS.put("Mundial", new LigaInfo("WC"));
        LIGAS.put("Ligue 1", new LigaInfo("FL1"));
        LIGAS.put("Eredivisie", new LigaInfo("DED"));
        LIGAS.put("Liga Portuguesa", new LigaInfo("PPL"));
    }

    private TextInputEditText etMatchesDate;
    private ProgressBar pbMatches;
    private TextView tvMatchesStatus;
    private RecyclerView rvMatches;

    private PartidosAdapter partidosAdapter;
    private ExecutorService executorService;

    @NonNull
    private LocalDate selectedDate = LocalDate.now(APP_ZONE);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_partidos, container, false);

        etMatchesDate = view.findViewById(R.id.etMatchesDate);
        pbMatches = view.findViewById(R.id.pbMatches);
        tvMatchesStatus = view.findViewById(R.id.tvMatchesStatus);
        rvMatches = view.findViewById(R.id.rvMatches);

        partidosAdapter = new PartidosAdapter();
        rvMatches.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMatches.setAdapter(partidosAdapter);

        executorService = Executors.newSingleThreadExecutor();

        configurarSelectorFecha(view);
        actualizarTextoFecha();
        cargarPartidosPorFecha(selectedDate);

        return view;
    }

    private void configurarSelectorFecha(@NonNull View root) {
        View selectorContainer = root.findViewById(R.id.tilMatchesDate);
        View.OnClickListener listener = v -> abrirDatePicker();

        selectorContainer.setOnClickListener(listener);
        etMatchesDate.setOnClickListener(listener);
    }

    private void abrirDatePicker() {
        if (!isAdded()) {
            return;
        }

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.matches_date_picker_title))
                .setSelection(localDateToUtcMillis(selectedDate))
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) {
                return;
            }

            LocalDate pickedDate = utcMillisToLocalDate(selection);
            if (pickedDate.equals(selectedDate)) {
                return;
            }

            selectedDate = pickedDate;
            actualizarTextoFecha();
            cargarPartidosPorFecha(selectedDate);
        });

        picker.show(getParentFragmentManager(), "matches-date-picker");
    }

    private void actualizarTextoFecha() {
        if (!isAdded()) {
            return;
        }
        etMatchesDate.setText(formatearFechaCabecera(selectedDate));
    }

    @NonNull
    private String formatearFechaCabecera(@NonNull LocalDate date) {
        String base = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", APP_LOCALE));
        if (base.length() > 1) {
            base = base.substring(0, 1).toUpperCase(APP_LOCALE) + base.substring(1);
        }

        LocalDate today = LocalDate.now(APP_ZONE);
        if (date.equals(today)) {
            return getString(R.string.matches_date_today_format, base);
        }
        return base;
    }

    private void cargarPartidosPorFecha(@NonNull LocalDate requestDate) {
        mostrarCarga(true);
        ocultarEstado();
        partidosAdapter.actualizarItems(new ArrayList<>());

        final String apiDateFrom = API_DATE_FORMAT.format(requestDate.minusDays(API_QUERY_BUFFER_DAYS));
        final String apiDateTo = API_DATE_FORMAT.format(requestDate.plusDays(API_QUERY_BUFFER_DAYS));

        executorService.execute(() -> {
            Map<String, List<PartidosAdapter.PartidoItem>> grouped = new LinkedHashMap<>();
            String firstError = null;
            boolean anySuccess = false;

            for (Map.Entry<String, LigaInfo> entry : LIGAS.entrySet()) {
                String competitionName = entry.getKey();
                LigaInfo ligaInfo = entry.getValue();

                ApiResult result = ejecutarGet(
                        "/competitions/" + ligaInfo.apiCode + "/matches?dateFrom=" + apiDateFrom + "&dateTo=" + apiDateTo
                );

                if (!result.ok) {
                    if (firstError == null) {
                        firstError = resultadoApiToMessage(result);
                    }
                    continue;
                }

                anySuccess = true;
                try {
                    List<PartidosAdapter.PartidoItem> partidos = parsePartidos(result.body, competitionName, requestDate);
                    if (!partidos.isEmpty()) {
                        ordenarPartidos(partidos);
                        grouped.put(competitionName, partidos);
                    }
                } catch (Exception e) {
                    if (firstError == null) {
                        firstError = getString(R.string.matches_error_parse);
                    }
                }
            }

            if (!anySuccess && firstError != null) {
                publicarError(firstError);
                return;
            }

            List<PartidosAdapter.RowItem> rows = buildRows(grouped);
            publicarPartidos(rows, requestDate);
        });
    }

    private void ordenarPartidos(@NonNull List<PartidosAdapter.PartidoItem> partidos) {
        Collections.sort(partidos, (a, b) -> {
            if (a.live && !b.live) {
                return -1;
            }
            if (!a.live && b.live) {
                return 1;
            }
            return a.sortTime.compareToIgnoreCase(b.sortTime);
        });
    }

    @NonNull
    private List<PartidosAdapter.RowItem> buildRows(@NonNull Map<String, List<PartidosAdapter.PartidoItem>> grouped) {
        List<PartidosAdapter.RowItem> rows = new ArrayList<>();

        for (Map.Entry<String, List<PartidosAdapter.PartidoItem>> entry : grouped.entrySet()) {
            List<PartidosAdapter.PartidoItem> partidos = entry.getValue();
            if (partidos == null || partidos.isEmpty()) {
                continue;
            }

            rows.add(PartidosAdapter.RowItem.header(entry.getKey()));

            Map<String, PartidosAdapter.PartidoItem> unique = new LinkedHashMap<>();
            for (PartidosAdapter.PartidoItem partido : partidos) {
                unique.put(buildKey(partido), partido);
            }

            for (PartidosAdapter.PartidoItem partido : unique.values()) {
                rows.add(PartidosAdapter.RowItem.match(partido));
            }
        }

        return rows;
    }

    @NonNull
    private List<PartidosAdapter.PartidoItem> parsePartidos(@NonNull String body,
                                                            @NonNull String competitionName,
                                                            @NonNull LocalDate requestDate) throws Exception {
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

            int matchday = item.optInt("matchday", -1);
            String round = matchday > 0 ? getString(R.string.matches_round_item, matchday) : "";

            String statusCode = item.optString("status", "");
            String utcDate = item.optString("utcDate", "");

            boolean live = ESTADO_LIVE.equals(statusCode) || ESTADO_PAUSED.equals(statusCode);
            boolean scheduled = ESTADO_SCHEDULED.equals(statusCode) || ESTADO_TIMED.equals(statusCode);

            String scoreOrTime;
            String statusText;
            String sortTime;

            LocalDateTime localDateTime = parseUtcToMadrid(utcDate);
            if (localDateTime == null || !requestDate.equals(localDateTime.toLocalDate())) {
                continue;
            }

            sortTime = localDateTime.format(SORT_TIME_FORMAT);

            if (scheduled) {
                scoreOrTime = localDateTime.format(MATCH_TIME_FORMAT);
                statusText = getString(R.string.matches_status_upcoming);
            } else {
                Integer homeGoals = fullTimeObj != null && !fullTimeObj.isNull("home") ? fullTimeObj.optInt("home") : null;
                Integer awayGoals = fullTimeObj != null && !fullTimeObj.isNull("away") ? fullTimeObj.optInt("away") : null;

                String homeGoalsText = homeGoals == null ? "-" : String.valueOf(homeGoals);
                String awayGoalsText = awayGoals == null ? "-" : String.valueOf(awayGoals);
                scoreOrTime = homeGoalsText + " - " + awayGoalsText;

                if (live) {
                    statusText = getString(R.string.matches_status_live);
                } else {
                    statusText = traducirEstado(statusCode);
                }
            }

            partidos.add(new PartidosAdapter.PartidoItem(
                    round,
                    competitionName,
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
            return LocalDateTime.ofInstant(instant, APP_ZONE);
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private LocalDate utcMillisToLocalDate(long utcMillis) {
        return Instant.ofEpochMilli(utcMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
    }

    private long localDateToUtcMillis(@NonNull LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    @NonNull
    private String buildKey(@NonNull PartidosAdapter.PartidoItem item) {
        return item.competitionName + "_" + item.homeTeam + "_" + item.awayTeam + "_" + item.sortTime;
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

    private void publicarPartidos(@NonNull List<PartidosAdapter.RowItem> rows,
                                  @NonNull LocalDate requestDate) {
        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(() -> {
            if (!isAdded() || !selectedDate.equals(requestDate)) {
                return;
            }

            mostrarCarga(false);

            if (rows.isEmpty()) {
                String emptyDate = requestDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", APP_LOCALE));
                if (emptyDate.length() > 1) {
                    emptyDate = emptyDate.substring(0, 1).toUpperCase(APP_LOCALE) + emptyDate.substring(1);
                }
                mostrarEstado(getString(R.string.matches_empty_date, emptyDate));
                partidosAdapter.actualizarItems(new ArrayList<>());
                return;
            }

            ocultarEstado();
            partidosAdapter.actualizarItems(rows);
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
            partidosAdapter.actualizarItems(new ArrayList<>());
        });
    }

    @NonNull
    private String resultadoApiToMessage(@NonNull ApiResult result) {
        if (result.code == 401 && "missing-key".equalsIgnoreCase(result.body)) {
            return getString(R.string.matches_error_missing_api_key);
        }
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

        LigaInfo(@NonNull String apiCode) {
            this.apiCode = apiCode;
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
