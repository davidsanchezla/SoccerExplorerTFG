package com.example.soccerexplorer;

import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MapaFragment extends Fragment {

    private static final String WORLD_GEOJSON_URL = "https://raw.githubusercontent.com/johan/world.geo.json/master/countries.geo.json";
    private static final String WORLD_COASTLINE_GEOJSON_URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_coastline.geojson";

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

    private MapView mapView;
    private MapTileProviderBasic whiteLabelsProvider;
    private TilesOverlay whiteLabelsOverlay;
    private ExecutorService worldOverlayExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Overlay> worldOverlays = new ArrayList<>();
    private int worldLoadGeneration = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        View view = inflater.inflate(R.layout.fragment_mapa, container, false);
        mapView = view.findViewById(R.id.osmMapView);

        mapView.setTileSource(DARK_NO_LABELS_TILE_SOURCE);
        mapView.setMultiTouchControls(true);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(8.5);
        mapView.getController().setZoom(5.5);
        mapView.getController().setCenter(new GeoPoint(40.4168, -3.7038));
        mapView.setBuiltInZoomControls(true);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setScrollableAreaLimitDouble(new BoundingBox(85.0, 180.0, -85.0, -180.0));

        int seaColor = ContextCompat.getColor(requireContext(), R.color.background_primary);
        mapView.setBackgroundColor(seaColor);
        ocultarCapaBaseMapa();

        configurarCapaEtiquetasBlancas();
        cargarCapasLineasMundo();

        return view;
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

        if (whiteLabelsOverlay != null) {
            mapView.getOverlays().remove(whiteLabelsOverlay);
            mapView.getOverlays().add(whiteLabelsOverlay);
        }

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
        if (mapView != null) {
            limpiarCapasMundo();
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

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (worldOverlayExecutor != null) {
            worldOverlayExecutor.shutdownNow();
            worldOverlayExecutor = null;
        }
        super.onDestroy();
    }
}
