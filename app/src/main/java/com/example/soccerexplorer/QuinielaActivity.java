package com.example.soccerexplorer;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

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
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuinielaActivity extends AppCompatActivity {

    private static final String API_BASE = "https://api.football-data.org/v4";
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Madrid");
    private static final Locale APP_LOCALE = new Locale("es", "ES");

    private static final int XP_PER_HIT = 10;
    private static final int XP_FULL_WEEK_BONUS = 50;

    private static final Map<String, String> LIGA_ID_TO_API_CODE = new HashMap<>();

    static {
        LIGA_ID_TO_API_CODE.put("SA", "SA");
        LIGA_ID_TO_API_CODE.put("ELC", "ELC");
        LIGA_ID_TO_API_CODE.put("BL1", "BL1");
        LIGA_ID_TO_API_CODE.put("CL", "CL");
        LIGA_ID_TO_API_CODE.put("EC", "EC");
        LIGA_ID_TO_API_CODE.put("PD", "PD");
        LIGA_ID_TO_API_CODE.put("PL", "PL");
        LIGA_ID_TO_API_CODE.put("BSA", "BSA");
        LIGA_ID_TO_API_CODE.put("WC", "WC");
        LIGA_ID_TO_API_CODE.put("FL1", "FL1");
        LIGA_ID_TO_API_CODE.put("DED", "DED");
        LIGA_ID_TO_API_CODE.put("PPL", "PPL");
    }

    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    private TextView tvQuinielaHeader;
    private TextView tvQuinielaRank;
    private TextView tvQuinielaStatus;
    private View pbQuiniela;
    private RecyclerView rvQuiniela;
    private MaterialButton btnGuardarQuiniela;

    private QuinielaAdapter quinielaAdapter;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final List<MatchItem> matches = new ArrayList<>();
    private final Map<String, String> pronosticos = new HashMap<>();

    private String userLigaId = "";
    private String semanaId = "";
    private int jornadaActual = -1;
    private String quinielaSemanaIdGuardada = "";
    private String quinielaLigaIdGuardada = "";
    private long experienciaTotal = 0L;
    private long rango = 1L;
    private boolean jornadaEmpezada = false;
    private boolean jornadaFinalizada = false;
    private boolean quinielaCerrada = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiniela);

        firestore = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        MaterialToolbar toolbar = findViewById(R.id.toolbarQuiniela);
        tvQuinielaHeader = findViewById(R.id.tvQuinielaHeader);
        tvQuinielaRank = findViewById(R.id.tvQuinielaRank);
        tvQuinielaStatus = findViewById(R.id.tvQuinielaStatus);
        pbQuiniela = findViewById(R.id.pbQuiniela);
        rvQuiniela = findViewById(R.id.rvQuiniela);
        btnGuardarQuiniela = findViewById(R.id.btnGuardarQuiniela);

        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        quinielaAdapter = new QuinielaAdapter((matchId, pick) -> {
            if (pick == null) {
                pronosticos.remove(matchId);
            } else {
                pronosticos.put(matchId, pick);
            }
            actualizarEstadoBotonGuardar();
        });
        rvQuiniela.setLayoutManager(new LinearLayoutManager(this));
        rvQuiniela.setAdapter(quinielaAdapter);

        btnGuardarQuiniela.setOnClickListener(v -> guardarPronosticos());

        if (currentUser == null) {
            mostrarEstado(getString(R.string.quiniela_error_no_user), true);
            btnGuardarQuiniela.setEnabled(false);
            return;
        }

        semanaId = obtenerSemanaIdActual();
        cargarPerfilYQuiniela();
    }

    private void cargarPerfilYQuiniela() {
        mostrarCarga(true);
        DocumentReference userRef = firestore.collection("users").document(currentUser.getUid());
        userRef.get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        mostrarCarga(false);
                        mostrarEstado(getString(R.string.quiniela_error_profile), true);
                        return;
                    }

                    userLigaId = valorString(userDoc.getString("equipoFavoritoLigaId"));
                    experienciaTotal = valorLong(userDoc.getLong("experienciaTotal"), 0L);
                    rango = valorLong(userDoc.getLong("rango"), 1L);

                    if (userLigaId.isEmpty()) {
                        mostrarCarga(false);
                        mostrarEstado(getString(R.string.quiniela_error_no_league), true);
                        return;
                    }

                    if (esTorneoNoSoportado(userLigaId)) {
                        jornadaActual = -1;
                        matches.clear();
                        pronosticos.clear();
                        quinielaCerrada = true;
                        actualizarCabecera();
                        mostrarCarga(false);
                        btnGuardarQuiniela.setEnabled(false);
                        quinielaAdapter.actualizarItems(new ArrayList<>(), new HashMap<>(), false, false);
                        mostrarEstado(getString(R.string.quiniela_error_tournament_not_supported), true);
                        return;
                    }

                    actualizarCabecera();
                    cargarDocQuinielaActual();
                })
                .addOnFailureListener(e -> {
                    mostrarCarga(false);
                    mostrarEstado(getString(R.string.quiniela_error_profile), true);
                });
    }

    private void cargarDocQuinielaActual() {
        DocumentReference quinielaRef = firestore.collection("users")
                .document(currentUser.getUid())
                .collection("quinielaActual")
                .document("actual");

        quinielaRef.get()
                .addOnSuccessListener(doc -> {
                    Map<String, Object> init = construirDocumentoBaseQuiniela(semanaId, userLigaId);

                    if (!doc.exists()) {
                        quinielaRef.set(init, SetOptions.merge())
                                .addOnSuccessListener(unused -> {
                                    quinielaSemanaIdGuardada = semanaId;
                                    quinielaLigaIdGuardada = userLigaId;
                                    pedirPartidosYResolverEstado();
                                })
                                .addOnFailureListener(e -> {
                                    mostrarCarga(false);
                                    mostrarEstado(getString(R.string.quiniela_error_load), true);
                                });
                        return;
                    }

                    String semanaDoc = valorString(doc.getString("semanaId"));
                    String ligaDoc = valorString(doc.getString("ligaId"));
                    quinielaSemanaIdGuardada = semanaDoc;
                    quinielaLigaIdGuardada = ligaDoc;
                    boolean semanaCompatible = semanaDoc.equals(semanaId)
                            || semanaDoc.startsWith(semanaId + "-J");
                    if (!semanaCompatible || !userLigaId.equals(ligaDoc)) {
                        quinielaRef.set(init, SetOptions.merge())
                                .addOnSuccessListener(unused -> {
                                    pronosticos.clear();
                                    quinielaCerrada = false;
                                    quinielaSemanaIdGuardada = semanaId;
                                    quinielaLigaIdGuardada = userLigaId;
                                    pedirPartidosYResolverEstado();
                                })
                                .addOnFailureListener(e -> {
                                    mostrarCarga(false);
                                    mostrarEstado(getString(R.string.quiniela_error_load), true);
                                });
                        return;
                    }

                    quinielaCerrada = Boolean.TRUE.equals(doc.getBoolean("cerrada"));
                    if (quinielaCerrada) {
                        btnGuardarQuiniela.setEnabled(false);
                    }
                    pronosticos.clear();
                    Map<String, Object> pronosticosMap = leerMapPronosticos(doc);
                    for (Map.Entry<String, Object> entry : pronosticosMap.entrySet()) {
                        Object value = entry.getValue();
                        if (value instanceof String) {
                            String pick = (String) value;
                            if ("1".equals(pick) || "X".equals(pick) || "2".equals(pick)) {
                                pronosticos.put(entry.getKey(), pick);
                            }
                        }
                    }

                    pedirPartidosYResolverEstado();
                })
                .addOnFailureListener(e -> {
                    mostrarCarga(false);
                    mostrarEstado(getString(R.string.quiniela_error_load), true);
                });
    }

    @NonNull
    private Map<String, Object> construirDocumentoBaseQuiniela(@NonNull String semanaIdDoc,
                                                                @NonNull String ligaIdDoc) {
        Map<String, Object> data = new HashMap<>();
        data.put("semanaId", semanaIdDoc);
        data.put("ligaId", ligaIdDoc);
        data.put("pronosticos", new HashMap<String, Object>());
        data.put("puntosSemana", 0L);
        data.put("cerrada", false);
        data.put("updatedAt", FieldValue.serverTimestamp());
        return data;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    private Map<String, Object> leerMapPronosticos(@NonNull DocumentSnapshot doc) {
        Object raw = doc.get("pronosticos");
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        return new HashMap<>();
    }

    private void pedirPartidosYResolverEstado() {
        String competitionCode = LIGA_ID_TO_API_CODE.get(userLigaId);
        if (competitionCode == null || competitionCode.trim().isEmpty()) {
            mostrarCarga(false);
            mostrarEstado(getString(R.string.quiniela_error_invalid_league), true);
            return;
        }

        executorService.execute(() -> {
            try {
                int matchday = obtenerJornadaObjetivo(competitionCode);
                if (matchday <= 0) {
                    runOnUiThread(() -> {
                        mostrarCarga(false);
                        mostrarEstado(getString(R.string.quiniela_error_no_matches), true);
                    });
                    return;
                }

                ApiResult result = ejecutarGet(
                        "/competitions/" + competitionCode + "/matches?matchday=" + matchday
                );
                if (!result.ok) {
                    runOnUiThread(() -> {
                        mostrarCarga(false);
                        mostrarEstado(getString(R.string.quiniela_error_api, result.code), true);
                    });
                    return;
                }

                List<MatchItem> parsed = parseMatchesSemana(result.body, competitionCode, matchday);
                if (parsed.isEmpty()) {
                    runOnUiThread(() -> {
                        jornadaActual = -1;
                        matches.clear();
                        mostrarCarga(false);
                        mostrarEstado(getString(R.string.quiniela_error_no_matches), true);
                        btnGuardarQuiniela.setEnabled(false);
                        quinielaAdapter.actualizarItems(new ArrayList<>(), new HashMap<>(), false, false);
                    });
                    return;
                }
                Collections.sort(parsed, (a, b) -> Long.compare(a.kickoffEpochMs, b.kickoffEpochMs));
                runOnUiThread(() -> {
                    jornadaActual = matchday;
                    String semanaConJornada = buildSemanaIdConJornada(jornadaActual);
                    if (!semanaConJornada.equals(quinielaSemanaIdGuardada)
                            || !userLigaId.equals(quinielaLigaIdGuardada)) {
                        resetearQuinielaParaJornada(semanaConJornada);
                    }
                    matches.clear();
                    matches.addAll(parsed);
                    resolverEstadoJornadaYUI();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    mostrarCarga(false);
                    mostrarEstado(getString(R.string.quiniela_error_network), true);
                });
            }
        });
    }

    private void resolverEstadoJornadaYUI() {
        if (matches.isEmpty()) {
            mostrarCarga(false);
            mostrarEstado(getString(R.string.quiniela_error_no_matches), true);
            btnGuardarQuiniela.setEnabled(false);
            quinielaAdapter.actualizarItems(new ArrayList<>(), new HashMap<>(), false, false);
            return;
        }

        jornadaEmpezada = false;
        jornadaFinalizada = true;

        long nowMs = System.currentTimeMillis();
        long primerKickoffMs = Long.MAX_VALUE;

        for (MatchItem item : matches) {
            if (item.kickoffEpochMs > 0L) {
                primerKickoffMs = Math.min(primerKickoffMs, item.kickoffEpochMs);
            }
            if (item.started) {
                jornadaEmpezada = true;
            }
            if (!item.finished) {
                jornadaFinalizada = false;
            }
        }

        if (!jornadaEmpezada && primerKickoffMs != Long.MAX_VALUE && nowMs >= primerKickoffMs) {
            jornadaEmpezada = true;
        }

        List<QuinielaAdapter.MatchRow> rows = new ArrayList<>();
        for (MatchItem item : matches) {
            rows.add(new QuinielaAdapter.MatchRow(
                    item.matchId,
                    item.homeTeam,
                    item.awayTeam,
                    item.meta,
                    item.homeLogo,
                    item.awayLogo,
                    item.resultadoFinal,
                    item.resultadoMarcador
            ));
        }

        boolean editable = !jornadaEmpezada && !quinielaCerrada;
        boolean mostrarResultados = jornadaFinalizada;

        quinielaAdapter.actualizarItems(rows, new HashMap<>(pronosticos), editable, mostrarResultados);
        actualizarEstadoBotonGuardar();
        actualizarCabecera();
        mostrarCarga(false);

        if (jornadaFinalizada) {
            calcularYPersistirPuntosSiCorresponde();
            return;
        }

        if (jornadaEmpezada) {
            mostrarEstado(getString(R.string.quiniela_status_started_locked), true);
        } else {
            ocultarEstado();
        }
    }

    private void guardarPronosticos() {
        if (currentUser == null) {
            mostrarEstado(getString(R.string.quiniela_error_no_user), true);
            return;
        }

        if (esTorneoNoSoportado(userLigaId)) {
            btnGuardarQuiniela.setEnabled(false);
            mostrarEstado(getString(R.string.quiniela_error_tournament_not_supported), true);
            return;
        }

        if (jornadaEmpezada || quinielaCerrada) {
            mostrarEstado(getString(R.string.quiniela_status_started_locked), true);
            return;
        }

        DocumentReference quinielaRef = firestore.collection("users")
                .document(currentUser.getUid())
                .collection("quinielaActual")
                .document("actual");

        String semanaDoc = buildSemanaIdConJornada();

        Map<String, Object> update = new HashMap<>();
        update.put("semanaId", semanaDoc);
        update.put("ligaId", userLigaId);
        update.put("pronosticos", new HashMap<>(pronosticos));
        update.put("updatedAt", FieldValue.serverTimestamp());

        btnGuardarQuiniela.setEnabled(false);
        quinielaRef.set(update, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    quinielaSemanaIdGuardada = semanaDoc;
                    quinielaLigaIdGuardada = userLigaId;
                    Toast.makeText(this, R.string.quiniela_saved, Toast.LENGTH_SHORT).show();
                    actualizarEstadoBotonGuardar();
                })
                .addOnFailureListener(e -> {
                    actualizarEstadoBotonGuardar();
                    mostrarEstado(getString(R.string.quiniela_error_save), true);
                });
    }

    private void calcularYPersistirPuntosSiCorresponde() {
        if (!jornadaFinalizada || currentUser == null) {
            return;
        }

        if (jornadaActual <= 0) {
            return;
        }

        String semanaDoc = buildSemanaIdConJornada();
        if (quinielaSemanaIdGuardada.isEmpty() || !semanaDoc.equals(quinielaSemanaIdGuardada)
                || !userLigaId.equals(quinielaLigaIdGuardada)) {
            mostrarEstado(getString(R.string.quiniela_status_no_picks_for_jornada), true);
            return;
        }

        final int totalPartidos = matches.size();
        int aciertos = 0;

        for (MatchItem item : matches) {
            String pick = pronosticos.get(item.matchId);
            if (pick != null && pick.equals(item.resultadoFinal)) {
                aciertos++;
            }
        }

        final int aciertosFinales = aciertos;
        final int xpGanada = (aciertosFinales * XP_PER_HIT)
                + (totalPartidos > 0 && aciertosFinales == totalPartidos ? XP_FULL_WEEK_BONUS : 0);

        final DocumentReference userRef = firestore.collection("users").document(currentUser.getUid());
        final DocumentReference quinielaRef = userRef.collection("quinielaActual").document("actual");

        firestore.runTransaction(transaction -> {
            DocumentSnapshot userDoc = transaction.get(userRef);
            DocumentSnapshot quinielaDoc = transaction.get(quinielaRef);

            String ultimaSemana = valorString(userDoc.getString("ultimaSemanaRecompensada"));
            long xpActual = valorLong(userDoc.getLong("experienciaTotal"), 0L);
            long puntosSemanaActual = valorLong(quinielaDoc.getLong("puntosSemana"), 0L);
            boolean cerradaActual = Boolean.TRUE.equals(quinielaDoc.getBoolean("cerrada"));
            long rangoActualDb = valorLong(userDoc.getLong("rango"), calcularRangoDesdeXp(xpActual));
            boolean xpAplicada = false;
            long xpFinal = xpActual;
            long rangoFinal = rangoActualDb;

            Map<String, Object> quinielaUpdate = new HashMap<>();
            quinielaUpdate.put("puntosSemana", (long) aciertosFinales);
            quinielaUpdate.put("cerrada", true);
            quinielaUpdate.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(quinielaRef, quinielaUpdate, SetOptions.merge());

            if (!semanaDoc.equals(ultimaSemana) && !cerradaActual) {
                long nuevoXp = xpActual + xpGanada;
                long nuevoRango = calcularRangoDesdeXp(nuevoXp);
                xpFinal = nuevoXp;
                rangoFinal = nuevoRango;
                xpAplicada = true;

                Map<String, Object> userUpdate = new HashMap<>();
                userUpdate.put("experienciaTotal", nuevoXp);
                userUpdate.put("rango", nuevoRango);
                userUpdate.put("ultimaSemanaRecompensada", semanaDoc);
                userUpdate.put("updatedAt", FieldValue.serverTimestamp());
                transaction.set(userRef, userUpdate, SetOptions.merge());
            } else if (puntosSemanaActual != aciertosFinales) {
                // Solo actualiza aciertos si recalculo por cambios de resultado sin dar XP otra vez
            }

            return new TxResultado(xpAplicada, xpFinal, rangoFinal);
        }).addOnSuccessListener(resultado -> {
            quinielaCerrada = true;
            experienciaTotal = resultado.xpTotal;
            rango = resultado.rango;
            quinielaSemanaIdGuardada = semanaDoc;
            quinielaLigaIdGuardada = userLigaId;
            actualizarCabecera();
            int xpMostrada = resultado.xpAplicada ? xpGanada : 0;
            mostrarEstado(getString(R.string.quiniela_status_finished_points, aciertosFinales, xpMostrada), true);
            actualizarEstadoBotonGuardar();
            quinielaAdapter.actualizarItems(
                    construirRowsActuales(),
                    new HashMap<>(pronosticos),
                    false,
                    true
            );
        }).addOnFailureListener(e -> {
            mostrarEstado(getString(R.string.quiniela_error_calculate), true);
        });
    }

    @NonNull
    private List<QuinielaAdapter.MatchRow> construirRowsActuales() {
        List<QuinielaAdapter.MatchRow> rows = new ArrayList<>();
        for (MatchItem item : matches) {
            rows.add(new QuinielaAdapter.MatchRow(
                    item.matchId,
                    item.homeTeam,
                    item.awayTeam,
                    item.meta,
                    item.homeLogo,
                    item.awayLogo,
                    item.resultadoFinal,
                    item.resultadoMarcador
            ));
        }
        return rows;
    }

    private void actualizarCabecera() {
        String rankName = nombreRango(rango);
        String jornadaText = jornadaActual > 0
                ? getString(R.string.quiniela_jornada_format, jornadaActual)
                : getString(R.string.quiniela_jornada_unknown);
        tvQuinielaHeader.setText(getString(R.string.quiniela_header_format, userLigaId, jornadaText));
        tvQuinielaRank.setText(getString(R.string.quiniela_rank_format, rankName, rango, experienciaTotal));
    }

    @NonNull
    private String buildSemanaIdConJornada() {
        return buildSemanaIdConJornada(jornadaActual);
    }

    @NonNull
    private String buildSemanaIdConJornada(int jornada) {
        if (jornada > 0) {
            return semanaId + "-J" + jornada;
        }
        return semanaId;
    }

    private void resetearQuinielaParaJornada(@NonNull String semanaConJornada) {
        pronosticos.clear();
        quinielaCerrada = false;
        quinielaSemanaIdGuardada = semanaConJornada;
        quinielaLigaIdGuardada = userLigaId;

        if (currentUser == null) {
            return;
        }

        DocumentReference quinielaRef = firestore.collection("users")
                .document(currentUser.getUid())
                .collection("quinielaActual")
                .document("actual");

        Map<String, Object> reset = construirDocumentoBaseQuiniela(semanaConJornada, userLigaId);
        quinielaRef.set(reset, SetOptions.merge())
                .addOnFailureListener(e -> mostrarEstado(getString(R.string.quiniela_error_save), true));
    }

    private void actualizarEstadoBotonGuardar() {
        boolean editable = !jornadaEmpezada && !quinielaCerrada;
        btnGuardarQuiniela.setEnabled(editable && pronosticos.size() == matches.size() && !matches.isEmpty());
    }

    @NonNull
    private String nombreRango(long rango) {
        if (rango <= 1L) {
            return getString(R.string.rank_canterano);
        }
        if (rango == 2L) {
            return getString(R.string.rank_amateur);
        }
        if (rango == 3L) {
            return getString(R.string.rank_profesional);
        }
        if (rango == 4L) {
            return getString(R.string.rank_estrella);
        }
        if (rango == 5L) {
            return getString(R.string.rank_elite);
        }
        if (rango == 6L) {
            return getString(R.string.rank_maestro);
        }
        return getString(R.string.rank_leyenda);
    }

    private long calcularRangoDesdeXp(long xp) {
        if (xp >= 3000L) {
            return 7L;
        }
        if (xp >= 1500L) {
            return 6L;
        }
        if (xp >= 1000L) {
            return 5L;
        }
        if (xp >= 600L) {
            return 4L;
        }
        if (xp >= 300L) {
            return 3L;
        }
        if (xp >= 100L) {
            return 2L;
        }
        return 1L;
    }

    private boolean esTorneoNoSoportado(@NonNull String ligaId) {
        String liga = ligaId.trim().toUpperCase(Locale.ROOT);
        return "CL".equals(liga)
                || "EC".equals(liga)
                || "WC".equals(liga);
    }

    @NonNull
    private String obtenerSemanaIdActual() {
        LocalDate hoy = LocalDate.now(APP_ZONE);
        WeekFields wf = WeekFields.ISO;
        int semana = hoy.get(wf.weekOfWeekBasedYear());
        int anioSemana = hoy.get(wf.weekBasedYear());
        return String.format(Locale.ROOT, "%04d-W%02d", anioSemana, semana);
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

        int maxStarted = extraerMaxJornadaDesdeRespuesta(started.body);
        if (maxStarted < 0) {
            return candidataBase;
        }

        long nowMs = System.currentTimeMillis();
        int nextAfterStarted = seleccionarJornadaConMasPartidos(scheduled.body, nowMs);
        if (nextAfterStarted > 0) {
            return nextAfterStarted;
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

            Map<Integer, MatchdayStats> statsByMatchday = new HashMap<>();
            for (int i = 0; i < matchesArray.length(); i++) {
                JSONObject obj = matchesArray.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                int matchday = obj.optInt("matchday", -1);
                if (matchday <= 0) {
                    continue;
                }

                String utcDate = obj.optString("utcDate", "");
                long kickoff = parseUtcToEpochMs(utcDate);
                if (kickoff <= minKickoffEpochMsExclusive) {
                    continue;
                }

                MatchdayStats stats = statsByMatchday.get(matchday);
                if (stats == null) {
                    stats = new MatchdayStats();
                    statsByMatchday.put(matchday, stats);
                }
                stats.count++;
                if (kickoff > 0L && (stats.minKickoffEpochMs <= 0L || kickoff < stats.minKickoffEpochMs)) {
                    stats.minKickoffEpochMs = kickoff;
                }
            }

            int bestMatchday = -1;
            MatchdayStats bestStats = null;
            for (Map.Entry<Integer, MatchdayStats> entry : statsByMatchday.entrySet()) {
                int matchday = entry.getKey();
                MatchdayStats stats = entry.getValue();

                if (bestStats == null) {
                    bestMatchday = matchday;
                    bestStats = stats;
                    continue;
                }

                if (stats.count > bestStats.count) {
                    bestMatchday = matchday;
                    bestStats = stats;
                    continue;
                }

                if (stats.count == bestStats.count) {
                    long currentMin = stats.minKickoffEpochMs;
                    long bestMin = bestStats.minKickoffEpochMs;
                    if (currentMin > 0L && (bestMin <= 0L || currentMin < bestMin)) {
                        bestMatchday = matchday;
                        bestStats = stats;
                    }
                }
            }

            return bestMatchday;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private int extraerJornadaDesdeRespuesta(@NonNull String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONArray matchesArray = root.optJSONArray("matches");
            if (matchesArray == null || matchesArray.length() == 0) {
                return -1;
            }
            Integer minMatchday = null;
            for (int i = 0; i < matchesArray.length(); i++) {
                JSONObject obj = matchesArray.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                int matchday = obj.optInt("matchday", -1);
                if (matchday <= 0) {
                    continue;
                }
                if (minMatchday == null || matchday < minMatchday) {
                    minMatchday = matchday;
                }
            }
            return minMatchday == null ? -1 : minMatchday;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private int extraerMaxJornadaDesdeRespuesta(@NonNull String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONArray matchesArray = root.optJSONArray("matches");
            if (matchesArray == null || matchesArray.length() == 0) {
                return -1;
            }
            int maxMatchday = -1;
            for (int i = 0; i < matchesArray.length(); i++) {
                JSONObject obj = matchesArray.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                int matchday = obj.optInt("matchday", -1);
                if (matchday > maxMatchday) {
                    maxMatchday = matchday;
                }
            }
            return maxMatchday;
        } catch (Exception ignored) {
            return -1;
        }
    }

    @NonNull
    private List<MatchItem> parseMatchesSemana(@NonNull String body,
                                               @NonNull String competitionCode,
                                               int targetMatchday) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONArray matchesArray = root.optJSONArray("matches");
        List<MatchItem> all = new ArrayList<>();
        if (matchesArray == null) {
            return all;
        }

        int jornadaFiltrada = targetMatchday > 0 ? targetMatchday : extraerJornadaDesdeRespuesta(body);

        for (int i = 0; i < matchesArray.length(); i++) {
            JSONObject obj = matchesArray.optJSONObject(i);
            if (obj == null) {
                continue;
            }

            int matchday = obj.optInt("matchday", -1);
            if (jornadaFiltrada > 0 && matchday > 0 && matchday != jornadaFiltrada) {
                continue;
            }

            long id = obj.optLong("id", -1L);
            if (id <= 0L) {
                continue;
            }

            JSONObject homeObj = obj.optJSONObject("homeTeam");
            JSONObject awayObj = obj.optJSONObject("awayTeam");
            String home = homeObj != null ? homeObj.optString("name", "-") : "-";
            String away = awayObj != null ? awayObj.optString("name", "-") : "-";

            String status = obj.optString("status", "");
            String utcDate = obj.optString("utcDate", "");
            LocalDateTime dateTime = parseUtcToMadrid(utcDate);

            String homeLogo = homeObj != null ? homeObj.optString("crest", null) : null;
            String awayLogo = awayObj != null ? awayObj.optString("crest", null) : null;
            long kickoffEpochMs = parseUtcToEpochMs(utcDate);

            boolean finished = "FINISHED".equals(status);
            boolean started = isStatusStarted(status);

            String meta = dateTime == null
                    ? status
                    : dateTime.format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", APP_LOCALE));

            String resultadoFinal = null;
            String resultadoMarcador = null;
            if (finished) {
                JSONObject score = obj.optJSONObject("score");
                JSONObject fullTime = score != null ? score.optJSONObject("fullTime") : null;

                Integer homeGoals = fullTime != null && !fullTime.isNull("home") ? fullTime.optInt("home") : null;
                Integer awayGoals = fullTime != null && !fullTime.isNull("away") ? fullTime.optInt("away") : null;
                if (homeGoals != null && awayGoals != null) {
                    resultadoMarcador = homeGoals + " - " + awayGoals;
                    if (homeGoals > awayGoals) {
                        resultadoFinal = "1";
                    } else if (homeGoals < awayGoals) {
                        resultadoFinal = "2";
                    } else {
                        resultadoFinal = "X";
                    }
                }
            }

            String matchId = competitionCode + "_" + id;
            all.add(new MatchItem(
                    matchId,
                    home,
                    away,
                    homeLogo,
                    awayLogo,
                    status,
                    meta,
                    kickoffEpochMs,
                    started,
                    finished,
                    resultadoFinal,
                    resultadoMarcador
            ));
        }

        return all;
    }

    private boolean isStatusStarted(@NonNull String status) {
        return "IN_PLAY".equals(status)
                || "PAUSED".equals(status)
                || "FINISHED".equals(status)
                || "SUSPENDED".equals(status);
    }

    @Nullable
    private LocalDateTime parseUtcToMadrid(@Nullable String utcDate) {
        if (utcDate == null || utcDate.trim().isEmpty()) {
            return null;
        }
        try {
            Instant instant = Instant.parse(utcDate);
            return LocalDateTime.ofInstant(instant, APP_ZONE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long parseUtcToEpochMs(@Nullable String utcDate) {
        if (utcDate == null || utcDate.trim().isEmpty()) {
            return -1L;
        }
        try {
            return Instant.parse(utcDate).toEpochMilli();
        } catch (Exception ignored) {
            return -1L;
        }
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
    private String valorString(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private long valorLong(@Nullable Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private void mostrarCarga(boolean loading) {
        pbQuiniela.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void mostrarEstado(@NonNull String text, boolean visible) {
        tvQuinielaStatus.setText(text);
        tvQuinielaStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void ocultarEstado() {
        tvQuinielaStatus.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        executorService.shutdownNow();
        super.onDestroy();
    }

    private static class MatchItem {
        final String matchId;
        final String homeTeam;
        final String awayTeam;
        @Nullable
        final String homeLogo;
        @Nullable
        final String awayLogo;
        final String status;
        final String meta;
        final long kickoffEpochMs;
        final boolean started;
        final boolean finished;
        @Nullable
        final String resultadoFinal;
        @Nullable
        final String resultadoMarcador;

        MatchItem(@NonNull String matchId,
                  @NonNull String homeTeam,
                  @NonNull String awayTeam,
                  @Nullable String homeLogo,
                  @Nullable String awayLogo,
                  @NonNull String status,
                  @NonNull String meta,
                  long kickoffEpochMs,
                  boolean started,
                  boolean finished,
                  @Nullable String resultadoFinal,
                  @Nullable String resultadoMarcador) {
            this.matchId = matchId;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.homeLogo = homeLogo;
            this.awayLogo = awayLogo;
            this.status = status;
            this.meta = meta;
            this.kickoffEpochMs = kickoffEpochMs;
            this.started = started;
            this.finished = finished;
            this.resultadoFinal = resultadoFinal;
            this.resultadoMarcador = resultadoMarcador;
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

    private static class TxResultado {
        final boolean xpAplicada;
        final long xpTotal;
        final long rango;

        TxResultado(boolean xpAplicada, long xpTotal, long rango) {
            this.xpAplicada = xpAplicada;
            this.xpTotal = xpTotal;
            this.rango = rango;
        }
    }

    private static class MatchdayStats {
        int count;
        long minKickoffEpochMs = -1L;
    }
}
