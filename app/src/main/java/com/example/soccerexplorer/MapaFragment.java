package com.example.soccerexplorer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

public class MapaFragment extends Fragment {

    private static final ITileSource CARTO_DARK = new XYTileSource(
            "CartoDark",
            1,
            20,
            256,
            ".png",
            new String[]{
                    "https://a.basemaps.cartocdn.com/dark_all/",
                    "https://b.basemaps.cartocdn.com/dark_all/",
                    "https://c.basemaps.cartocdn.com/dark_all/"
            },
            "Map tiles by CARTO, data by OpenStreetMap contributors"
    );

    private MapView mapView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        View view = inflater.inflate(R.layout.fragment_mapa, container, false);
        mapView = view.findViewById(R.id.osmMapView);

        mapView.setTileSource(CARTO_DARK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(5.5);
        mapView.getController().setCenter(new GeoPoint(40.4168, -3.7038));
        mapView.setBuiltInZoomControls(true);
        mapView.setTilesScaledToDpi(true);

        return view;
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
}
