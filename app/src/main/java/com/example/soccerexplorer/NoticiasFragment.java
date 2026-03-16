package com.example.soccerexplorer;

import android.app.Activity;
import android.content.Context;
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
import androidx.annotation.StringRes;
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
import java.util.concurrent.RejectedExecutionException;

public class NoticiasFragment extends Fragment {

    private static final int NEWS_PAGE_SIZE = 20;
    private static final long CACHE_DURATION_MS = 60000;
    private static final String CLAUSULA_FUTBOL = "(futbol OR fútbol OR balonpie OR balompie OR club OR liga OR partido OR jornada OR gol OR entrenador OR delantero OR defensa OR portero OR laliga OR \"la liga\" OR champions OR \"copa del rey\")";
    private static final String CLAUSULA_EXCLUIR_NO_FUTBOL = "NOT (basket OR baloncesto OR basquet OR basketball OR nba OR acb OR euroliga OR eurocup OR wnba)";
    private static final String[] PALABRAS_FUTBOL = {
            "futbol", "fútbol", "balonpie", "balompie", "club", "liga", "partido", "jornada",
            "gol", "entrenador", "delantero", "defensa", "portero", "laliga", "la liga",
            "champions", "copa del rey", "estadio", "derbi"
    };
    private static final String[] PALABRAS_NO_FUTBOL = {
            "basket", "baloncesto", "basquet", "basketball", "nba", "acb", "euroliga",
            "eurocup", "wnba", "liga endesa"
    };

    private static String lastTeamCached = "";
    private static long lastFetchTimeMs = 0L;
    private static final List<NoticiasAdapter.NoticiaItem> cachedNoticias = new ArrayList<>();

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private TextView tvEquipoActual;
    private TextView tvNoticiasError;
    private ProgressBar progressNoticias;
    private NoticiasAdapter noticiasAdapter;
    private Context appContext;

    private ExecutorService executorService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_noticias, container, false);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        appContext = requireContext().getApplicationContext();
        asegurarEjecutor();

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
            mostrarError(obtenerTextoRecurso(R.string.news_error_no_user));
            return;
        }

        mostrarLoading(true);

        firestore.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded() || getView() == null || tvEquipoActual == null) {
                        return;
                    }

                    String equipo = documentSnapshot.getString("equipoFavoritoNombre");
                    if (equipo == null || equipo.trim().isEmpty()) {
                        mostrarLoading(false);
                        mostrarError(obtenerTextoRecurso(R.string.news_error_no_team));
                        return;
                    }

                    tvEquipoActual.setText(obtenerTextoRecurso(R.string.news_team_label, equipo));
                    pedirNoticiasApi(equipo);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getView() == null) {
                        return;
                    }
                    mostrarLoading(false);
                    mostrarError(obtenerTextoRecurso(R.string.news_error_team_load));
                });
    }

    private void pedirNoticiasApi(@NonNull String equipo) {
        if (BuildConfig.NEWS_API_KEY == null || BuildConfig.NEWS_API_KEY.trim().isEmpty()) {
            mostrarLoading(false);
            mostrarError(obtenerTextoRecurso(R.string.news_error_missing_key));
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

        ExecutorService executor = asegurarEjecutor();
        Runnable requestTask = () -> {
            try {
                String equipoLimpio = equipo.trim();
                String queryEquipo = construirQueryEquipo(equipoLimpio);
                String queryFutbol = "(" + queryEquipo + ") AND "
                        + CLAUSULA_FUTBOL + " " + CLAUSULA_EXCLUIR_NO_FUTBOL;
                String querySoloEquipo = "(" + queryEquipo + ") " + CLAUSULA_EXCLUIR_NO_FUTBOL;

                List<NoticiasAdapter.NoticiaItem> noticias = solicitarNoticiasConFallback(
                        equipoLimpio,
                        queryFutbol,
                        querySoloEquipo
                );
                if (noticias == null) {
                    return;
                }

                actualizarCache(equipo, noticias);
                publicarNoticias(noticias);
            } catch (Exception e) {
                publicarError(obtenerTextoRecurso(R.string.news_error_network));
            }
        };

        try {
            executor.execute(requestTask);
        } catch (RejectedExecutionException ignored) {
            asegurarEjecutor().execute(requestTask);
        }
    }

    @Nullable
    private List<NoticiasAdapter.NoticiaItem> solicitarNoticiasConFallback(@NonNull String equipo,
                                                                            @NonNull String queryFutbol,
                                                                            @NonNull String querySoloEquipo) throws Exception {
        ApiResponse intentoTopHeadlines = ejecutarRequest(construirUrlTopHeadlines(queryFutbol));
        if (!intentoTopHeadlines.isOk()) {
            publicarError(obtenerMensajeErrorApi(intentoTopHeadlines.responseCode, intentoTopHeadlines.body));
            return null;
        }

        List<NoticiasAdapter.NoticiaItem> noticiasTopHeadlines = parsearNoticias(intentoTopHeadlines.body, equipo);
        if (!noticiasTopHeadlines.isEmpty()) {
            return noticiasTopHeadlines;
        }

        ApiResponse intentoTopHeadlinesSoloEquipo = ejecutarRequest(construirUrlTopHeadlines(querySoloEquipo));
        if (!intentoTopHeadlinesSoloEquipo.isOk()) {
            publicarError(obtenerMensajeErrorApi(intentoTopHeadlinesSoloEquipo.responseCode, intentoTopHeadlinesSoloEquipo.body));
            return null;
        }

        List<NoticiasAdapter.NoticiaItem> noticiasTopHeadlinesSoloEquipo = parsearNoticias(intentoTopHeadlinesSoloEquipo.body, equipo);
        if (!noticiasTopHeadlinesSoloEquipo.isEmpty()) {
            return noticiasTopHeadlinesSoloEquipo;
        }

        ApiResponse intentoEverything = ejecutarRequest(construirUrlEverything(queryFutbol));
        if (!intentoEverything.isOk()) {
            publicarError(obtenerMensajeErrorApi(intentoEverything.responseCode, intentoEverything.body));
            return null;
        }

        return parsearNoticias(intentoEverything.body, equipo);
    }

    @NonNull
    private String construirQueryEquipo(@NonNull String equipo) {
        String equipoNormalizado = equipo.trim().toLowerCase();
        switch (equipoNormalizado) {
            case "valencia":
            case "valencia cf":
                return "\"Valencia CF\" OR \"Valencia C.F.\" OR \"Valencia Club de Futbol\" OR \"Valencia Club de Fútbol\" OR valencianista OR Valencia";
            case "levante":
            case "levante ud":
                return "\"Levante UD\" OR \"Levante U.D.\" OR \"Levante Union Deportiva\" OR \"Levante Unión Deportiva\" OR granota OR Levante";
            case "getafe":
            case "getafe cf":
                return "\"Getafe CF\" OR \"Getafe C.F.\" OR \"Getafe Club de Futbol\" OR \"Getafe Club de Fútbol\" OR Getafe";
            case "barcelona":
            case "fc barcelona":
                return "\"FC Barcelona\" OR Barcelona OR Barça OR Barca";
            case "atletico madrid":
            case "atletico de madrid":
            case "atlético de madrid":
                return "\"Atletico de Madrid\" OR \"Atlético de Madrid\" OR \"Atletico Madrid\" OR colchonero";
            default:
                return "\"" + equipo + "\"";
        }
    }

    @NonNull
    private String construirUrlTopHeadlines(@NonNull String query) throws Exception {
        String queryEncoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        return "https://newsapi.org/v2/top-headlines?country=es&category=sports&pageSize="
                + NEWS_PAGE_SIZE + "&q=" + queryEncoded;
    }

    @NonNull
    private String construirUrlEverything(@NonNull String query) throws Exception {
        String queryEncoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        return "https://newsapi.org/v2/everything?language=es&pageSize=" + NEWS_PAGE_SIZE
                + "&sortBy=relevancy&searchIn=title,description&q=" + queryEncoded;
    }

    @NonNull
    private ApiResponse ejecutarRequest(@NonNull String endpoint) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("X-Api-Key", BuildConfig.NEWS_API_KEY);
            connection.setRequestProperty("User-Agent", "SoccerExplorer-Android");
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            InputStream inputStream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String body = leerInputStream(inputStream);
            return new ApiResponse(responseCode, body);
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
    private List<NoticiasAdapter.NoticiaItem> parsearNoticias(@NonNull String response,
                                                              @NonNull String equipo) throws Exception {
        List<NoticiasAdapter.NoticiaItem> noticias = new ArrayList<>();
        List<String> terminosEquipo = obtenerTerminosEquipoFiltro(equipo);
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

            String descripcion = article.optString("description", "");
            String contenido = article.optString("content", "");
            if (!esNoticiaRelacionadaConEquipoYFutbol(titulo, descripcion, contenido, terminosEquipo)) {
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
                fuenteYFecha = obtenerTextoRecurso(R.string.news_unknown_source);
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
    private List<String> obtenerTerminosEquipoFiltro(@NonNull String equipo) {
        String equipoNormalizado = equipo.trim().toLowerCase();
        List<String> terminos = new ArrayList<>();

        switch (equipoNormalizado) {
            case "valencia":
            case "valencia cf":
                terminos.add("valencia cf");
                terminos.add("valencia c.f");
                terminos.add("valencia club de futbol");
                terminos.add("valencia club de fútbol");
                terminos.add("valencianista");
                terminos.add("valencia");
                break;
            case "levante":
            case "levante ud":
                terminos.add("levante ud");
                terminos.add("levante u.d");
                terminos.add("levante union deportiva");
                terminos.add("levante unión deportiva");
                terminos.add("granota");
                terminos.add("levante");
                break;
            case "getafe":
            case "getafe cf":
                terminos.add("getafe cf");
                terminos.add("getafe c.f");
                terminos.add("getafe club de futbol");
                terminos.add("getafe club de fútbol");
                terminos.add("getafe");
                break;
            case "barcelona":
            case "fc barcelona":
                terminos.add("fc barcelona");
                terminos.add("barcelona");
                terminos.add("barça");
                terminos.add("barca");
                break;
            case "atletico madrid":
            case "atletico de madrid":
            case "atlético de madrid":
                terminos.add("atletico de madrid");
                terminos.add("atlético de madrid");
                terminos.add("atletico madrid");
                terminos.add("colchonero");
                break;
            default:
                terminos.add(equipoNormalizado);
                break;
        }

        return terminos;
    }

    private boolean esNoticiaRelacionadaConEquipoYFutbol(@NonNull String titulo,
                                                          @NonNull String descripcion,
                                                          @NonNull String contenido,
                                                          @NonNull List<String> terminosEquipo) {
        String textoCompleto = (titulo + " " + descripcion + " " + contenido).toLowerCase();

        if (!contieneAlgunTermino(textoCompleto, terminosEquipo)) {
            return false;
        }

        if (!contieneAlgunTermino(textoCompleto, PALABRAS_FUTBOL)) {
            return false;
        }

        return !contieneAlgunTermino(textoCompleto, PALABRAS_NO_FUTBOL);
    }

    private boolean contieneAlgunTermino(@NonNull String texto, @NonNull List<String> terminos) {
        for (String termino : terminos) {
            if (texto.contains(termino)) {
                return true;
            }
        }
        return false;
    }

    private boolean contieneAlgunTermino(@NonNull String texto, @NonNull String[] terminos) {
        for (String termino : terminos) {
            if (texto.contains(termino)) {
                return true;
            }
        }
        return false;
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
            return obtenerTextoRecurso(R.string.news_error_rate_limit);
        }

        if (!apiMessage.isEmpty()) {
            return obtenerTextoRecurso(R.string.news_error_api_detailed, responseCode, apiMessage);
        }

        return obtenerTextoRecurso(R.string.news_error_api);
    }

    private void actualizarCache(@NonNull String equipo, @NonNull List<NoticiasAdapter.NoticiaItem> noticias) {
        synchronized (cachedNoticias) {
            lastTeamCached = equipo;
            lastFetchTimeMs = System.currentTimeMillis();
            cachedNoticias.clear();
            cachedNoticias.addAll(noticias);
        }
    }

    private void publicarNoticias(@NonNull List<NoticiasAdapter.NoticiaItem> noticias) {
        if (!isAdded()) {
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        activity.runOnUiThread(() -> {
            if (!isAdded() || getView() == null || tvNoticiasError == null || progressNoticias == null || noticiasAdapter == null) {
                return;
            }
            mostrarLoading(false);
            if (noticias.isEmpty()) {
                mostrarError(obtenerTextoRecurso(R.string.news_empty));
                noticiasAdapter.actualizarNoticias(new ArrayList<>());
                return;
            }

            tvNoticiasError.setVisibility(View.GONE);
            noticiasAdapter.actualizarNoticias(noticias);
        });
    }

    private void publicarError(@NonNull String message) {
        if (!isAdded()) {
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        activity.runOnUiThread(() -> {
            if (!isAdded() || getView() == null || tvNoticiasError == null || progressNoticias == null || noticiasAdapter == null) {
                return;
            }
            mostrarLoading(false);
            mostrarError(message);
            noticiasAdapter.actualizarNoticias(new ArrayList<>());
        });
    }

    private ExecutorService asegurarEjecutor() {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            executorService = Executors.newSingleThreadExecutor();
        }
        return executorService;
    }

    @NonNull
    private String obtenerTextoRecurso(@StringRes int resId) {
        Context context = appContext;
        if (context == null && getContext() != null) {
            context = getContext().getApplicationContext();
            appContext = context;
        }
        if (context == null) {
            return "";
        }
        return context.getString(resId);
    }

    @NonNull
    private String obtenerTextoRecurso(@StringRes int resId, Object... args) {
        Context context = appContext;
        if (context == null && getContext() != null) {
            context = getContext().getApplicationContext();
            appContext = context;
        }
        if (context == null) {
            return "";
        }
        return context.getString(resId, args);
    }

    private void mostrarLoading(boolean loading) {
        if (progressNoticias == null || tvNoticiasError == null) {
            return;
        }
        progressNoticias.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            tvNoticiasError.setVisibility(View.GONE);
        }
    }

    private void mostrarError(@NonNull String message) {
        if (tvNoticiasError == null) {
            return;
        }
        tvNoticiasError.setText(message);
        tvNoticiasError.setVisibility(View.VISIBLE);
    }

    private void abrirDetalleNoticia(@NonNull NoticiasAdapter.NoticiaItem noticia) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        if (noticia.url == null || noticia.url.trim().isEmpty()) {
            Toast.makeText(context, R.string.news_error_no_url, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(context, NewsWebViewActivity.class);
        intent.putExtra(NewsWebViewActivity.EXTRA_NEWS_URL, noticia.url);
        intent.putExtra(NewsWebViewActivity.EXTRA_NEWS_TITLE, noticia.titulo);
        startActivity(intent);
    }

    private static class ApiResponse {
        final int responseCode;
        @NonNull
        final String body;

        ApiResponse(int responseCode, @NonNull String body) {
            this.responseCode = responseCode;
            this.body = body;
        }

        boolean isOk() {
            return responseCode >= 200 && responseCode < 300;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        tvEquipoActual = null;
        tvNoticiasError = null;
        progressNoticias = null;
    }
}
