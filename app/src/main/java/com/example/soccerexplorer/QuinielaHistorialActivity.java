package com.example.soccerexplorer;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuinielaHistorialActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "quiniela_pdf_channel";
    private static final int NOTIFICATION_ID = 1001;

    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    private RecyclerView rvHistorial;
    private LinearLayout layoutEmpty;
    private ProgressBar pbHistorial;
    private MaterialButton btnExportarPdf;
    private MaterialButton btnGuardarPdf;

    private QuinielaHistorialAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Uri lastSavedPdfUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiniela_historial);

        crearCanalNotificacion();

        firestore = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        MaterialToolbar toolbar = findViewById(R.id.toolbarHistorial);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvHistorial = findViewById(R.id.rvHistorialQuinielas);
        layoutEmpty = findViewById(R.id.layoutHistorialEmpty);
        pbHistorial = findViewById(R.id.pbHistorial);
        btnExportarPdf = findViewById(R.id.btnExportarPdf);
        btnGuardarPdf = findViewById(R.id.btnGuardarPdf);

        adapter = new QuinielaHistorialAdapter();
        adapter.setOnSelectionChangedListener(this::onSelectionChanged);

        rvHistorial.setLayoutManager(new LinearLayoutManager(this));
        rvHistorial.setAdapter(adapter);

        btnExportarPdf.setOnClickListener(v -> confirmarExportarPdf());
        btnGuardarPdf.setOnClickListener(v -> confirmarGuardarPdf());

        if (currentUser == null) {
            mostrarError(getString(R.string.historial_error_no_user));
            return;
        }

        cargarHistorial();
    }

    private void onSelectionChanged(Set<String> selectedIds) {
        boolean hasSelection = !selectedIds.isEmpty();
        btnExportarPdf.setEnabled(hasSelection);
        btnGuardarPdf.setEnabled(hasSelection);
    }

    private void cargarHistorial() {
        pbHistorial.setVisibility(View.VISIBLE);
        rvHistorial.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("quinielas")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(this::onHistorialLoaded)
                .addOnFailureListener(e -> mostrarError(getString(R.string.historial_error_load)));
    }

    private void onHistorialLoaded(@NonNull QuerySnapshot querySnapshot) {
        pbHistorial.setVisibility(View.GONE);

        if (querySnapshot.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvHistorial.setVisibility(View.GONE);
            btnExportarPdf.setEnabled(false);
            btnGuardarPdf.setEnabled(false);
            return;
        }

        List<QuinielaHistorialAdapter.QuinielaItem> items = new ArrayList<>();

        for (int i = 0; i < querySnapshot.getDocuments().size(); i++) {
            var doc = querySnapshot.getDocuments().get(i);
            QuinielaHistorialAdapter.QuinielaItem item = parseQuinielaDoc(doc, i);
            if (item != null) {
                items.add(item);
            }
        }

        if (items.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvHistorial.setVisibility(View.GONE);
            btnExportarPdf.setEnabled(false);
        } else {
            rvHistorial.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            adapter.submitList(items);
            btnExportarPdf.setEnabled(true);
        }
    }

    private QuinielaHistorialAdapter.QuinielaItem parseQuinielaDoc(
            com.google.firebase.firestore.DocumentSnapshot doc, int index) {
        String semanaId = doc.getString("semanaId");
        String ligaId = doc.getString("ligaId");
        Long puntosSemana = doc.getLong("puntosSemana");
        Long xpGanada = doc.getLong("xpGanada");
        Boolean bonusPleno = doc.getBoolean("bonusPleno");
        Long totalPartidos = doc.getLong("totalPartidos");
        List<Map<String, Object>> partidosRaw = (List<Map<String, Object>>) doc.get("partidos");

        if (semanaId == null || ligaId == null) {
            return null;
        }

        String tituloJornada = generarTituloJornada(semanaId);
        int aciertos = puntosSemana != null ? puntosSemana.intValue() : 0;
        int xp = xpGanada != null ? xpGanada.intValue() : 0;
        int total = totalPartidos != null ? totalPartidos.intValue() : 0;
        boolean pleno = bonusPleno != null && bonusPleno;

        List<QuinielaHistorialAdapter.PartidoItem> partidos = new ArrayList<>();

        if (partidosRaw != null) {
            for (Map<String, Object> p : partidosRaw) {
                String matchId = (String) p.get("matchId");
                String homeTeam = (String) p.get("local");
                String awayTeam = (String) p.get("visitante");
                String pronostico = (String) p.get("pick");
                String marcadorStr = (String) p.get("marcador");
                String resultadoFinal = (String) p.get("resultado");
                Boolean aciertoBool = (Boolean) p.get("acierto");

                if (homeTeam == null) homeTeam = "?";
                if (awayTeam == null) awayTeam = "?";
                if (pronostico == null) pronostico = "-";

                int homeScore = -1;
                int awayScore = -1;
                if (marcadorStr != null && marcadorStr.contains("-")) {
                    try {
                        String[] parts = marcadorStr.split("-");
                        homeScore = Integer.parseInt(parts[0].trim());
                        awayScore = Integer.parseInt(parts[1].trim());
                    } catch (Exception ignored) {}
                }

                boolean esAcierto = aciertoBool != null && aciertoBool;
                if (!esAcierto && homeScore >= 0 && awayScore >= 0 && pronostico != null) {
                    if (homeScore > awayScore && "1".equals(pronostico)) esAcierto = true;
                    else if (homeScore == awayScore && "X".equals(pronostico)) esAcierto = true;
                    else if (homeScore < awayScore && "2".equals(pronostico)) esAcierto = true;
                }

                String marcador = marcadorStr != null ? marcadorStr : "";
                String homeLogo = (String) p.get("homeLogo");
                String awayLogo = (String) p.get("awayLogo");
                
                partidos.add(new QuinielaHistorialAdapter.PartidoItem(
                        matchId != null ? matchId : "",
                        homeTeam, awayTeam,
                        homeLogo, awayLogo,
                        pronostico,
                        homeScore, awayScore,
                        marcador,
                        esAcierto
                ));
            }
        }

        String id = doc.getId();
        return new QuinielaHistorialAdapter.QuinielaItem(
                id, tituloJornada, semanaId, ligaId,
                aciertos, total, xp, pleno, partidos
        );
    }

    private String generarTituloJornada(String semanaId) {
        if (semanaId == null) return "Quiniela";

        String[] parts = semanaId.split("-J");
        if (parts.length > 1) {
            try {
                int jornada = Integer.parseInt(parts[1]);
                return String.format(Locale.getDefault(), "Jornada %d", jornada);
            } catch (NumberFormatException ignored) {
            }
        }

        return semanaId;
    }

    private Long toLong(Object value) {
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof Double) return ((Double) value).longValue();
        return null;
    }

    private void exportarPdf() {
        List<QuinielaHistorialAdapter.QuinielaItem> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.historial_select_at_least_one, Toast.LENGTH_SHORT).show();
            return;
        }

        btnExportarPdf.setEnabled(false);
        btnGuardarPdf.setEnabled(false);
        btnExportarPdf.setText(R.string.historial_generating_button);

        String email = currentUser.getEmail();
        String userEmail = email != null ? email : "";

        executor.execute(() -> {
            try {
                PdfGenerator generator = new PdfGenerator(this);
                File pdfFile = generator.generateHistorialPdf(userEmail, selected);
                generator.shutdown();

                runOnUiThread(() -> {
                    btnExportarPdf.setEnabled(true);
                    btnGuardarPdf.setEnabled(true);
                    btnExportarPdf.setText(R.string.historial_export_button);
                    compartirPdf(pdfFile);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    btnExportarPdf.setEnabled(true);
                    btnGuardarPdf.setEnabled(true);
                    btnExportarPdf.setText(R.string.historial_export_button);
                    Toast.makeText(this, R.string.historial_pdf_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void guardarPdf() {
        List<QuinielaHistorialAdapter.QuinielaItem> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.historial_select_at_least_one, Toast.LENGTH_SHORT).show();
            return;
        }

        btnExportarPdf.setEnabled(false);
        btnGuardarPdf.setEnabled(false);
        btnGuardarPdf.setText(R.string.historial_saving_button);

        String email = currentUser.getEmail();
        String userEmail = email != null ? email : "";

        executor.execute(() -> {
            try {
                PdfGenerator generator = new PdfGenerator(this);
                File pdfFile = generator.generateHistorialPdf(userEmail, selected);
                generator.shutdown();

                String fileName = "quinielas_" + java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";

                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                if (uri != null) {
                    try (java.io.OutputStream outputStream = getContentResolver().openOutputStream(uri);
                         java.io.InputStream inputStream = new java.io.FileInputStream(pdfFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, len);
                        }
                    }

                    values.clear();
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);

                    lastSavedPdfUri = uri;
                }

                final Uri finalUri = uri;
                runOnUiThread(() -> {
                    btnExportarPdf.setEnabled(true);
                    btnGuardarPdf.setEnabled(true);
                    btnGuardarPdf.setText(R.string.historial_save_button);
                    mostrarNotificacion(finalUri, fileName);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnExportarPdf.setEnabled(true);
                    btnGuardarPdf.setEnabled(true);
                    btnGuardarPdf.setText(R.string.historial_save_button);
                    Toast.makeText(this, R.string.historial_pdf_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void confirmarExportarPdf() {
        List<QuinielaHistorialAdapter.QuinielaItem> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.historial_select_at_least_one, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.historial_confirm_export_title)
                .setMessage(getString(R.string.historial_confirm_export_message, selected.size()))
                .setPositiveButton(R.string.historial_confirm_export_yes, (dialog, which) -> exportarPdf())
                .setNegativeButton(R.string.historial_confirm_export_no, null)
                .show();
    }

    private void confirmarGuardarPdf() {
        List<QuinielaHistorialAdapter.QuinielaItem> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.historial_select_at_least_one, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.historial_confirm_save_title)
                .setMessage(getString(R.string.historial_confirm_save_message, selected.size()))
                .setPositiveButton(R.string.historial_confirm_save_yes, (dialog, which) -> guardarPdf())
                .setNegativeButton(R.string.historial_confirm_save_no, null)
                .show();
    }

    private void compartirPdf(File pdfFile) {
        try {
            var uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    pdfFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, getString(R.string.historial_pdf_share_title)));
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, R.string.historial_pdf_success, Toast.LENGTH_LONG).show();
        }
    }

    private void mostrarError(String mensaje) {
        pbHistorial.setVisibility(View.GONE);
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
        layoutEmpty.setVisibility(View.VISIBLE);
        rvHistorial.setVisibility(View.GONE);
        btnExportarPdf.setEnabled(false);
        btnGuardarPdf.setEnabled(false);
    }

    private void crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Historial de Quinielas";
            String description = "Notificaciones al guardar PDF de quinielas";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void mostrarNotificacion(Uri pdfUri, String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                Toast.makeText(this, R.string.historial_notifications_permission_hint, Toast.LENGTH_LONG).show();
                return;
            }
        }

        Intent intentAbrir = new Intent(Intent.ACTION_VIEW);
        intentAbrir.setDataAndType(pdfUri, "application/pdf");
        intentAbrir.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intentAbrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(getString(R.string.historial_notification_title))
                .setContentText(getString(R.string.historial_notification_text_format, fileName))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.historial_notification_big_text_format, fileName)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 250, 250, 250});

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.historial_notification_permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
