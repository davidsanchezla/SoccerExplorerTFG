package com.example.soccerexplorer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfGenerator {

    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;
    private static final int ROW_HEIGHT = 28;
    private static final int TABLE_HEADER_HEIGHT = 30;
    private static final int SHIELD_SIZE = 24;
    private static final int SHIELD_DOWNLOAD_SIZE = 64;

    private static final int COLOR_PRIMARY = Color.parseColor("#1A73E8");
    private static final int COLOR_SUCCESS = Color.parseColor("#34A853");
    private static final int COLOR_ERROR = Color.parseColor("#EA4335");
    private static final int COLOR_TEXT_DARK = Color.parseColor("#202124");
    private static final int COLOR_TEXT_GRAY = Color.parseColor("#5F6368");
    private static final int COLOR_BORDER = Color.parseColor("#DADCE0");
    private static final int COLOR_BG_LIGHT = Color.parseColor("#F1F3F4");
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");
    private static final int COLOR_GOLD = Color.parseColor("#FBBC04");

    private final Context context;
    private final Map<String, String> leagueNames;
    private final Map<String, Bitmap> shieldCache;
    private final ExecutorService executor;

    public PdfGenerator(Context context) {
        this.context = context;
        this.leagueNames = new HashMap<>();
        this.shieldCache = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(2);

        leagueNames.put("PD", "LaLiga");
        leagueNames.put("PL", "Premier League");
        leagueNames.put("SA", "Serie A");
        leagueNames.put("BL1", "Bundesliga");
        leagueNames.put("FL1", "Ligue 1");
        leagueNames.put("CL", "Champions League");
        leagueNames.put("ELC", "Championship");
        leagueNames.put("EC", "UEFA Euro");
        leagueNames.put("BSA", "Serie A Brasil");
        leagueNames.put("PPL", "Primeira Liga");
        leagueNames.put("DED", "Eredivisie");
        leagueNames.put("WC", "Mundial");
    }

    public void preDownloadShields(List<QuinielaHistorialAdapter.QuinielaItem> quinielas) {
        for (QuinielaHistorialAdapter.QuinielaItem quiniela : quinielas) {
            for (QuinielaHistorialAdapter.PartidoItem partido : quiniela.partidos) {
                downloadShield(partido.homeLogo);
                downloadShield(partido.awayLogo);
            }
        }
    }

    private void downloadShield(String url) {
        if (shieldCache.containsKey(url) || url == null || url.isEmpty()) return;
        
        executor.execute(() -> {
            try {
                URL imageUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) imageUrl.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();
                
                InputStream input = conn.getInputStream();
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(input, null, opts);
                input.close();
                conn.disconnect();
                
                int sampleSize = 1;
                int w = opts.outWidth;
                int h = opts.outHeight;
                while (w / sampleSize > SHIELD_DOWNLOAD_SIZE * 2) {
                    sampleSize *= 2;
                }
                
                conn = (HttpURLConnection) imageUrl.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();
                
                input = conn.getInputStream();
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sampleSize;
                Bitmap bitmap = BitmapFactory.decodeStream(input, null, opts);
                input.close();
                conn.disconnect();
                
                if (bitmap != null) {
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, SHIELD_DOWNLOAD_SIZE, SHIELD_DOWNLOAD_SIZE, true);
                    shieldCache.put(url, scaled);
                    if (bitmap != scaled) bitmap.recycle();
                }
            } catch (Exception ignored) {}
        });
    }

    public File generateHistorialPdf(
            String userEmail,
            List<QuinielaHistorialAdapter.QuinielaItem> quinielas
    ) throws IOException {
        preDownloadShields(quinielas);
        
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {}

        PdfDocument document = new PdfDocument();
        int currentPage = 1;
        int yPosition;
        int quinielaIndex = 0;

        while (quinielaIndex < quinielas.size()) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH, PAGE_HEIGHT, currentPage
            ).create();

            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            yPosition = drawPageHeader(canvas, userEmail);

            while (quinielaIndex < quinielas.size() && yPosition < PAGE_HEIGHT - 80) {
                yPosition = drawQuinielaCard(canvas, yPosition, quinielas.get(quinielaIndex));
                quinielaIndex++;
            }

            drawPageFooter(canvas, currentPage, quinielaIndex < quinielas.size());
            document.finishPage(page);
            currentPage++;
        }

        File cacheDir = context.getCacheDir();
        String fileName = "quinielas_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";
        File pdfFile = new File(cacheDir, fileName);

        FileOutputStream fos = new FileOutputStream(pdfFile);
        document.writeTo(fos);
        fos.close();
        document.close();

        return pdfFile;
    }

    private int drawPageHeader(Canvas canvas, String userEmail) {
        Paint titlePaint = new Paint();
        titlePaint.setColor(COLOR_PRIMARY);
        titlePaint.setTextSize(24);
        titlePaint.setFakeBoldText(true);
        titlePaint.setAntiAlias(true);
        
        canvas.drawText("SOCCER EXPLORER", MARGIN, 35, titlePaint);

        Paint subtitlePaint = new Paint();
        subtitlePaint.setColor(COLOR_TEXT_DARK);
        subtitlePaint.setTextSize(16);
        subtitlePaint.setFakeBoldText(true);
        subtitlePaint.setAntiAlias(true);
        
        canvas.drawText("Historial de Quinielas", MARGIN, 58, subtitlePaint);

        if (userEmail != null && !userEmail.isEmpty()) {
            Paint emailPaint = new Paint();
            emailPaint.setColor(COLOR_TEXT_GRAY);
            emailPaint.setTextSize(10);
            emailPaint.setAntiAlias(true);
            canvas.drawText(userEmail, MARGIN, 75, emailPaint);
        }

        Paint linePaint = new Paint();
        linePaint.setColor(COLOR_BORDER);
        linePaint.setStrokeWidth(1);
        canvas.drawLine(MARGIN, 85, PAGE_WIDTH - MARGIN, 85, linePaint);

        return 100;
    }

    private int drawQuinielaCard(Canvas canvas, int y, QuinielaHistorialAdapter.QuinielaItem quiniela) {
        int cardHeight = 50 + TABLE_HEADER_HEIGHT + (quiniela.partidos.size() * ROW_HEIGHT) + 40;
        
        Paint cardBg = new Paint();
        cardBg.setColor(COLOR_WHITE);
        RectF cardRect = new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + cardHeight);
        canvas.drawRoundRect(cardRect, 8, 8, cardBg);

        Paint cardBorder = new Paint();
        cardBorder.setColor(COLOR_BORDER);
        cardBorder.setStyle(Paint.Style.STROKE);
        cardBorder.setStrokeWidth(1);
        canvas.drawRoundRect(cardRect, 8, 8, cardBorder);

        String leagueName = leagueNames.getOrDefault(quiniela.ligaId, quiniela.ligaId);
        
        Paint leaguePaint = new Paint();
        leaguePaint.setColor(COLOR_PRIMARY);
        leaguePaint.setTextSize(11);
        leaguePaint.setFakeBoldText(true);
        leaguePaint.setAntiAlias(true);
        canvas.drawText(leagueName.toUpperCase(), MARGIN + 15, y + 22, leaguePaint);

        Paint jornadaPaint = new Paint();
        jornadaPaint.setColor(COLOR_TEXT_DARK);
        jornadaPaint.setTextSize(14);
        jornadaPaint.setFakeBoldText(true);
        jornadaPaint.setAntiAlias(true);
        canvas.drawText(quiniela.tituloJornada, MARGIN + 15, y + 40, jornadaPaint);

        int aciertos = 0;
        for (QuinielaHistorialAdapter.PartidoItem p : quiniela.partidos) {
            if (p.esAcierto) aciertos++;
        }

        Paint statsPaint = new Paint();
        statsPaint.setColor(COLOR_TEXT_DARK);
        statsPaint.setTextSize(12);
        statsPaint.setFakeBoldText(true);
        statsPaint.setAntiAlias(true);
        String statsText = aciertos + "/" + quiniela.partidos.size();
        float statsW = statsPaint.measureText(statsText);
        canvas.drawText(statsText, PAGE_WIDTH - MARGIN - statsW - 15, y + 30, statsPaint);

        if (quiniela.bonusPleno) {
            Paint badgeBg = new Paint();
            badgeBg.setColor(COLOR_GOLD);
            RectF badgeRect = new RectF(PAGE_WIDTH - MARGIN - 55, y + 12, PAGE_WIDTH - MARGIN - 12, y + 28);
            canvas.drawRoundRect(badgeRect, 4, 4, badgeBg);
            
            Paint badgeText = new Paint();
            badgeText.setColor(COLOR_WHITE);
            badgeText.setTextSize(9);
            badgeText.setFakeBoldText(true);
            badgeText.setAntiAlias(true);
            canvas.drawText("PLENO", PAGE_WIDTH - MARGIN - 50, y + 23, badgeText);
        }

        y += 55;

        int[] colWidths = {200, 55, 75, 50};
        int[] colX = new int[4];
        colX[0] = MARGIN + 10;
        for (int i = 1; i < 4; i++) {
            colX[i] = colX[i - 1] + colWidths[i - 1];
        }

        Paint headerBg = new Paint();
        headerBg.setColor(COLOR_PRIMARY);
        RectF headerRect = new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + TABLE_HEADER_HEIGHT);
        canvas.drawRect(headerRect, headerBg);

        Paint headerPaint = new Paint();
        headerPaint.setColor(COLOR_WHITE);
        headerPaint.setTextSize(10);
        headerPaint.setFakeBoldText(true);
        headerPaint.setAntiAlias(true);

        canvas.drawText("PARTIDO", colX[0], y + 20, headerPaint);
        
        String pickLabel = "PICK";
        float pickW = headerPaint.measureText(pickLabel);
        canvas.drawText(pickLabel, colX[1] + (colWidths[1] - pickW) / 2, y + 20, headerPaint);
        
        String scoreLabel = "MARC.";
        float scoreW = headerPaint.measureText(scoreLabel);
        canvas.drawText(scoreLabel, colX[2] + (colWidths[2] - scoreW) / 2, y + 20, headerPaint);
        
        String okLabel = "OK";
        float okW = headerPaint.measureText(okLabel);
        canvas.drawText(okLabel, colX[3] + (colWidths[3] - okW) / 2, y + 20, headerPaint);

        y += TABLE_HEADER_HEIGHT;

        int row = 0;
        for (QuinielaHistorialAdapter.PartidoItem partido : quiniela.partidos) {
            boolean alt = row % 2 == 1;
            
            if (alt) {
                Paint altBg = new Paint();
                altBg.setColor(COLOR_BG_LIGHT);
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, altBg);
            }

            int textY = y + 19;

            Bitmap homeShield = shieldCache.get(partido.homeLogo);
            if (homeShield != null) {
                RectF homeRect = new RectF(MARGIN + 10, y + (ROW_HEIGHT - SHIELD_SIZE) / 2f, 
                        MARGIN + 10 + SHIELD_SIZE, y + (ROW_HEIGHT + SHIELD_SIZE) / 2f);
                canvas.drawBitmap(homeShield, null, homeRect, null);
            }

            Bitmap awayShield = shieldCache.get(partido.awayLogo);
            if (awayShield != null) {
                RectF awayRect = new RectF(MARGIN + 38, y + (ROW_HEIGHT - SHIELD_SIZE) / 2f, 
                        MARGIN + 38 + SHIELD_SIZE, y + (ROW_HEIGHT + SHIELD_SIZE) / 2f);
                canvas.drawBitmap(awayShield, null, awayRect, null);
            }

            Paint matchPaint = new Paint();
            matchPaint.setColor(COLOR_TEXT_DARK);
            matchPaint.setTextSize(9);
            matchPaint.setAntiAlias(true);
            
            String home = truncate(partido.homeTeam, 14);
            String away = truncate(partido.awayTeam, 14);
            canvas.drawText(home + " vs " + away, MARGIN + 58, textY, matchPaint);

            Paint pickBg = new Paint();
            pickBg.setColor(partido.esAcierto ? COLOR_SUCCESS : COLOR_ERROR);
            RectF pickRect = new RectF(colX[1] + 10, y + 6, colX[1] + 38, y + 22);
            canvas.drawRoundRect(pickRect, 4, 4, pickBg);

            Paint pickText = new Paint();
            pickText.setColor(COLOR_WHITE);
            pickText.setTextSize(11);
            pickText.setFakeBoldText(true);
            pickText.setAntiAlias(true);
            float pickW2 = pickText.measureText(partido.pronostico);
            canvas.drawText(partido.pronostico, colX[1] + 10 + (28 - pickW2) / 2, textY, pickText);

            String score = partido.resultadoMarcador;
            if (score == null || score.isEmpty() || score.equals("- -")) {
                score = "?";
            }
            Paint scorePaint = new Paint();
            scorePaint.setColor(COLOR_TEXT_DARK);
            scorePaint.setTextSize(10);
            scorePaint.setFakeBoldText(true);
            scorePaint.setAntiAlias(true);
            float scoreW2 = scorePaint.measureText(score);
            canvas.drawText(score, colX[2] + (colWidths[2] - scoreW2) / 2, textY, scorePaint);

            String result = partido.esAcierto ? "\u2713" : "\u2717";
            Paint resultPaint = new Paint();
            resultPaint.setColor(partido.esAcierto ? COLOR_SUCCESS : COLOR_ERROR);
            resultPaint.setTextSize(14);
            resultPaint.setFakeBoldText(true);
            resultPaint.setAntiAlias(true);
            float resultW = resultPaint.measureText(result);
            canvas.drawText(result, colX[3] + (colWidths[3] - resultW) / 2, textY + 2, resultPaint);

            y += ROW_HEIGHT;
            row++;
        }

        y += 8;

        Paint summaryPaint = new Paint();
        summaryPaint.setColor(COLOR_TEXT_GRAY);
        summaryPaint.setTextSize(10);
        summaryPaint.setAntiAlias(true);
        String summary = String.format(Locale.getDefault(), 
                "Aciertos: %d/%d  |  XP: +%d", 
                aciertos, quiniela.partidos.size(), quiniela.xpGanada);
        canvas.drawText(summary, MARGIN + 15, y + 15, summaryPaint);

        return y + 35;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 2) + "..";
    }

    private void drawPageFooter(Canvas canvas, int page, boolean hasMore) {
        Paint linePaint = new Paint();
        linePaint.setColor(COLOR_BORDER);
        linePaint.setStrokeWidth(1);
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 40, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 40, linePaint);

        Paint footerPaint = new Paint();
        footerPaint.setColor(COLOR_TEXT_GRAY);
        footerPaint.setTextSize(9);
        footerPaint.setAntiAlias(true);

        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es")));
        canvas.drawText("SoccerExplorer  |  " + fecha, MARGIN, PAGE_HEIGHT - 20, footerPaint);
    }

    public void shutdown() {
        executor.shutdown();
        for (Bitmap b : shieldCache.values()) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        shieldCache.clear();
    }
}
