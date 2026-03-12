package com.example.soccerexplorer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoticiasFragment extends Fragment {

    private static final int NEWS_PAGE_SIZE = 20;
    private static final long CACHE_DURATION_MS = 60000;

    private static String lastTeamCached = "";
    private static long lastFetchTimeMs = 0L;
    private static final List<NoticiasAdapter.NoticiaItem> cachedNoticias = new ArrayList<>();

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private TextView tvEquipoActual;
    private TextView tvNoticiasError;
    private ProgressBar progressNoticias;
    private NoticiasAdapter noticiasAdapter;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_noticias, container, false);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        tvEquipoActual = view.findViewById(R.id.tvEquipoActual);
        tvNoticiasError = view.findViewById(R.id.tvNoticiasError);
        progressNoticias = view.findViewById(R.id.progressNoticias);
        RecyclerView rvNoticias = view.findViewById(R.id.rvNoticias);

        rvNoticias.setLayoutManager(new LinearLayoutManager(requireContext()));
        noticiasAdapter = new NoticiasAdapter(this::abrirDetalleNoticia);
        rvNoticias.setAdapter(noticiasAdapter);

        cargarNoticiasEquipoFavorito();
        return view;
    }

    private void cargarNoticiasEquipoFavorito() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            mostrarError(getString(R.string.news_error_no_user));
            return;
        }

        mostrarLoading(true);

        firestore.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String equipo = documentSnapshot.getString("equipoFavoritoNombre");
                    if (equipo == null || equipo.trim().isEmpty()) {
                        mostrarLoading(false);
                        mostrarError(getString(R.string.news_error_no_team));
                        return;
                    }

                    tvEquipoActual.setText(getString(R.string.news_team_label, equipo));
                    pedirNoticiasApi(equipo);
                })
                .addOnFailureListener(e -> {
                    mostrarLoading(false);
                    mostrarError(getString(R.string.news_error_team_load));
                });
    }

    private void pedirNoticiasApi(@NonNull String equipo) {
        if (BuildConfig.NEWS_API_KEY == null || BuildConfig.NEWS_API_KEY.trim().isEmpty()) {
            mostrarLoading(false);
            mostrarError(getString(R.string.news_error_missing_key));
            return;
        }

        if (equipo.equalsIgnoreCase(lastTeamCached)
                && !cachedNoticias.isEmpty()
                && (System.currentTimeMillis() - lastFetchTimeMs) < CACHE_DURATION_MS) {
            mostrarLoading(false);
            tvNoticiasError.setVisibility(View.GONE);
            noticiasAdapter.actualizarNoticias(new ArrayList<>(cachedNoticias));
            return;
        }

        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String query = URLEncoder.encode(equipo, StandardCharsets.UTF_8.name());
                String endpoint = "https://newsapi.org/v2/everything?q=" + query
                        + "&language=es&pageSize=" + NEWS_PAGE_SIZE + "&sortBy=publishedAt";

                URL url = new URL(endpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("X-Api-Key", BuildConfig.NEWS_API_KEY);
                connection.setRequestProperty("User-Agent", "SoccerExplorer-Android");
                connection.setRequestProperty("Accept", "application/json");

                int responseCode = connection.getResponseCode();
                InputStream inputStream;
                if (responseCode >= 200 && responseCode < 300) {
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = connection.getErrorStream();
                }

                String response = leerInputStream(inputStream);
                if (responseCode < 200 || responseCode >= 300) {
                    postError(obtenerMensajeErrorApi(responseCode, response));
                    return;
                }

                List<NoticiasAdapter.NoticiaItem> noticias = parsearNoticias(response);
                actualizarCache(equipo, noticias);
                postNoticias(noticias);
            } catch (Exception e) {
                postError(getString(R.string.news_error_network));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
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
    private List<NoticiasAdapter.NoticiaItem> parsearNoticias(@NonNull String response) throws Exception {
        List<NoticiasAdapter.NoticiaItem> noticias = new ArrayList<>();
        JSONObject root = new JSONObject(response);
        JSONArray articles = root.optJSONArray("articles");
        if (articles == null) {
            return noticias;
        }

        for (int i = 0; i < articles.length(); i++) {
            JSONObject article = articles.optJSONObject(i);
            if (article == null) {
                continue;
            }

            String titulo = article.optString("title", "").trim();
            if (titulo.isEmpty()) {
                continue;
            }

            JSONObject source = article.optJSONObject("source");
            String fuente = source != null ? source.optString("name", "") : "";
            String fecha = article.optString("publishedAt", "");
            String fuenteYFecha = fuente;
            if (!fecha.isEmpty()) {
                fuenteYFecha = fuenteYFecha.isEmpty() ? fecha : fuente + " - " + fecha;
            }
            if (fuenteYFecha.isEmpty()) {
                fuenteYFecha = getString(R.string.news_unknown_source);
            }

            String imageUrl = article.optString("urlToImage", null);
            if (imageUrl != null && imageUrl.trim().isEmpty()) {
                imageUrl = null;
            }

            String url = article.optString("url", null);
            if (url != null && url.trim().isEmpty()) {
                url = null;
            }

            noticias.add(new NoticiasAdapter.NoticiaItem(titulo, fuenteYFecha, imageUrl, url));
        }

        return noticias;
    }

    @NonNull
    private String obtenerMensajeErrorApi(int responseCode, @NonNull String responseBody) {
        String apiMessage = "";
        try {
            JSONObject errorJson = new JSONObject(responseBody);
            apiMessage = errorJson.optString("message", "").trim();
        } catch (Exception ignored) {
        }

        if (responseCode == 429) {
            return getString(R.string.news_error_rate_limit);
        }

        if (!apiMessage.isEmpty()) {
            return getString(R.string.news_error_api_detailed, responseCode, apiMessage);
        }

        return getString(R.string.news_error_api);
    }

    private void actualizarCache(@NonNull String equipo, @NonNull List<NoticiasAdapter.NoticiaItem> noticias) {
        synchronized (cachedNoticias) {
            lastTeamCached = equipo;
            lastFetchTimeMs = System.currentTimeMillis();
            cachedNoticias.clear();
            cachedNoticias.addAll(noticias);
        }
    }

    private void postNoticias(@NonNull List<NoticiasAdapter.NoticiaItem> noticias) {
        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(() -> {
            mostrarLoading(false);
            if (noticias.isEmpty()) {
                mostrarError(getString(R.string.news_empty));
                noticiasAdapter.actualizarNoticias(new ArrayList<>());
                return;
            }

            tvNoticiasError.setVisibility(View.GONE);
            noticiasAdapter.actualizarNoticias(noticias);
        });
    }

    private void postError(@NonNull String message) {
        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(() -> {
            mostrarLoading(false);
            mostrarError(message);
            noticiasAdapter.actualizarNoticias(new ArrayList<>());
        });
    }

    private void mostrarLoading(boolean loading) {
        progressNoticias.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            tvNoticiasError.setVisibility(View.GONE);
        }
    }

    private void mostrarError(@NonNull String message) {
        tvNoticiasError.setText(message);
        tvNoticiasError.setVisibility(View.VISIBLE);
    }

    private void abrirDetalleNoticia(@NonNull NoticiasAdapter.NoticiaItem noticia) {
        if (noticia.url == null || noticia.url.trim().isEmpty()) {
            Toast.makeText(requireContext(), R.string.news_error_no_url, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(requireContext(), NewsWebViewActivity.class);
        intent.putExtra(NewsWebViewActivity.EXTRA_NEWS_URL, noticia.url);
        intent.putExtra(NewsWebViewActivity.EXTRA_NEWS_TITLE, noticia.titulo);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executorService.shutdownNow();
    }
}
