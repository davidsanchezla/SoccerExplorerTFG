package com.example.soccerexplorer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
                    btnLogin.setEnabled(true);

                    if (task.isSuccessful()) {
                        gestionarRecordarContrasena(email, password);
                        resolverNavegacionPostLogin();
                        return;
                    }

                    mostrarErrorCredencialesInvalidas();
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
    private void mostrarErrorCredencialesInvalidas() {
        tvLoginError.setText(R.string.error_invalid_credentials);
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
}
