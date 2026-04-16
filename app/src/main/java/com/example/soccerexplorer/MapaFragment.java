package com.example.soccerexplorer;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MapaFragment extends Fragment {

    private static final String WORLD_GEOJSON_URL = "https://raw.githubusercontent.com/johan/world.geo.json/master/countries.geo.json";
    private static final String WORLD_COASTLINE_GEOJSON_URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_coastline.geojson";
    private static final String API_BASE = "https://api.football-data.org/v4";
    private static final String NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    private static final String REST_COUNTRIES_NAME_URL = "https://restcountries.com/v3.1/name/";
    private static final String COLLECTION_COMPETICIONES = "competiciones";
    private static final String COLLECTION_EQUIPOS = "equipos";
    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_EQUIPO_FAVORITO_LIGA_ID = "equipoFavoritoLigaId";
    private static final long NOMINATIM_MIN_INTERVAL_MS = 1100L;

    private static final Map<String, String> COMPETICIONES_OBJETIVO = new LinkedHashMap<>();
    private static final Set<String> LIGAS_SYNC_API = new HashSet<>();
    private static final Map<String, CapitalInfo> CAPITALES_ESPECIALES = new HashMap<>();

    static {
        COMPETICIONES_OBJETIVO.put("PPL", "Liga Portuguesa");
        COMPETICIONES_OBJETIVO.put("EC", "Eurocopa");
        COMPETICIONES_OBJETIVO.put("WC", "Mundial");

        LIGAS_SYNC_API.add("PPL");
        LIGAS_SYNC_API.add("EC");
        LIGAS_SYNC_API.add("WC");

        CAPITALES_ESPECIALES.put("england", new CapitalInfo("London", 51.5072, -0.1276));
        CAPITALES_ESPECIALES.put("scotland", new CapitalInfo("Edinburgh", 55.9533, -3.1883));
        CAPITALES_ESPECIALES.put("wales", new CapitalInfo("Cardiff", 51.4816, -3.1791));
        CAPITALES_ESPECIALES.put("northern ireland", new CapitalInfo("Belfast", 54.5973, -5.9301));
    }

    private static final XYTileSource DARK_NO_LABELS_TILE_SOURCE = new XYTileSource(
            "CartoDarkNoLabels",
            0,
            20,
            256,
            ".png",
            new String[]{
                    "https://a.basemaps.cartocdn.com/dark_nolabels/",
                    "https://b.basemaps.cartocdn.com/dark_nolabels/",
                    "https://c.basemaps.cartocdn.com/dark_nolabels/"
            }
    );

    private static final XYTileSource WHITE_LABELS_TILE_SOURCE = new XYTileSource(
            "CartoWhiteLabels",
            0,
            20,
            256,
            ".png",
            new String[]{
                    "https://a.basemaps.cartocdn.com/dark_only_labels/",
                    "https://b.basemaps.cartocdn.com/dark_only_labels/",
                    "https://c.basemaps.cartocdn.com/dark_only_labels/"
            }
    );

    private static final ColorMatrixColorFilter WHITE_LABELS_COLOR_FILTER = new ColorMatrixColorFilter(
            new ColorMatrix(new float[]{
                    0f, 0f, 0f, 0f, 255f,
                    0f, 0f, 0f, 0f, 255f,
                    0f, 0f, 0f, 0f, 255f,
                    0f, 0f, 0f, 0f, 0f
            })
    );

    private MapView mapView;
    private ChipGroup grupoChipsLigasMapa;
    private ProgressBar pbMapaEquipos;
    private TextView tvMapaEstado;

    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;

    private MapTileProviderBasic whiteLabelsProvider;
    private TilesOverlay whiteLabelsOverlay;
    private RadiusMarkerClusterer equiposClusterer;

    private ExecutorService worldOverlayExecutor;
    private ExecutorService leagueSyncExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Overlay> worldOverlays = new ArrayList<>();
    private int worldLoadGeneration = 0;

    private final List<LigaItem> ligas = new ArrayList<>();
    private final List<CustomTarget<Bitmap>> activeShieldTargets = new ArrayList<>();
    private boolean bloqueandoEventoChipLiga;
    private int equiposLoadGeneration = 0;
    private final Set<String> ligasSincronizando = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> ligasSincronizadasSesion = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, CoordenadaGeo> geocodeCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, CapitalInfo> capitalesCache = Collections.synchronizedMap(new HashMap<>());
    private long lastNominatimRequestMs = 0L;
    @Nullable
    private LigaItem ligaSeleccionadaActual = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        View view = inflater.inflate(R.layout.fragment_mapa, container, false);
        mapView = view.findViewById(R.id.osmMapView);
        grupoChipsLigasMapa = view.findViewById(R.id.grupoChipsLigasMapa);
        pbMapaEquipos = view.findViewById(R.id.pbMapaEquipos);
        tvMapaEstado = view.findViewById(R.id.tvMapaEstado);

        firestore = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();

        configurarMapa();
        configurarCapaEtiquetasBlancas();
        configurarClusterEquipos();
        configurarChipsLigas();
        cargarCapasLineasMundo();
        cargarLigas();

        return view;
    }

    private void configurarMapa() {
        if (mapView == null) {
            return;
        }

        mapView.setTileSource(DARK_NO_LABELS_TILE_SOURCE);
        mapView.setMultiTouchControls(true);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(18.0);
        mapView.getController().setZoom(5.5);
        mapView.getController().setCenter(new GeoPoint(40.4168, -3.7038));
        mapView.setBuiltInZoomControls(true);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setScrollableAreaLimitDouble(new BoundingBox(85.0, 180.0, -85.0, -180.0));

        int seaColor = ContextCompat.getColor(requireContext(), R.color.background_primary);
        mapView.setBackgroundColor(seaColor);
        ocultarCapaBaseMapa();
    }

    private void configurarChipsLigas() {
        if (grupoChipsLigasMapa == null) {
            return;
        }

        grupoChipsLigasMapa.setOnCheckedStateChangeListener((group, idsMarcados) -> {
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
    }

    private void configurarClusterEquipos() {
        if (!isAdded() || mapView == null || equiposClusterer != null) {
            return;
        }

        RadiusMarkerClusterer clusterer = new RadiusMarkerClusterer(requireContext().getApplicationContext());
        clusterer.setRadius(86);
        clusterer.setAnimation(true);
        clusterer.setMaxClusteringZoomLevel(7);
        clusterer.setIcon(crearIconoCluster());
        equiposClusterer = clusterer;
        mapView.getOverlays().add(clusterer);
        asegurarOrdenCapas();
    }

    private Bitmap crearIconoCluster() {
        int sizePx = dpToPx(58);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float radius = sizePx / 2f;
        int colorStart = ContextCompat.getColor(requireContext(), R.color.primary);
        int colorEnd = ContextCompat.getColor(requireContext(), R.color.accent_coral);
        int strokeColor = ContextCompat.getColor(requireContext(), R.color.accent_coral_stroke);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setShader(new LinearGradient(
                0f,
                0f,
                sizePx,
                sizePx,
                colorStart,
                colorEnd,
                Shader.TileMode.CLAMP
        ));

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dpToPx(2));
        strokePaint.setColor(strokeColor);

        canvas.drawCircle(radius, radius, radius - dpToPx(1), fillPaint);
        canvas.drawCircle(radius, radius, radius - dpToPx(1), strokePaint);
        return bitmap;
    }

    private void cargarLigas() {
        mostrarCargaEquipos(true);
        ocultarEstado();

        firestore.collection(COLLECTION_COMPETICIONES)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) {
                        return;
                    }

                    ligas.clear();
                    Set<String> ligasExistentes = new HashSet<>();
                    for (DocumentSnapshot documentSnapshot : queryDocumentSnapshots.getDocuments()) {
                        String ligaId = documentSnapshot.getId();
                        String ligaNombre = documentSnapshot.getString("nombre");
                        if (ligaNombre == null || ligaNombre.trim().isEmpty()) {
                            ligaNombre = ligaId;
                        }
                        ligas.add(new LigaItem(ligaId, ligaNombre));
                        ligasExistentes.add(ligaId.toUpperCase(Locale.ROOT));
                    }

                    anexarCompeticionesObjetivoSiFaltan(ligasExistentes);
                    asegurarCompeticionesObjetivoEnFirestore();

                    Collections.sort(ligas, (a, b) -> a.nombre.compareToIgnoreCase(b.nombre));
                    crearChipsLigas();

                    if (ligas.isEmpty()) {
                        mostrarCargaEquipos(false);
                        mostrarEstado(R.string.map_empty_leagues);
                        limpiarEquiposMapa();
                        return;
                    }

                    seleccionarLigaInicial();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    mostrarCargaEquipos(false);
                    mostrarEstado(R.string.map_error_load_leagues);
                    limpiarEquiposMapa();
                });
    }

    private void anexarCompeticionesObjetivoSiFaltan(@NonNull Set<String> ligasExistentes) {
        for (Map.Entry<String, String> entry : COMPETICIONES_OBJETIVO.entrySet()) {
            String ligaId = entry.getKey();
            if (ligasExistentes.contains(ligaId.toUpperCase(Locale.ROOT))) {
                continue;
            }
            ligas.add(new LigaItem(ligaId, entry.getValue()));
        }
    }

    private void asegurarCompeticionesObjetivoEnFirestore() {
        for (Map.Entry<String, String> entry : COMPETICIONES_OBJETIVO.entrySet()) {
            Map<String, Object> data = new HashMap<>();
            data.put("nombre", entry.getValue());
            data.put("updatedAt", FieldValue.serverTimestamp());
            firestore.collection(COLLECTION_COMPETICIONES)
                    .document(entry.getKey())
                    .set(data, SetOptions.merge());
        }
    }

    private void crearChipsLigas() {
        if (grupoChipsLigasMapa == null) {
            return;
        }

        grupoChipsLigasMapa.removeAllViews();

        for (LigaItem liga : ligas) {
            Chip chipLiga = new Chip(requireContext());
            chipLiga.setId(View.generateViewId());
            chipLiga.setText(liga.nombre);
            chipLiga.setTag(liga);
            chipLiga.setCheckable(true);
            chipLiga.setCheckedIconVisible(false);
            chipLiga.setChipBackgroundColorResource(R.color.chip_liga_fondo);
            chipLiga.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_liga_texto));
            chipLiga.setChipStrokeColorResource(R.color.chip_liga_borde);
            chipLiga.setChipStrokeWidth(1f);
            grupoChipsLigasMapa.addView(chipLiga);
        }
    }

    private void seleccionarLigaInicial() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            seleccionarYMarcarLigaPorId(null);
            return;
        }

        firestore.collection(COLLECTION_USERS)
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) {
                        return;
                    }
                    String ligaFavoritaId = documentSnapshot.getString(FIELD_EQUIPO_FAVORITO_LIGA_ID);
                    seleccionarYMarcarLigaPorId(ligaFavoritaId);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    seleccionarYMarcarLigaPorId(null);
                });
    }

    private void seleccionarYMarcarLigaPorId(@Nullable String ligaIdPreferida) {
        if (ligas.isEmpty()) {
            mostrarCargaEquipos(false);
            mostrarEstado(R.string.map_empty_leagues);
            return;
        }

        LigaItem ligaInicial = ligas.get(0);
        if (ligaIdPreferida != null && !ligaIdPreferida.trim().isEmpty()) {
            for (LigaItem liga : ligas) {
                if (liga.id.equals(ligaIdPreferida)) {
                    ligaInicial = liga;
                    break;
                }
            }
        }

        marcarChipLigaSeleccionada(ligaInicial.id);
        alSeleccionarLiga(ligaInicial);
    }

    private void marcarChipLigaSeleccionada(@NonNull String ligaId) {
        if (grupoChipsLigasMapa == null) {
            return;
        }

        for (int i = 0; i < grupoChipsLigasMapa.getChildCount(); i++) {
            View child = grupoChipsLigasMapa.getChildAt(i);
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
                grupoChipsLigasMapa.check(chip.getId());
                bloqueandoEventoChipLiga = false;
                break;
            }
        }
    }

    private void alSeleccionarLiga(@NonNull LigaItem liga) {
        ligaSeleccionadaActual = liga;
        cargarEquiposLiga(liga.id);
    }

    private void cargarEquiposLiga(@NonNull String ligaId) {
        int generationAtSubmit = ++equiposLoadGeneration;
        mostrarCargaEquipos(true);
        ocultarEstado();
        limpiarCargasEscudosPendientes();
        limpiarEquiposMapa();

        firestore.collection(COLLECTION_COMPETICIONES)
                .document(ligaId)
                .collection(COLLECTION_EQUIPOS)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded() || generationAtSubmit != equiposLoadGeneration) {
                        return;
                    }

                    List<EquipoMapaItem> equipos = new ArrayList<>();
                    for (DocumentSnapshot documentSnapshot : queryDocumentSnapshots.getDocuments()) {
                        EquipoMapaItem item = parsearEquipo(documentSnapshot);
                        if (item != null) {
                            equipos.add(item);
                        }
                    }

                    Collections.sort(equipos, (a, b) -> a.equipo.compareToIgnoreCase(b.equipo));
                    boolean requiereSync = debeSincronizarLigaDesdeApi(
                            ligaId,
                            queryDocumentSnapshots,
                            equipos
                    );

                    if (requiereSync) {
                        mostrarEstado(R.string.map_status_syncing_teams);
                        iniciarSincronizacionLigaEnSegundoPlano(ligaId, generationAtSubmit);
                    }

                    mostrarCargaEquipos(false);

                    if (equipos.isEmpty()) {
                        if (requiereSync) {
                            limpiarEquiposMapa();
                            return;
                        }

                        mostrarEstado(R.string.map_empty_teams_coordinates);
                        limpiarEquiposMapa();
                        return;
                    }

                    if (!requiereSync) {
                        ocultarEstado();
                    }
                    poblarMapaConEquipos(equipos, generationAtSubmit);
                    ajustarCamaraAEqipos(equipos);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || generationAtSubmit != equiposLoadGeneration) {
                        return;
                    }
                    mostrarCargaEquipos(false);
                    mostrarEstado(R.string.map_error_load_teams);
                    limpiarEquiposMapa();
                });
    }

    private boolean debeSincronizarLigaDesdeApi(@NonNull String ligaId,
                                                @NonNull QuerySnapshot querySnapshot,
                                                @NonNull List<EquipoMapaItem> equiposParseados) {
        String ligaKey = ligaId.trim().toUpperCase(Locale.ROOT);
        if (!LIGAS_SYNC_API.contains(ligaKey)) {
            return false;
        }
        if (ligasSincronizadasSesion.contains(ligaKey) || ligasSincronizando.contains(ligaKey)) {
            return false;
        }

        int totalDocs = querySnapshot.size();
        if (totalDocs == 0) {
            return true;
        }

        if (equiposParseados.isEmpty() || equiposParseados.size() < totalDocs) {
            return true;
        }

        if ("PPL".equals(ligaKey) && totalDocs < 10) {
            return true;
        }

        return ("EC".equals(ligaKey) || "WC".equals(ligaKey)) && totalDocs < 16;
    }

    private void iniciarSincronizacionLigaEnSegundoPlano(@NonNull String ligaId, int generationAtSubmit) {
        String ligaKey = ligaId.trim().toUpperCase(Locale.ROOT);
        if (!LIGAS_SYNC_API.contains(ligaKey)
                || ligasSincronizadasSesion.contains(ligaKey)
                || ligasSincronizando.contains(ligaKey)) {
            return;
        }

        ligasSincronizando.add(ligaKey);
        ExecutorService executor = asegurarEjecutorSyncLigas();
        executor.execute(() -> {
            SyncResult result = sincronizarLigaDesdeApi(ligaKey);

            ligasSincronizando.remove(ligaKey);
            if (result.success) {
                ligasSincronizadasSesion.add(ligaKey);
            }

            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }

                boolean mismaLiga = ligaSeleccionadaActual != null
                        && ligaSeleccionadaActual.id.equalsIgnoreCase(ligaKey);
                if (!mismaLiga) {
                    return;
                }

                if (result.success) {
                    cargarEquiposLiga(ligaKey);
                    return;
                }

                if (generationAtSubmit == equiposLoadGeneration
                        && equiposClusterer != null
                        && !equiposClusterer.getItems().isEmpty()) {
                    ocultarEstado();
                    return;
                }

                if (generationAtSubmit == equiposLoadGeneration
                        && (equiposClusterer == null || equiposClusterer.getItems().isEmpty())) {
                    mostrarCargaEquipos(false);
                    mostrarEstado(result.errorMessageRes != 0
                            ? result.errorMessageRes
                            : R.string.map_error_load_teams);
                }
            });
        });
    }

    private ExecutorService asegurarEjecutorSyncLigas() {
        if (leagueSyncExecutor == null || leagueSyncExecutor.isShutdown() || leagueSyncExecutor.isTerminated()) {
            leagueSyncExecutor = Executors.newSingleThreadExecutor();
        }
        return leagueSyncExecutor;
    }

    @NonNull
    private SyncResult sincronizarLigaDesdeApi(@NonNull String ligaCode) {
        if (BuildConfig.FOOTBALL_DATA_API_KEY == null || BuildConfig.FOOTBALL_DATA_API_KEY.trim().isEmpty()) {
            return SyncResult.error(R.string.map_error_sync_missing_api_key);
        }

        ApiResult apiResult = ejecutarGetFootballData("/competitions/" + ligaCode + "/teams");
        if (!apiResult.ok) {
            return SyncResult.error(R.string.map_error_sync_teams_api);
        }

        List<ApiTeamItem> equiposApi;
        try {
            equiposApi = parsearEquiposApi(apiResult.body);
        } catch (Exception e) {
            return SyncResult.error(R.string.map_error_sync_parse_teams);
        }

        if (equiposApi.isEmpty()) {
            return SyncResult.error(R.string.map_error_sync_empty_teams);
        }

        try {
            WriteBatch batch = firestore.batch();
            var compRef = firestore.collection(COLLECTION_COMPETICIONES).document(ligaCode);

            Map<String, Object> compData = new HashMap<>();
            compData.put("nombre", nombreCompeticionPorCodigo(ligaCode));
            compData.put("updatedAt", FieldValue.serverTimestamp());
            batch.set(compRef, compData, SetOptions.merge());

            int equiposGuardados = 0;
            for (ApiTeamItem equipo : equiposApi) {
                CoordenadaGeo coordenada = resolverCoordenadasEquipo(ligaCode, equipo);
                if (coordenada == null) {
                    continue;
                }

                Map<String, Object> data = new HashMap<>();
                data.put("equipo", equipo.name);
                data.put("nombre", equipo.venue.isEmpty() ? equipo.name : equipo.venue);
                data.put("ciudad", equipo.city);
                data.put("escudo", equipo.crest);
                data.put("latitud", coordenada.latitud);
                data.put("longitud", coordenada.longitud);
                data.put("source", "football-data");
                data.put("updatedAt", FieldValue.serverTimestamp());

                batch.set(
                        compRef.collection(COLLECTION_EQUIPOS).document(String.valueOf(equipo.id)),
                        data,
                        SetOptions.merge()
                );
                equiposGuardados++;
            }

            if (equiposGuardados == 0) {
                return SyncResult.error(R.string.map_error_sync_no_coordinates);
            }

            Tasks.await(batch.commit());
            return SyncResult.success();
        } catch (Exception e) {
            return SyncResult.error(R.string.map_error_sync_firestore);
        }
    }

    @NonNull
    private String nombreCompeticionPorCodigo(@NonNull String ligaCode) {
        String nombre = COMPETICIONES_OBJETIVO.get(ligaCode);
        return nombre == null || nombre.trim().isEmpty() ? ligaCode : nombre;
    }

    @NonNull
    private List<ApiTeamItem> parsearEquiposApi(@NonNull String body) throws Exception {
        List<ApiTeamItem> out = new ArrayList<>();
        JSONObject root = new JSONObject(body);
        JSONArray teams = root.optJSONArray("teams");
        if (teams == null) {
            return out;
        }

        for (int i = 0; i < teams.length(); i++) {
            JSONObject team = teams.optJSONObject(i);
            if (team == null) {
                continue;
            }

            int id = team.optInt("id", -1);
            if (id <= 0) {
                continue;
            }

            String name = normalizarTexto(team.optString("name", ""));
            if (name.isEmpty()) {
                continue;
            }

            String venue = normalizarTexto(team.optString("venue", ""));
            String address = normalizarTexto(team.optString("address", ""));
            String crest = normalizarTexto(team.optString("crest", ""));
            JSONObject areaObj = team.optJSONObject("area");
            String areaName = areaObj == null ? "" : normalizarTexto(areaObj.optString("name", ""));
            String city = extraerCiudadDesdeAddress(address);

            if (city.isEmpty() && !areaName.isEmpty()) {
                city = areaName;
            }

            out.add(new ApiTeamItem(id, name, venue, city, areaName, crest));
        }

        return out;
    }

    @NonNull
    private String extraerCiudadDesdeAddress(@NonNull String address) {
        if (address.trim().isEmpty()) {
            return "";
        }
        String[] parts = address.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.matches(".*\\d.*") && parts.length > 1) {
                continue;
            }
            return candidate;
        }
        return "";
    }

    @Nullable
    private CoordenadaGeo resolverCoordenadasEquipo(@NonNull String ligaCode, @NonNull ApiTeamItem equipo) {
        if ("EC".equalsIgnoreCase(ligaCode) || "WC".equalsIgnoreCase(ligaCode)) {
            String country = equipo.areaName.isEmpty() ? equipo.name : equipo.areaName;
            return resolverCoordenadasCapital(country);
        }
        return resolverCoordenadasClubPortugal(equipo);
    }

    @Nullable
    private CoordenadaGeo resolverCoordenadasClubPortugal(@NonNull ApiTeamItem equipo) {
        List<String> queries = new ArrayList<>();
        if (!equipo.venue.isEmpty() && !equipo.city.isEmpty()) {
            queries.add(equipo.venue + ", " + equipo.city + ", Portugal");
        }
        if (!equipo.city.isEmpty()) {
            queries.add(equipo.city + ", Portugal");
        }
        if (!equipo.venue.isEmpty()) {
            queries.add(equipo.venue + ", Portugal");
        }
        queries.add(equipo.name + ", Portugal");

        for (String query : queries) {
            CoordenadaGeo coordenada = geocodificarConCache(query);
            if (coordenada != null) {
                return coordenada;
            }
        }
        return null;
    }

    @Nullable
    private CoordenadaGeo resolverCoordenadasCapital(@NonNull String countryRaw) {
        String country = countryRaw.trim();
        if (country.isEmpty()) {
            return null;
        }

        String key = normalizarClave(country);
        CapitalInfo especial = CAPITALES_ESPECIALES.get(key);
        if (especial != null) {
            return new CoordenadaGeo(especial.latitud, especial.longitud);
        }

        CapitalInfo cached = capitalesCache.get(key);
        if (cached != null) {
            return new CoordenadaGeo(cached.latitud, cached.longitud);
        }

        CapitalInfo fetched = obtenerCapitalPorRestCountries(country);
        if (fetched != null) {
            capitalesCache.put(key, fetched);
            return new CoordenadaGeo(fetched.latitud, fetched.longitud);
        }

        return geocodificarConCache(country);
    }

    @Nullable
    private CapitalInfo obtenerCapitalPorRestCountries(@NonNull String country) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            String encoded = URLEncoder.encode(country, StandardCharsets.UTF_8.name());
            String urlStr = REST_COUNTRIES_NAME_URL + encoded + "?fields=capital,latlng,name";
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "SoccerExplorer-Android");

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }

            inputStream = connection.getInputStream();
            String body = leerInputStream(inputStream);
            JSONArray array = new JSONArray(body);
            if (array.length() == 0) {
                return null;
            }

            JSONObject first = array.optJSONObject(0);
            if (first == null) {
                return null;
            }

            String capitalName = "";
            JSONArray capitalArray = first.optJSONArray("capital");
            if (capitalArray != null && capitalArray.length() > 0) {
                capitalName = normalizarTexto(capitalArray.optString(0, ""));
            }

            JSONArray latlng = first.optJSONArray("latlng");
            if (latlng != null && latlng.length() >= 2) {
                double lat = latlng.optDouble(0, Double.NaN);
                double lon = latlng.optDouble(1, Double.NaN);
                if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                    return new CapitalInfo(capitalName, lat, lon);
                }
            }

            if (!capitalName.isEmpty()) {
                CoordenadaGeo capitalGeo = geocodificarConCache(capitalName + ", " + country);
                if (capitalGeo != null) {
                    return new CapitalInfo(capitalName, capitalGeo.latitud, capitalGeo.longitud);
                }
            }

            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Nullable
    private CoordenadaGeo geocodificarConCache(@NonNull String rawQuery) {
        String query = rawQuery.trim();
        if (query.isEmpty()) {
            return null;
        }

        String key = normalizarClave(query);
        CoordenadaGeo cached = geocodeCache.get(key);
        if (cached != null) {
            return cached;
        }

        CoordenadaGeo fetched = geocodificarNominatim(query);
        if (fetched != null) {
            geocodeCache.put(key, fetched);
        }
        return fetched;
    }

    @Nullable
    private CoordenadaGeo geocodificarNominatim(@NonNull String query) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            esperarVentanaNominatim();
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String urlStr = NOMINATIM_SEARCH_URL
                    + "?q=" + encoded
                    + "&format=json"
                    + "&limit=1"
                    + "&addressdetails=0";
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "SoccerExplorer-Android/1.0");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.8");

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }

            inputStream = connection.getInputStream();
            String body = leerInputStream(inputStream);
            JSONArray results = new JSONArray(body);
            if (results.length() == 0) {
                return null;
            }

            JSONObject first = results.optJSONObject(0);
            if (first == null) {
                return null;
            }

            String latStr = first.optString("lat", "");
            String lonStr = first.optString("lon", "");
            if (latStr.trim().isEmpty() || lonStr.trim().isEmpty()) {
                return null;
            }

            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);
            if (lat < -90d || lat > 90d || lon < -180d || lon > 180d) {
                return null;
            }

            return new CoordenadaGeo(lat, lon);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private synchronized void esperarVentanaNominatim() {
        long now = System.currentTimeMillis();
        long diff = now - lastNominatimRequestMs;
        if (diff < NOMINATIM_MIN_INTERVAL_MS) {
            long sleepMs = NOMINATIM_MIN_INTERVAL_MS - diff;
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        lastNominatimRequestMs = System.currentTimeMillis();
    }

    @NonNull
    private String normalizarClave(@NonNull String input) {
        return input.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    @NonNull
    private ApiResult ejecutarGetFootballData(@NonNull String pathAndQuery) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;

        try {
            URL url = new URL(API_BASE + pathAndQuery);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("X-Auth-Token", BuildConfig.FOOTBALL_DATA_API_KEY);
            connection.setRequestProperty("User-Agent", "SoccerExplorer-Android");
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            inputStream = (code >= 200 && code < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = leerInputStream(inputStream);
            return new ApiResult(code >= 200 && code < 300, code, body);
        } catch (Exception e) {
            return new ApiResult(false, 500, "");
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
            }

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

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toString(StandardCharsets.UTF_8.name());
    }

    @Nullable
    private EquipoMapaItem parsearEquipo(@NonNull DocumentSnapshot documentSnapshot) {
        Double latitud = parsearCoordenada(documentSnapshot.get("latitud"));
        Double longitud = parsearCoordenada(documentSnapshot.get("longitud"));
        if (latitud == null || longitud == null) {
            return null;
        }
        if (latitud < -90d || latitud > 90d || longitud < -180d || longitud > 180d) {
            return null;
        }

        String equipo = normalizarTexto(documentSnapshot.getString("equipo"));
        if (equipo.isEmpty()) {
            equipo = normalizarTexto(documentSnapshot.getString("nombre"));
        }
        if (equipo.isEmpty()) {
            equipo = documentSnapshot.getId();
        }

        String estadio = normalizarTexto(documentSnapshot.getString("nombre"));
        String ciudad = normalizarTexto(documentSnapshot.getString("ciudad"));
        String escudo = normalizarTexto(documentSnapshot.getString("escudo"));
        if (escudo.isEmpty()) {
            escudo = null;
        }

        return new EquipoMapaItem(
                documentSnapshot.getId(),
                equipo,
                estadio,
                ciudad,
                latitud,
                longitud,
                escudo
        );
    }

    @Nullable
    private Double parsearCoordenada(@Nullable Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(text.replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    @NonNull
    private String normalizarTexto(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private void poblarMapaConEquipos(@NonNull List<EquipoMapaItem> equipos, int generationAtSubmit) {
        if (mapView == null || equiposClusterer == null) {
            return;
        }

        for (EquipoMapaItem equipo : equipos) {
            Marker marker = crearMarkerEquipo(equipo, generationAtSubmit);
            equiposClusterer.add(marker);
        }

        equiposClusterer.invalidate();
        asegurarOrdenCapas();
        mapView.invalidate();
    }

    @NonNull
    private Marker crearMarkerEquipo(@NonNull EquipoMapaItem equipo, int generationAtSubmit) {
        Marker marker = new Marker(mapView);
        marker.setPosition(new GeoPoint(equipo.latitud, equipo.longitud));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setInfoWindowAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_TOP);
        marker.setTitle(equipo.equipo);
        marker.setSnippet(construirSubtituloMarker(equipo));
        marker.setIcon(new BitmapDrawable(getResources(), crearIconoEquipo(null, equipo.equipo)));

        marker.setOnMarkerClickListener((m, map) -> {
            cerrarInfoWindowsEquipos();
            m.showInfoWindow();
            map.getController().animateTo(m.getPosition());
            if (ligaSeleccionadaActual != null && isAdded()) {
                Intent intent = new Intent(requireContext(), TeamDetailsActivity.class);
                intent.putExtra(TeamDetailsActivity.EXTRA_COMPETITION_CODE, ligaSeleccionadaActual.id);
                intent.putExtra(TeamDetailsActivity.EXTRA_TEAM_NAME, equipo.equipo);
                startActivity(intent);
            }
            return true;
        });

        cargarEscudoEnMarker(marker, equipo.escudoUrl, generationAtSubmit);
        return marker;
    }

    @NonNull
    private String construirSubtituloMarker(@NonNull EquipoMapaItem equipo) {
        if (!equipo.estadio.isEmpty() && !equipo.ciudad.isEmpty()) {
            return getString(R.string.map_team_snippet_stadium_city, equipo.estadio, equipo.ciudad);
        }
        if (!equipo.estadio.isEmpty()) {
            return equipo.estadio;
        }
        if (!equipo.ciudad.isEmpty()) {
            return equipo.ciudad;
        }
        return getString(R.string.map_team_location_unknown);
    }

    private void cerrarInfoWindowsEquipos() {
        if (equiposClusterer == null) {
            return;
        }
        for (Marker marker : equiposClusterer.getItems()) {
            marker.closeInfoWindow();
        }
    }

    private void cargarEscudoEnMarker(@NonNull Marker marker, @Nullable String escudoUrl, int generationAtSubmit) {
        Object model = construirModeloSolicitudEscudo(escudoUrl);
        if (model == null) {
            return;
        }

        CustomTarget<Bitmap> target = new CustomTarget<Bitmap>(dpToPx(120), dpToPx(120)) {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                activeShieldTargets.remove(this);
                if (!isAdded() || mapView == null || equiposClusterer == null || generationAtSubmit != equiposLoadGeneration) {
                    return;
                }
                marker.setIcon(new BitmapDrawable(getResources(), crearIconoEquipo(resource, marker.getTitle())));
                equiposClusterer.invalidate();
                mapView.invalidate();
            }

            @Override
            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                activeShieldTargets.remove(this);
            }

            @Override
            public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                activeShieldTargets.remove(this);
            }
        };

        activeShieldTargets.add(target);
        Glide.with(this)
                .asBitmap()
                .load(model)
                .into(target);
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
                    ("x-access-token:" + token).getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
            );

            return new com.bumptech.glide.load.model.GlideUrl(
                    cleanUrl,
                    new com.bumptech.glide.load.model.LazyHeaders.Builder()
                            .addHeader("Authorization", "Basic " + basicAuth)
                            .addHeader("User-Agent", "SoccerExplorer")
                            .build()
            );
        }

        return normalizedUrl;
    }

    @NonNull
    private Bitmap crearIconoEquipo(@Nullable Bitmap escudoBitmap, @Nullable String teamName) {
        int circleDiameterPx = dpToPx(80);
        int textAreaHeightPx = dpToPx(20);
        int totalWidthPx = circleDiameterPx;
        int totalHeightPx = circleDiameterPx + textAreaHeightPx;
        int paddingPx = dpToPx(8);

        Bitmap bitmap = Bitmap.createBitmap(totalWidthPx, totalHeightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float centerX = totalWidthPx / 2f;
        float centerY = circleDiameterPx / 2f;
        float outerRadius = circleDiameterPx / 2f - dpToPx(1);

        int white = ContextCompat.getColor(requireContext(), R.color.white);
        int coral = ContextCompat.getColor(requireContext(), R.color.accent_coral);
        int stroke = ContextCompat.getColor(requireContext(), R.color.accent_coral_stroke);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setShader(new LinearGradient(
                0f,
                0f,
                totalWidthPx,
                totalHeightPx,
                new int[]{white, white, coral},
                new float[]{0f, 0.78f, 1f},
                Shader.TileMode.CLAMP
        ));

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dpToPx(2));
        strokePaint.setColor(stroke);

        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#44000000"));

        canvas.drawCircle(centerX, centerY + dpToPx(1), outerRadius, shadowPaint);
        canvas.drawCircle(centerX, centerY, outerRadius, fillPaint);
        canvas.drawCircle(centerX, centerY, outerRadius, strokePaint);

        if (escudoBitmap == null || escudoBitmap.isRecycled()) {
            Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fallbackPaint.setColor(ContextCompat.getColor(requireContext(), R.color.primary));
            canvas.drawCircle(centerX, centerY, dpToPx(11), fallbackPaint);
        } else {
            float innerRadius = centerX - paddingPx;
            BitmapShader shader = new BitmapShader(escudoBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            float drawableSize = innerRadius * 2f;
            float scale = Math.max(
                    drawableSize / Math.max(1f, escudoBitmap.getWidth()),
                    drawableSize / Math.max(1f, escudoBitmap.getHeight())
            );
            float dx = centerX - (escudoBitmap.getWidth() * scale) / 2f;
            float dy = centerY - (escudoBitmap.getHeight() * scale) / 2f;
            matrix.setScale(scale, scale);
            matrix.postTranslate(dx, dy);
            shader.setLocalMatrix(matrix);

            Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            imagePaint.setShader(shader);

            canvas.drawCircle(centerX, centerY, innerRadius, imagePaint);
        }

        // Dibujar nombre del equipo debajo del escudo
        if (teamName != null && !teamName.trim().isEmpty()) {
            String displayName = teamName.trim();
            if (displayName.length() > 12) {
                displayName = displayName.substring(0, 12) + "...";
            }

            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(dpToPx(10));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setShadowLayer(1f, 1f, 1f, Color.BLACK);

            float textY = circleDiameterPx + dpToPx(10);
            canvas.drawText(displayName, centerX, textY, textPaint);
        }

        return bitmap;
    }

    private void ajustarCamaraAEqipos(@NonNull List<EquipoMapaItem> equipos) {
        if (mapView == null || equipos.isEmpty()) {
            return;
        }

        if (equipos.size() == 1) {
            EquipoMapaItem equipo = equipos.get(0);
            GeoPoint point = new GeoPoint(equipo.latitud, equipo.longitud);
            mapView.getController().animateTo(point);
            mapView.getController().setZoom(13.2);
            return;
        }

        double maxLat = -Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;

        for (EquipoMapaItem equipo : equipos) {
            maxLat = Math.max(maxLat, equipo.latitud);
            maxLon = Math.max(maxLon, equipo.longitud);
            minLat = Math.min(minLat, equipo.latitud);
            minLon = Math.min(minLon, equipo.longitud);
        }

        BoundingBox boundingBox = new BoundingBox(maxLat, maxLon, minLat, minLon).increaseByScale(1.08f);
        mapView.post(() -> {
            if (mapView == null) {
                return;
            }
            mapView.zoomToBoundingBox(boundingBox, true);
        });
    }

    private int dpToPx(float dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    private void limpiarEquiposMapa() {
        if (equiposClusterer == null || mapView == null) {
            return;
        }

        cerrarInfoWindowsEquipos();
        equiposClusterer.getItems().clear();
        equiposClusterer.invalidate();
        mapView.invalidate();
    }

    private void limpiarCargasEscudosPendientes() {
        if (!isAdded()) {
            activeShieldTargets.clear();
            return;
        }
        for (CustomTarget<Bitmap> target : new ArrayList<>(activeShieldTargets)) {
            Glide.with(this).clear(target);
        }
        activeShieldTargets.clear();
    }

    private void mostrarCargaEquipos(boolean loading) {
        if (pbMapaEquipos == null) {
            return;
        }
        pbMapaEquipos.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void mostrarEstado(@StringRes int messageRes) {
        if (tvMapaEstado == null) {
            return;
        }
        tvMapaEstado.setText(getString(messageRes));
        tvMapaEstado.setVisibility(View.VISIBLE);
    }

    private void ocultarEstado() {
        if (tvMapaEstado == null) {
            return;
        }
        tvMapaEstado.setVisibility(View.GONE);
    }

    private void asegurarOrdenCapas() {
        if (mapView == null) {
            return;
        }

        if (whiteLabelsOverlay != null) {
            mapView.getOverlays().remove(whiteLabelsOverlay);
            mapView.getOverlays().add(whiteLabelsOverlay);
        }
        if (equiposClusterer != null) {
            mapView.getOverlays().remove(equiposClusterer);
            mapView.getOverlays().add(equiposClusterer);
        }
    }

    private void configurarCapaEtiquetasBlancas() {
        if (!isAdded() || mapView == null) {
            return;
        }

        if (whiteLabelsOverlay != null || whiteLabelsProvider != null) {
            return;
        }

        whiteLabelsProvider = new MapTileProviderBasic(requireContext().getApplicationContext(), WHITE_LABELS_TILE_SOURCE);
        whiteLabelsOverlay = new TilesOverlay(whiteLabelsProvider, requireContext().getApplicationContext());
        whiteLabelsOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        whiteLabelsOverlay.setLoadingLineColor(Color.TRANSPARENT);
        whiteLabelsOverlay.setColorFilter(WHITE_LABELS_COLOR_FILTER);
        mapView.getOverlays().add(whiteLabelsOverlay);
    }

    private void ocultarCapaBaseMapa() {
        if (mapView == null || mapView.getOverlayManager() == null || mapView.getOverlayManager().getTilesOverlay() == null) {
            return;
        }

        ColorMatrix transparente = new ColorMatrix(new float[]{
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f
        });

        mapView.getOverlayManager()
                .getTilesOverlay()
                .setColorFilter(new ColorMatrixColorFilter(transparente));
    }

    private void cargarCapasLineasMundo() {
        if (!isAdded() || mapView == null) {
            return;
        }

        worldLoadGeneration++;
        final int generationAtSubmit = worldLoadGeneration;

        ExecutorService executor = asegurarEjecutorCapasMundo();
        try {
            executor.submit(() -> {
                try {
                    List<Overlay> overlays = construirCapasMundo();
                    mainHandler.post(() -> aplicarCapasMundo(overlays, generationAtSubmit));
                } catch (Exception ignored) {
                }
            });
        } catch (RejectedExecutionException rejected) {
            ExecutorService recreated = recrearEjecutorCapasMundo();
            recreated.submit(() -> {
                try {
                    List<Overlay> overlays = construirCapasMundo();
                    mainHandler.post(() -> aplicarCapasMundo(overlays, generationAtSubmit));
                } catch (Exception ignored) {
                }
            });
        }
    }

    private ExecutorService asegurarEjecutorCapasMundo() {
        if (worldOverlayExecutor == null || worldOverlayExecutor.isShutdown() || worldOverlayExecutor.isTerminated()) {
            worldOverlayExecutor = Executors.newSingleThreadExecutor();
        }
        return worldOverlayExecutor;
    }

    private ExecutorService recrearEjecutorCapasMundo() {
        if (worldOverlayExecutor != null) {
            worldOverlayExecutor.shutdownNow();
        }
        worldOverlayExecutor = Executors.newSingleThreadExecutor();
        return worldOverlayExecutor;
    }

    private void aplicarCapasMundo(List<Overlay> overlays, int generationAtSubmit) {
        if (!isAdded() || mapView == null || generationAtSubmit != worldLoadGeneration) {
            return;
        }

        limpiarCapasMundo();
        worldOverlays.addAll(overlays);
        mapView.getOverlays().addAll(overlays);
        asegurarOrdenCapas();
        mapView.invalidate();
    }

    private void limpiarCapasMundo() {
        if (mapView == null || worldOverlays.isEmpty()) {
            return;
        }
        mapView.getOverlays().removeAll(worldOverlays);
        worldOverlays.clear();
    }

    private List<Overlay> construirCapasMundo() throws Exception {
        int colorMar = ContextCompat.getColor(requireContext(), R.color.background_primary);
        int colorRellenoPais = ContextCompat.getColor(requireContext(), R.color.background_secondary);
        int colorBordePais = ContextCompat.getColor(requireContext(), R.color.primary);
        int colorCosta = ContextCompat.getColor(requireContext(), R.color.accent_coral);

        String geoJson = descargarTexto(WORLD_GEOJSON_URL);
        JSONObject root = new JSONObject(geoJson);
        JSONArray features = root.optJSONArray("features");

        List<Overlay> overlays = new ArrayList<>();
        agregarFondoMar(overlays, colorMar);
        if (features == null) {
            return overlays;
        }

        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.optJSONObject(i);
            if (feature == null) {
                continue;
            }

            JSONObject geometry = feature.optJSONObject("geometry");
            if (geometry == null) {
                continue;
            }

            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if (coordinates == null) {
                continue;
            }

            if ("Polygon".equals(type)) {
                agregarPoligonoPaisDesdeCoordenadas(coordinates, overlays, colorRellenoPais, colorBordePais);
            } else if ("MultiPolygon".equals(type)) {
                for (int polygonIndex = 0; polygonIndex < coordinates.length(); polygonIndex++) {
                    JSONArray polygonCoords = coordinates.optJSONArray(polygonIndex);
                    if (polygonCoords == null) {
                        continue;
                    }
                    agregarPoligonoPaisDesdeCoordenadas(polygonCoords, overlays, colorRellenoPais, colorBordePais);
                }
            }
        }

        String coastlineGeoJson = descargarTexto(WORLD_COASTLINE_GEOJSON_URL);
        JSONObject coastlineRoot = new JSONObject(coastlineGeoJson);
        JSONArray coastlineFeatures = coastlineRoot.optJSONArray("features");
        if (coastlineFeatures != null) {
            for (int i = 0; i < coastlineFeatures.length(); i++) {
                JSONObject feature = coastlineFeatures.optJSONObject(i);
                if (feature == null) {
                    continue;
                }

                JSONObject geometry = feature.optJSONObject("geometry");
                if (geometry == null) {
                    continue;
                }

                String type = geometry.optString("type", "");
                JSONArray coordinates = geometry.optJSONArray("coordinates");
                if (coordinates == null) {
                    continue;
                }

                if ("LineString".equals(type)) {
                    agregarLineaCosta(coordinates, overlays, colorCosta);
                } else if ("MultiLineString".equals(type)) {
                    for (int lineIndex = 0; lineIndex < coordinates.length(); lineIndex++) {
                        JSONArray line = coordinates.optJSONArray(lineIndex);
                        if (line == null) {
                            continue;
                        }
                        agregarLineaCosta(line, overlays, colorCosta);
                    }
                }
            }
        }

        return overlays;
    }

    private void agregarFondoMar(List<Overlay> overlays, int colorMar) {
        List<GeoPoint> puntosFondo = new ArrayList<>();
        puntosFondo.add(new GeoPoint(85.0, -180.0));
        puntosFondo.add(new GeoPoint(85.0, 180.0));
        puntosFondo.add(new GeoPoint(-85.0, 180.0));
        puntosFondo.add(new GeoPoint(-85.0, -180.0));

        Polygon fondoMar = new Polygon();
        fondoMar.setPoints(puntosFondo);
        fondoMar.setFillColor(colorMar);
        fondoMar.setStrokeColor(Color.TRANSPARENT);
        fondoMar.setStrokeWidth(0f);
        overlays.add(fondoMar);
    }

    private void agregarPoligonoPaisDesdeCoordenadas(
            JSONArray polygonCoordinates,
            List<Overlay> overlays,
            int countryFillColor,
            int countryBorderColor
    ) {
        JSONArray exteriorRing = polygonCoordinates.optJSONArray(0);
        if (exteriorRing == null || exteriorRing.length() < 3) {
            return;
        }

        List<GeoPoint> points = parsearAnilloGeoPoint(exteriorRing);
        if (points.size() < 3) {
            return;
        }

        Polygon country = new Polygon();
        country.setPoints(points);
        country.setFillColor(countryFillColor);
        country.setStrokeColor(countryBorderColor);
        country.setStrokeWidth(1.8f);
        overlays.add(country);
    }

    private void agregarLineaCosta(JSONArray lineCoordinates, List<Overlay> overlays, int coastlineColor) {
        List<GeoPoint> points = parsearAnilloGeoPoint(lineCoordinates);
        if (points.size() < 2) {
            return;
        }
        Polyline coastline = new Polyline();
        coastline.setPoints(points);
        coastline.setColor(coastlineColor);
        coastline.setWidth(3.8f);
        overlays.add(coastline);
    }

    private List<GeoPoint> parsearAnilloGeoPoint(JSONArray ring) {
        List<GeoPoint> points = new ArrayList<>(ring.length());
        for (int i = 0; i < ring.length(); i++) {
            JSONArray pair = ring.optJSONArray(i);
            if (pair == null || pair.length() < 2) {
                continue;
            }

            double lon = pair.optDouble(0, Double.NaN);
            double lat = pair.optDouble(1, Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                continue;
            }
            points.add(new GeoPoint(lat, lon));
        }
        return points;
    }

    private String descargarTexto(String urlString) throws Exception {
        HttpURLConnection connection = null;
        InputStream inputStream = null;

        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "application/json");

            inputStream = new BufferedInputStream(connection.getInputStream());
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        worldLoadGeneration++;
        equiposLoadGeneration++;

        limpiarCargasEscudosPendientes();
        ligas.clear();

        if (mapView != null) {
            limpiarCapasMundo();

            if (equiposClusterer != null) {
                cerrarInfoWindowsEquipos();
                equiposClusterer.getItems().clear();
                mapView.getOverlays().remove(equiposClusterer);
                equiposClusterer = null;
            }

            if (whiteLabelsOverlay != null) {
                mapView.getOverlays().remove(whiteLabelsOverlay);
                whiteLabelsOverlay.onDetach(mapView);
                whiteLabelsOverlay = null;
            }
            if (whiteLabelsProvider != null) {
                whiteLabelsProvider.detach();
                whiteLabelsProvider = null;
            }
            mapView.onDetach();
            mapView = null;
        }

        grupoChipsLigasMapa = null;
        pbMapaEquipos = null;
        tvMapaEstado = null;

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (worldOverlayExecutor != null) {
            worldOverlayExecutor.shutdownNow();
            worldOverlayExecutor = null;
        }
        if (leagueSyncExecutor != null) {
            leagueSyncExecutor.shutdownNow();
            leagueSyncExecutor = null;
        }
        super.onDestroy();
    }

    private static class LigaItem {
        final String id;
        final String nombre;

        LigaItem(@NonNull String id, @NonNull String nombre) {
            this.id = id;
            this.nombre = nombre;
        }
    }

    private static class EquipoMapaItem {
        final String id;
        final String equipo;
        final String estadio;
        final String ciudad;
        final double latitud;
        final double longitud;
        @Nullable
        final String escudoUrl;

        EquipoMapaItem(
                @NonNull String id,
                @NonNull String equipo,
                @NonNull String estadio,
                @NonNull String ciudad,
                double latitud,
                double longitud,
                @Nullable String escudoUrl
        ) {
            this.id = id;
            this.equipo = equipo;
            this.estadio = estadio;
            this.ciudad = ciudad;
            this.latitud = latitud;
            this.longitud = longitud;
            this.escudoUrl = escudoUrl;
        }
    }

    private static class CoordenadaGeo {
        final double latitud;
        final double longitud;

        CoordenadaGeo(double latitud, double longitud) {
            this.latitud = latitud;
            this.longitud = longitud;
        }
    }

    private static class CapitalInfo {
        final String nombre;
        final double latitud;
        final double longitud;

        CapitalInfo(@NonNull String nombre, double latitud, double longitud) {
            this.nombre = nombre;
            this.latitud = latitud;
            this.longitud = longitud;
        }
    }

    private static class ApiTeamItem {
        final int id;
        final String name;
        final String venue;
        final String city;
        final String areaName;
        final String crest;

        ApiTeamItem(int id,
                    @NonNull String name,
                    @NonNull String venue,
                    @NonNull String city,
                    @NonNull String areaName,
                    @NonNull String crest) {
            this.id = id;
            this.name = name;
            this.venue = venue;
            this.city = city;
            this.areaName = areaName;
            this.crest = crest;
        }
    }

    private static class ApiResult {
        final boolean ok;
        final int code;
        @NonNull
        final String body;

        ApiResult(boolean ok, int code, @NonNull String body) {
            this.ok = ok;
            this.code = code;
            this.body = body;
        }
    }

    private static class SyncResult {
        final boolean success;
        @StringRes
        final int errorMessageRes;

        private SyncResult(boolean success, @StringRes int errorMessageRes) {
            this.success = success;
            this.errorMessageRes = errorMessageRes;
        }

        @NonNull
        static SyncResult success() {
            return new SyncResult(true, 0);
        }

        @NonNull
        static SyncResult error(@StringRes int errorRes) {
            return new SyncResult(false, errorRes);
        }
    }
}
