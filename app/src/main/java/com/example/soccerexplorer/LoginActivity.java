package com.example.soccerexplorer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    // region Variables
    private static final String PREFS_NAME = "login_prefs";
    private static final String KEY_REMEMBER = "remember_password";
    private static final String KEY_EMAIL = "remembered_email";
    private static final String KEY_PASSWORD = "remembered_password";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_COOLDOWN_UNTIL = "cooldown_until";

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final long COOLDOWN_DURATION_MS = 5 * 60 * 1000L;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private SharedPreferences sharedPreferences;

    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private CheckBox cbRememberPassword;
    private TextView tvLoginError;
    private Button btnLogin;

    private final Handler cooldownHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable cooldownRunnable;
    // endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // region Inicializacion
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // endregion

        // region UI
        vincularVistas();
        cargarCredencialesRecordadas();
        configurarListenersCampos();
        // endregion

        // region Listeners
        btnLogin.setOnClickListener(v -> intentarLogin());
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                intentarLogin();
                return true;
            }
            return false;
        });
        // endregion

        evaluarEstadoCooldownEnPantalla();

    }

    // region UI
    private void vincularVistas() {
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        cbRememberPassword = findViewById(R.id.cbRememberPassword);
        tvLoginError = findViewById(R.id.tvLoginError);
        btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegisterLink = findViewById(R.id.tvRegisterLink);

        tvRegisterLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void configurarListenersCampos() {
        TextWatcher clearErrorWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (estaEnCooldown()) {
                    actualizarMensajeCooldown();
                    return;
                }
                limpiarErroresInputs();
                ocultarErrorLogin();
            }
        };

        etEmail.addTextChangedListener(clearErrorWatcher);
        etPassword.addTextChangedListener(clearErrorWatcher);
    }

    private void cargarCredencialesRecordadas() {
        boolean rememberPassword = sharedPreferences.getBoolean(KEY_REMEMBER, false);
        cbRememberPassword.setChecked(rememberPassword);

        if (rememberPassword) {
            etEmail.setText(sharedPreferences.getString(KEY_EMAIL, ""));
            etPassword.setText(sharedPreferences.getString(KEY_PASSWORD, ""));
        }
    }

    // endregion

    // region Login
    private void intentarLogin() {
        if (estaEnCooldown()) {
            actualizarMensajeCooldown();
            return;
        }

        String email = obtenerTexto(etEmail).trim();
        String password = obtenerTexto(etPassword);

        limpiarErroresInputs();
        ocultarErrorLogin();

        boolean hasErrors = false;

        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_required));
            hasErrors = true;
        }

        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_required));
            hasErrors = true;
        }

        if (hasErrors) {
            return;
        }

        btnLogin.setEnabled(false);

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        limpiarSeguridadLogin();
                        btnLogin.setEnabled(true);
                        gestionarRecordarContrasena(email, password);
                        resolverNavegacionPostLogin();
                        return;
                    }

                    registrarIntentoFallido();
                });
    }

    private void resolverNavegacionPostLogin() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, R.string.error_invalid_credentials, Toast.LENGTH_SHORT).show();
            return;
        }

        firestore.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Boolean onboardingCompleted = documentSnapshot.getBoolean("onboardingCompleted");
                    if (Boolean.TRUE.equals(onboardingCompleted)) {
                        navegarAPrincipal();
                        return;
                    }

                    navegarAElegirEquipoFavorito();
                })
                .addOnFailureListener(e -> navegarAElegirEquipoFavorito());
    }
    // endregion

    // region Errores UI
    private void registrarIntentoFallido() {
        int failedAttempts = sharedPreferences.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_FAILED_ATTEMPTS, failedAttempts);

        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            long cooldownUntil = System.currentTimeMillis() + COOLDOWN_DURATION_MS;
            editor.putLong(KEY_COOLDOWN_UNTIL, cooldownUntil);
            editor.apply();

            mostrarErrorCredencialesInvalidas();
            Toast.makeText(
                    this,
                    getString(R.string.error_login_cooldown_started, formatearTiempo(COOLDOWN_DURATION_MS)),
                    Toast.LENGTH_SHORT
            ).show();
            evaluarEstadoCooldownEnPantalla();
            return;
        }

        editor.apply();
        btnLogin.setEnabled(true);
        mostrarErrorIntentosRestantes(MAX_FAILED_ATTEMPTS - failedAttempts);
    }

    private void mostrarErrorCredencialesInvalidas() {
        tvLoginError.setText(R.string.error_invalid_credentials);
        tvLoginError.setVisibility(View.VISIBLE);
        tilEmail.setError(" ");
        tilPassword.setError(" ");
    }

    private void mostrarErrorIntentosRestantes(int intentosRestantes) {
        tvLoginError.setText(getString(R.string.error_login_attempts_left, intentosRestantes));
        tvLoginError.setVisibility(View.VISIBLE);
        tilEmail.setError(" ");
        tilPassword.setError(" ");
    }

    private void ocultarErrorLogin() {
        tvLoginError.setText("");
        tvLoginError.setVisibility(View.GONE);
    }

    private void limpiarErroresInputs() {
        tilEmail.setError(null);
        tilPassword.setError(null);
    }

    private void evaluarEstadoCooldownEnPantalla() {
        if (estaEnCooldown()) {
            btnLogin.setEnabled(false);
            iniciarTickerCooldown();
            actualizarMensajeCooldown();
        } else {
            detenerTickerCooldown();
            btnLogin.setEnabled(true);
            btnLogin.setText(R.string.action_login);
        }
    }

    private boolean estaEnCooldown() {
        long cooldownUntil = sharedPreferences.getLong(KEY_COOLDOWN_UNTIL, 0L);
        return cooldownUntil > System.currentTimeMillis();
    }

    private long obtenerTiempoRestanteCooldownMs() {
        long cooldownUntil = sharedPreferences.getLong(KEY_COOLDOWN_UNTIL, 0L);
        return Math.max(0L, cooldownUntil - System.currentTimeMillis());
    }

    private void actualizarMensajeCooldown() {
        long restanteMs = obtenerTiempoRestanteCooldownMs();
        if (restanteMs <= 0L) {
            limpiarSeguridadLogin();
            btnLogin.setEnabled(true);
            btnLogin.setText(R.string.action_login);
            ocultarErrorLogin();
            return;
        }

        String tiempo = formatearTiempo(restanteMs);
        tvLoginError.setText(getString(R.string.error_login_cooldown_active, tiempo));
        tvLoginError.setVisibility(View.VISIBLE);
        btnLogin.setText(getString(R.string.action_login_cooldown, tiempo));
    }

    @NonNull
    private String formatearTiempo(long ms) {
        long totalSegundos = Math.max(0L, ms / 1000L);
        long minutos = totalSegundos / 60L;
        long segundos = totalSegundos % 60L;
        return String.format("%02d:%02d", minutos, segundos);
    }

    private void iniciarTickerCooldown() {
        if (cooldownRunnable != null) {
            return;
        }

        cooldownRunnable = new Runnable() {
            @Override
            public void run() {
                if (!estaEnCooldown()) {
                    limpiarSeguridadLogin();
                    btnLogin.setEnabled(true);
                    btnLogin.setText(R.string.action_login);
                    ocultarErrorLogin();
                    detenerTickerCooldown();
                    return;
                }

                actualizarMensajeCooldown();
                cooldownHandler.postDelayed(this, 1000L);
            }
        };

        cooldownHandler.post(cooldownRunnable);
    }

    private void detenerTickerCooldown() {
        if (cooldownRunnable != null) {
            cooldownHandler.removeCallbacks(cooldownRunnable);
            cooldownRunnable = null;
        }
    }

    private void limpiarSeguridadLogin() {
        sharedPreferences.edit()
                .remove(KEY_FAILED_ATTEMPTS)
                .remove(KEY_COOLDOWN_UNTIL)
                .apply();
    }
    // endregion

    // region Preferencias
    private void gestionarRecordarContrasena(@NonNull String email, @NonNull String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if (cbRememberPassword.isChecked()) {
            editor.putBoolean(KEY_REMEMBER, true);
            editor.putString(KEY_EMAIL, email);
            editor.putString(KEY_PASSWORD, password);
        } else {
            editor.putBoolean(KEY_REMEMBER, false);
            editor.remove(KEY_EMAIL);
            editor.remove(KEY_PASSWORD);
        }

        editor.apply();
    }
    // endregion

    // region Navegacion
    private void navegarAElegirEquipoFavorito() {
        Intent intent = new Intent(LoginActivity.this, ElegirEquipoFavActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void navegarAPrincipal() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
    // endregion

    // region Utils
    @NonNull
    private String obtenerTexto(@NonNull TextInputEditText textInputEditText) {
        Editable editable = textInputEditText.getText();
        return editable == null ? "" : editable.toString();
    }
    // endregion

    @Override
    protected void onDestroy() {
        detenerTickerCooldown();
        super.onDestroy();
    }
}
