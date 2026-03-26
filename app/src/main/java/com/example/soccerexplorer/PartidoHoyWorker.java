package com.example.soccerexplorer;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class PartidoHoyWorker extends Worker {

    private static final String API_BASE = "https://api.football-data.org/v4";
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Madrid");
    private static final String PREFS_NOTIFICATIONS = "match_notifications";
    private static final String KEY_LAST_NOTIFIED_DATE = "last_notified_date";
    private static final String CHANNEL_ID = "partidos_hoy_channel";
    private static final int NOTIFICATION_ID = 9101;

    public PartidoHoyWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                return Result.success();
            }

            DocumentSnapshot userDoc = Tasks.await(
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(currentUser.getUid())
                            .get(),
                    12,
                    TimeUnit.SECONDS
            );

            String equipoFavorito = userDoc.getString("equipoFavoritoNombre");
            if (equipoFavorito == null || equipoFavorito.trim().isEmpty()) {
                return Result.success();
            }

            LocalDate today = LocalDate.now(APP_ZONE);
            String todayKey = today.toString();
            String userDateKey = KEY_LAST_NOTIFIED_DATE + "_" + currentUser.getUid();

            SharedPreferences prefs = getApplicationContext()
                    .getSharedPreferences(PREFS_NOTIFICATIONS, Context.MODE_PRIVATE);
            String lastNotifiedDate = prefs.getString(userDateKey, "");

            if (todayKey.equals(lastNotifiedDate)) {
                return Result.success();
            }

            String apiDate = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
            ApiResult apiResult = ejecutarGet("/matches?date=" + apiDate);
            if (!apiResult.ok) {
                return apiResult.code == 429 ? Result.retry() : Result.failure();
            }

            JSONObject match = encontrarPartidoEquipo(apiResult.body, equipoFavorito);
            if (match == null) {
                return Result.success();
            }

            JSONObject home = match.optJSONObject("homeTeam");
            JSONObject away = match.optJSONObject("awayTeam");

            String homeName = home != null ? home.optString("name", "") : "";
            String awayName = away != null ? away.optString("name", "") : "";

            String utcDate = match.optString("utcDate", "");
            String timeText = formatearHora(utcDate);

            mostrarNotificacion(homeName, awayName, timeText);

            prefs.edit().putString(userDateKey, todayKey).apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    @Nullable
    private JSONObject encontrarPartidoEquipo(@NonNull String body, @NonNull String equipoFavorito) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONArray matches = root.optJSONArray("matches");
        if (matches == null) {
            return null;
        }

        String favoritoNorm = normalizarTexto(equipoFavorito);

        for (int i = 0; i < matches.length(); i++) {
            JSONObject item = matches.optJSONObject(i);
            if (item == null) {
                continue;
            }

            JSONObject home = item.optJSONObject("homeTeam");
            JSONObject away = item.optJSONObject("awayTeam");

            String homeName = home != null ? home.optString("name", "") : "";
            String awayName = away != null ? away.optString("name", "") : "";

            String homeNorm = normalizarTexto(homeName);
            String awayNorm = normalizarTexto(awayName);

            if (coincideEquipo(favoritoNorm, homeNorm) || coincideEquipo(favoritoNorm, awayNorm)) {
                return item;
            }
        }

        return null;
    }

    private boolean coincideEquipo(@NonNull String favNorm, @NonNull String candidateNorm) {
        if (favNorm.isEmpty() || candidateNorm.isEmpty()) {
            return false;
        }
        return candidateNorm.equals(favNorm)
                || candidateNorm.startsWith(favNorm + " ")
                || favNorm.startsWith(candidateNorm + " ");
    }

    @NonNull
    private String normalizarTexto(@NonNull String input) {
        return input
                .toLowerCase(Locale.ROOT)
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace(".", " ")
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @NonNull
    private String formatearHora(@NonNull String utcDate) {
        if (utcDate.trim().isEmpty()) {
            return "--:--";
        }

        try {
            Instant instant = Instant.parse(utcDate);
            LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, APP_ZONE);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm", new Locale("es", "ES"));
            return localDateTime.format(formatter);
        } catch (Exception e) {
            return "--:--";
        }
    }

    private void mostrarNotificacion(@NonNull String home, @NonNull String away, @NonNull String hora) {
        Context context = getApplicationContext();
        crearCanalSiHaceFalta(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        Intent openMainIntent = new Intent(context, MainActivity.class);
        openMainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openMainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notif_matches_title))
                .setContentText(context.getString(R.string.notif_matches_content, home, away, hora))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.notif_matches_content, home, away, hora)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
    }

    private void crearCanalSiHaceFalta(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_matches_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(context.getString(R.string.notif_matches_channel_desc));
        manager.createNotificationChannel(channel);
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
