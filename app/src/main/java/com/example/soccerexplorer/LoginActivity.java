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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "login_prefs";
    private static final String KEY_REMEMBER = "remember_password";
    private static final String KEY_EMAIL = "remembered_email";
    private static final String KEY_PASSWORD = "remembered_password";

    private FirebaseAuth firebaseAuth;
    private SharedPreferences sharedPreferences;

    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private CheckBox cbRememberPassword;
    private TextView tvLoginError;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        bindViews();
        loadRememberedCredentials();
        setupFieldListeners();

        btnLogin.setOnClickListener(v -> attemptLogin());
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void bindViews() {
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        cbRememberPassword = findViewById(R.id.cbRememberPassword);
        tvLoginError = findViewById(R.id.tvLoginError);
        btnLogin = findViewById(R.id.btnLogin);
    }

    private void setupFieldListeners() {
        TextWatcher clearErrorWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearInputErrors();
                hideLoginError();
            }
        };

        etEmail.addTextChangedListener(clearErrorWatcher);
        etPassword.addTextChangedListener(clearErrorWatcher);
    }

    private void loadRememberedCredentials() {
        boolean rememberPassword = sharedPreferences.getBoolean(KEY_REMEMBER, false);
        cbRememberPassword.setChecked(rememberPassword);

        if (rememberPassword) {
            etEmail.setText(sharedPreferences.getString(KEY_EMAIL, ""));
            etPassword.setText(sharedPreferences.getString(KEY_PASSWORD, ""));
        }
    }

    private void attemptLogin() {
        String email = getText(etEmail).trim();
        String password = getText(etPassword);

        clearInputErrors();
        hideLoginError();

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
                        handleRememberPassword(email, password);
                        navigateToMain();
                        return;
                    }

                    showInvalidCredentialsError();
                });
    }

    private void showInvalidCredentialsError() {
        tvLoginError.setText(R.string.error_invalid_credentials);
        tvLoginError.setVisibility(View.VISIBLE);
        tilEmail.setError(" ");
        tilPassword.setError(" ");
    }

    private void hideLoginError() {
        tvLoginError.setText("");
        tvLoginError.setVisibility(View.GONE);
    }

    private void clearInputErrors() {
        tilEmail.setError(null);
        tilPassword.setError(null);
    }

    private void handleRememberPassword(@NonNull String email, @NonNull String password) {
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

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @NonNull
    private String getText(@NonNull TextInputEditText textInputEditText) {
        Editable editable = textInputEditText.getText();
        return editable == null ? "" : editable.toString();
    }
}
