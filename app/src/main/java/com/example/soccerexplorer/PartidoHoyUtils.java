package com.example.soccerexplorer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PartidoHoyUtils {

    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Madrid");

    private PartidoHoyUtils() {}

    @Nullable
    public static JSONObject encontrarPartidoEquipo(@NonNull String body, @NonNull String equipoFavorito) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONArray matches = root.optJSONArray("matches");
        if (matches == null) {
            return null;
        }

        String favoritoNorm = normalizarTexto(equipoFavorito);

        for (int i = 0; i < matches.length(); i++) {
            JSONObject item = matches.optJSONObject(i);
            if (item == null) continue;

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

    public static boolean coincideEquipo(@NonNull String favNorm, @NonNull String candidateNorm) {
        if (favNorm.isEmpty() || candidateNorm.isEmpty()) {
            return false;
        }
        return candidateNorm.equals(favNorm)
                || candidateNorm.startsWith(favNorm + " ")
                || favNorm.startsWith(candidateNorm + " ");
    }

    @NonNull
    public static String normalizarTexto(@NonNull String input) {
        return input
                .toLowerCase(Locale.ROOT)
                .replace("\u00e1", "a")
                .replace("\u00e9", "e")
                .replace("\u00ed", "i")
                .replace("\u00f3", "o")
                .replace("\u00fa", "u")
                .replace(".", " ")
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @NonNull
    public static String formatearHora(@NonNull String utcDate) {
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

    @NonNull
    public static String leerInputStream(@Nullable InputStream inputStream) throws Exception {
        if (inputStream == null) return "";

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }
}
