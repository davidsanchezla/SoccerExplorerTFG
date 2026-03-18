package com.example.soccerexplorer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private TextInputLayout tilRepeatPassword;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private TextInputEditText etRepeatPassword;
    private MaterialButton btnRegister;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

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
        btnRegister = findViewById(R.id.btnRegister);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        TextView tvLoginLink = findViewById(R.id.tvLoginLink);

        btnRegister.setOnClickListener(v -> {
            if (validarFormulario()) {
                registrarUsuario();
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

    private void registrarUsuario() {
        String email = etUsername.getText() == null ? "" : etUsername.getText().toString().trim();
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();

        btnRegister.setEnabled(false);

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && task.getResult() != null
                            && task.getResult().getUser() != null) {
                        String uid = task.getResult().getUser().getUid();
                        guardarUsuarioEnFirestore(uid, email);
                        return;
                    }

                    btnRegister.setEnabled(true);

                    Exception exception = task.getException();
                    if (exception instanceof FirebaseAuthUserCollisionException) {
                        tilUsername.setError(getString(R.string.error_email_already_exists));
                    } else if (exception instanceof FirebaseAuthWeakPasswordException) {
                        tilPassword.setError(getString(R.string.error_password_rules));
                    } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                        tilUsername.setError(getString(R.string.error_email_format));
                    } else {
                        Toast.makeText(this, R.string.error_register_generic, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void guardarUsuarioEnFirestore(String uid, String email) {
        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("email", email);
        user.put("createdAt", FieldValue.serverTimestamp());
        user.put("onboardingCompleted", false); // Nuevo campo para poder controlar el primer login
        user.put("experienciaTotal", 0L);
        user.put("rango", 1L);
        user.put("ultimaSemanaRecompensada", null);

        firestore.collection("users")
                .document(uid)
                .set(user)
                .addOnSuccessListener(unused -> {
                    btnRegister.setEnabled(true);
                    Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnRegister.setEnabled(true);
                    Toast.makeText(this, R.string.error_save_user_db, Toast.LENGTH_LONG).show();
                });
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
