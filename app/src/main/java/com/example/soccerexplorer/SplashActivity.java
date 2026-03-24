package com.example.soccerexplorer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 1200L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView ivSplashLogo = findViewById(R.id.ivSplashLogo);
        TextView tvSplashSubtitle = findViewById(R.id.tvSplashSubtitle);
        View splashGlow = findViewById(R.id.splashGlow);

        prepararVista(ivSplashLogo, 24f);
        prepararVista(tvSplashSubtitle, 16f);
        splashGlow.setAlpha(0f);
        splashGlow.setScaleX(0.86f);
        splashGlow.setScaleY(0.86f);

        ivSplashLogo.post(() -> {
            animarVista(ivSplashLogo, 0L, 620L);
            animarVista(tvSplashSubtitle, 220L, 440L);
            splashGlow.animate()
                    .alpha(0.45f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(700L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            ivSplashLogo.postDelayed(this::abrirLogin, SPLASH_DURATION_MS);
        });
    }

    private void prepararVista(@NonNull View view, float translationDp) {
        float translationPx = translationDp * getResources().getDisplayMetrics().density;
        view.setAlpha(0f);
        view.setTranslationY(translationPx);
    }

    private void animarVista(@NonNull View view, long startDelayMs, long durationMs) {
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(startDelayMs)
                .setDuration(durationMs)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void abrirLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
