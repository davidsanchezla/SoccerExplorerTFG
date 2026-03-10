package com.example.soccerexplorer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private TextInputLayout tilRepeatPassword;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private TextInputEditText etRepeatPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        tilUsername = findViewById(R.id.tilUsername);
        tilPassword = findViewById(R.id.tilPassword);
        tilRepeatPassword = findViewById(R.id.tilRepeatPassword);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etRepeatPassword = findViewById(R.id.etRepeatPassword);

        MaterialButton btnRegister = findViewById(R.id.btnRegister);
        TextView tvLoginLink = findViewById(R.id.tvLoginLink);

        btnRegister.setOnClickListener(v -> {
            if (validarFormulario()) {
                Toast.makeText(this, R.string.register_success_placeholder, Toast.LENGTH_SHORT).show();
            }
        });

        tvLoginLink.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }

    private boolean validarFormulario() {
        boolean isValid = true;

        String email = etUsername.getText() == null ? "" : etUsername.getText().toString().trim();
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();
        String repeatPassword = etRepeatPassword.getText() == null
                ? "" : etRepeatPassword.getText().toString();

        tilUsername.setError(null);
        tilPassword.setError(null);
        tilRepeatPassword.setError(null);

        if (email.isEmpty()) {
            tilUsername.setError(getString(R.string.error_email_required));
            isValid = false;
        } else if (!esCorreoValido(email)) {
            tilUsername.setError(getString(R.string.error_email_format));
            isValid = false;
        }

        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_password_required));
            isValid = false;
        } else if (!esPasswordValido(password)) {
            tilPassword.setError(getString(R.string.error_password_rules));
            isValid = false;
        }

        if (repeatPassword.isEmpty()) {
            tilRepeatPassword.setError(getString(R.string.error_repeat_password_required));
            isValid = false;
        } else if (!password.equals(repeatPassword)) {
            tilRepeatPassword.setError(getString(R.string.error_password_mismatch));
            isValid = false;
        }

        return isValid;
    }

    private boolean esPasswordValido(String password) {
        return password.length() >= 8
                && password.matches(".*[A-Z].*")
                && password.matches(".*\\d.*");
    }

    private boolean esCorreoValido(String email) {
        return email.contains("@") && email.matches(".*\\.[A-Za-z]{2,}$");
    }
}
