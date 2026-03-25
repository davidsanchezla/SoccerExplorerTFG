package com.example.soccerexplorer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TeamDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_COMPETITION_CODE = "competition_code";
    public static final String EXTRA_TEAM_NAME = "team_name";

    private static final String API_BASE = "https://api.football-data.org/v4";

    private ProgressBar pbTeamDetails;
    private TextView tvTeamDetailsError;
    private View layoutTeamDetailsContent;
    private ImageView ivTeamCrest;
    private TextView tvTeamName;
    private TextView tvTeamShortName;
    private TextView tvTeamTla;
    private TextView tvTeamVenue;
    private TextView tvTeamAddress;
    private TextView tvTeamWebsite;
    private TextView tvTeamFounded;
    private TextView tvTeamClubColors;
    private TextView tvTeamArea;
    private TextView tvTeamRunningCompetitions;
    private TextView tvTeamStaff;
    private TextView tvTeamSquad;
    private RecyclerView rvTeamSquad;
    private TeamSquadAdapter squadAdapter;

    private String competitionCode;
    private String teamName;

    // Cache para listas de equipos por competición
    private static class CachedTeams {
        final String json;
        final long timestamp;

        CachedTeams(String json, long timestamp) {
            this.json = json;
            this.timestamp = timestamp;
        }
    }

    private static final Map<String, CachedTeams> teamsCache = new ConcurrentHashMap<>();
    private static final long TEAMS_CACHE_TTL_MS = 10L * 60L * 1000L; // 10 minutos

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_details);

        pbTeamDetails = findViewById(R.id.pbTeamDetails);
        tvTeamDetailsError = findViewById(R.id.tvTeamDetailsError);
        layoutTeamDetailsContent = findViewById(R.id.layoutTeamDetailsContent);
        ivTeamCrest = findViewById(R.id.ivTeamCrest);
        tvTeamName = findViewById(R.id.tvTeamName);
        tvTeamShortName = findViewById(R.id.tvTeamShortName);
        tvTeamTla = findViewById(R.id.tvTeamTla);
        tvTeamVenue = findViewById(R.id.tvTeamVenue);
        tvTeamAddress = findViewById(R.id.tvTeamAddress);
        tvTeamWebsite = findViewById(R.id.tvTeamWebsite);
        tvTeamFounded = findViewById(R.id.tvTeamFounded);
        tvTeamClubColors = findViewById(R.id.tvTeamClubColors);
        tvTeamArea = findViewById(R.id.tvTeamArea);
        tvTeamRunningCompetitions = findViewById(R.id.tvTeamRunningCompetitions);
        tvTeamStaff = findViewById(R.id.tvTeamStaff);
        tvTeamSquad = findViewById(R.id.tvTeamSquad);
        rvTeamSquad = findViewById(R.id.rvTeamSquad);
        rvTeamSquad.setLayoutManager(new LinearLayoutManager(this));
        squadAdapter = new TeamSquadAdapter();
        rvTeamSquad.setAdapter(squadAdapter);

        Intent intent = getIntent();
        competitionCode = intent.getStringExtra(EXTRA_COMPETITION_CODE);
        teamName = intent.getStringExtra(EXTRA_TEAM_NAME);

        if (competitionCode == null || teamName == null) {
            showError("Faltan parametros necesarios.");
            return;
        }

        setTitle(teamName);
        cargarDetallesEquipo();
    }

    private void cargarDetallesEquipo() {
        mostrarCarga(true);
        ocultarError();
        ocultarContenido();

        new Thread(() -> {
            // Paso 1: Buscar el ID del equipo en la competición
            int teamId = buscarEquipoPorNombre(competitionCode, teamName);
            if (teamId == -1) {
                runOnUiThread(() -> showError("Equipo no encontrado en la competición.\n\n" + lastDebugInfo));
                return;
            }

            // Paso 2: Obtener detalles del equipo
            String json = obtenerDetallesEquipo(teamId);
            if (json == null) {
                runOnUiThread(() -> showError("Error obteniendo detalles del equipo."));
                return;
            }

            runOnUiThread(() -> parsearYMostrarDatos(json));
        }).start();
    }

    private int buscarEquipoPorNombre(String competitionCode, String teamName) {
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("Buscando: '").append(teamName).append("' en competición ").append(competitionCode).append("\n");
        
        try {
            // Usar método con cache en lugar de llamada directa
            String json = obtenerListaEquipos(competitionCode);
            if (json == null) {
                debugInfo.append("Error: no se pudo obtener lista de equipos.\n");
                lastDebugInfo = debugInfo.toString();
                return -1;
            }

            JSONObject root = new JSONObject(json);
            JSONArray teams = root.getJSONArray("teams");
            
            // Estrategia 1: coincidencia exacta normalizada simple
            String nombreSimple = normalizarSimple(teamName);
            debugInfo.append("Nombre simple Firestore: '").append(nombreSimple).append("'\n");
            
            for (int i = 0; i < teams.length(); i++) {
                JSONObject team = teams.getJSONObject(i);
                String apiName = team.getString("name");
                String apiSimple = normalizarSimple(apiName);
                if (apiSimple.equals(nombreSimple)) {
                    debugInfo.append("Coincidencia exacta simple: '").append(apiName).append("'\n");
                    lastDebugInfo = debugInfo.toString();
                    return team.getInt("id");
                }
            }
            
            // Estrategia 2: coincidencia normalizada agresiva
            String nombreAgresivo = normalizarAgresiva(teamName);
            debugInfo.append("Nombre agresivo Firestore: '").append(nombreAgresivo).append("'\n");
            
            for (int i = 0; i < teams.length(); i++) {
                JSONObject team = teams.getJSONObject(i);
                String apiName = team.getString("name");
                String apiAgresivo = normalizarAgresiva(apiName);
                if (apiAgresivo.equals(nombreAgresivo)) {
                    debugInfo.append("Coincidencia agresiva: '").append(apiName).append("'\n");
                    lastDebugInfo = debugInfo.toString();
                    return team.getInt("id");
                }
            }
            
            // Estrategia 3: por shortName y tla
            for (int i = 0; i < teams.length(); i++) {
                JSONObject team = teams.getJSONObject(i);
                String shortName = team.optString("shortName", "");
                String tla = team.optString("tla", "");
                String shortSimple = normalizarSimple(shortName);
                
                if (shortSimple.equals(nombreSimple) || 
                    tla.equalsIgnoreCase(teamName.toUpperCase(Locale.ROOT))) {
                    debugInfo.append("Coincidencia por shortName/tla: '").append(team.getString("name")).append("' (short: ").append(shortName).append(", tla: ").append(tla).append(")\n");
                    lastDebugInfo = debugInfo.toString();
                    return team.getInt("id");
                }
            }
            
            // Si no encontró, construir mensaje de depuración con todos los equipos
            debugInfo.append("No se encontró coincidencia. Equipos disponibles (").append(teams.length()).append("):\n");
            for (int i = 0; i < teams.length(); i++) {
                JSONObject team = teams.getJSONObject(i);
                debugInfo.append("  - ").append(team.getString("name"))
                        .append(" (simple: ").append(normalizarSimple(team.getString("name")))
                        .append(", agresivo: ").append(normalizarAgresiva(team.getString("name")))
                        .append(")\n");
            }
            lastDebugInfo = debugInfo.toString();
            
        } catch (Exception e) {
            debugInfo.append("Excepción: ").append(e.getMessage()).append("\n");
            lastDebugInfo = debugInfo.toString();
            e.printStackTrace();
        }
        return -1;
    }
    
    private String lastDebugInfo = "";

    @NonNull
    private String normalizarSimple(@NonNull String input) {
        String normalized = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "").trim();
    }

    @NonNull
    private String normalizarAgresiva(@NonNull String input) {
        String simple = normalizarSimple(input);
        // Eliminar artículos y sufijos comunes (incluyendo "club" y "real")
        return simple
                .replaceAll("\\b(el|la|los|las|de|del|en|y|fc|cf|sc|ac|afc|cfc|cd|ud|sad|club|real)\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String obtenerListaEquipos(String competitionCode) {
        // Verificar si existe cache válida
        CachedTeams cached = teamsCache.get(competitionCode);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.timestamp) <= TEAMS_CACHE_TTL_MS) {
            return cached.json;
        }

        // No hay cache o expiró, hacer petición HTTP
        String path = "/competitions/" + competitionCode + "/teams";
        String json = ejecutarGet(path);
        if (json != null) {
            // Almacenar en cache
            teamsCache.put(competitionCode, new CachedTeams(json, now));
        }
        return json;
    }

    @NonNull
    private String normalizarNombre(@NonNull String input) {
        return normalizarSimple(input);
    }

    private String obtenerDetallesEquipo(int teamId) {
        try {
            String path = "/teams/" + teamId;
            return ejecutarGet(path);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private String ejecutarGet(@NonNull String pathAndQuery) {
        if (BuildConfig.FOOTBALL_DATA_API_KEY == null || BuildConfig.FOOTBALL_DATA_API_KEY.trim().isEmpty()) {
            return null;
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
                return body;
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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

    private void parsearYMostrarDatos(String json) {
        try {
            JSONObject team = new JSONObject(json);

            // Información básica
            String name = team.optString("name", "Desconocido");
            String shortName = team.optString("shortName", "");
            String tla = team.optString("tla", "");
            String crestUrl = team.optString("crest", null);
            String venue = team.optString("venue", "");
            String address = team.optString("address", "");
            String website = team.optString("website", "");
            int founded = team.optInt("founded", 0);
            String clubColors = team.optString("clubColors", "");

            // Área
            JSONObject area = team.optJSONObject("area");
            String areaName = area != null ? area.optString("name", "") : "";

            // Competiciones actuales
            JSONArray runningCompetitions = team.optJSONArray("runningCompetitions");
            StringBuilder competitionsStr = new StringBuilder();
            if (runningCompetitions != null) {
                for (int i = 0; i < runningCompetitions.length(); i++) {
                    JSONObject comp = runningCompetitions.getJSONObject(i);
                    competitionsStr.append(comp.optString("name", ""));
                    if (i < runningCompetitions.length() - 1) competitionsStr.append(", ");
                }
            }

            // Staff
            JSONArray staff = team.optJSONArray("staff");
            int staffCount = staff != null ? staff.length() : 0;

            // Plantilla (squad)
            JSONArray squad = team.optJSONArray("squad");
            int squadCount = squad != null ? squad.length() : 0;
            List<Player> players = new ArrayList<>();
            if (squad != null) {
                for (int i = 0; i < squad.length(); i++) {
                    JSONObject playerJson = squad.getJSONObject(i);
                    String playerName = playerJson.optString("name", "");
                    String position = playerJson.optString("position", "");
                    String nationality = playerJson.optString("nationality", "");
                    players.add(new Player(playerName, position, nationality));
                }
            }

            // Mostrar datos
            tvTeamName.setText(name);
            if (!shortName.isEmpty()) {
                tvTeamShortName.setText(shortName);
                tvTeamShortName.setVisibility(View.VISIBLE);
            } else {
                tvTeamShortName.setVisibility(View.GONE);
            }
            if (!tla.isEmpty()) {
                tvTeamTla.setText(tla);
                tvTeamTla.setVisibility(View.VISIBLE);
            } else {
                tvTeamTla.setVisibility(View.GONE);
            }

            if (crestUrl != null && !crestUrl.isEmpty()) {
                Glide.with(this)
                        .load(crestUrl)
                        .into(ivTeamCrest);
                ivTeamCrest.setVisibility(View.VISIBLE);
            } else {
                ivTeamCrest.setVisibility(View.GONE);
            }

            tvTeamVenue.setText(getString(R.string.team_details_venue, venue));
            tvTeamAddress.setText(getString(R.string.team_details_address, address));
            tvTeamWebsite.setText(getString(R.string.team_details_website, website));
            if (!website.isEmpty()) {
                tvTeamWebsite.setOnClickListener(v -> {
                    String url = website;
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "http://" + url;
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                });
            }
            tvTeamFounded.setText(getString(R.string.team_details_founded, founded));
            tvTeamClubColors.setText(getString(R.string.team_details_club_colors, clubColors));
            tvTeamArea.setText(getString(R.string.team_details_area, areaName));

            if (competitionsStr.length() > 0) {
                tvTeamRunningCompetitions.setText(getString(R.string.team_details_running_competitions, competitionsStr.toString()));
                tvTeamRunningCompetitions.setVisibility(View.VISIBLE);
            } else {
                tvTeamRunningCompetitions.setVisibility(View.GONE);
            }

            tvTeamStaff.setText(getString(R.string.team_details_staff) + " (" + staffCount + ")");
            tvTeamSquad.setText(getString(R.string.team_details_squad) + " (" + squadCount + ")");
            squadAdapter.setPlayers(players);

            mostrarContenido();
            mostrarCarga(false);

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error procesando datos del equipo.");
        }
    }

    private void mostrarCarga(boolean loading) {
        pbTeamDetails.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        tvTeamDetailsError.setText(message);
        tvTeamDetailsError.setVisibility(View.VISIBLE);
        mostrarCarga(false);
        ocultarContenido();
    }

    private void ocultarError() {
        tvTeamDetailsError.setVisibility(View.GONE);
    }

    private void mostrarContenido() {
        layoutTeamDetailsContent.setVisibility(View.VISIBLE);
    }

    private void ocultarContenido() {
        layoutTeamDetailsContent.setVisibility(View.GONE);
    }

    // Método público para limpiar la cache de equipos
    public static void clearTeamsCache() {
        teamsCache.clear();
    }

    private static class Player {
        final String name;
        final String position;
        final String nationality;

        Player(String name, String position, String nationality) {
            this.name = name;
            this.position = position;
            this.nationality = nationality;
        }
    }

    private static class TeamSquadAdapter extends RecyclerView.Adapter<TeamSquadAdapter.PlayerViewHolder> {

        private final List<Player> players = new ArrayList<>();

        void setPlayers(List<Player> players) {
            this.players.clear();
            this.players.addAll(players);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PlayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_player, parent, false);
            return new PlayerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PlayerViewHolder holder, int position) {
            Player player = players.get(position);
            holder.tvPlayerName.setText(player.name);
            String pos = player.position;
            if (pos != null && !pos.isEmpty()) {
                holder.tvPlayerPosition.setText(pos);
                holder.tvPlayerPosition.setVisibility(View.VISIBLE);
            } else {
                holder.tvPlayerPosition.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return players.size();
        }

        static class PlayerViewHolder extends RecyclerView.ViewHolder {
            final TextView tvPlayerName;
            final TextView tvPlayerPosition;

            PlayerViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPlayerName = itemView.findViewById(R.id.tvPlayerName);
                tvPlayerPosition = itemView.findViewById(R.id.tvPlayerPosition);
            }
        }
    }
}