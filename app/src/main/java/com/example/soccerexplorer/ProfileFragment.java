package com.example.soccerexplorer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.button.MaterialButton;

public class ProfileFragment extends Fragment {

    private TextView tvProfileEmailValue;
    private TextView tvProfileFavTeamValue;
    private TextView tvProfileStatus;
    private MaterialButton btnProfileQuiniela;
    private MaterialButton btnProfileLogout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        tvProfileEmailValue = view.findViewById(R.id.tvProfileEmailValue);
        tvProfileFavTeamValue = view.findViewById(R.id.tvProfileFavTeamValue);
        tvProfileStatus = view.findViewById(R.id.tvProfileStatus);
        btnProfileQuiniela = view.findViewById(R.id.btnProfileQuiniela);
        btnProfileLogout = view.findViewById(R.id.btnProfileLogout);

        configurarAcciones();

        cargarDatosBasicosPerfil();
        return view;
    }

    private void configurarAcciones() {
        btnProfileQuiniela.setOnClickListener(v -> {
            if (!isAdded()) {
                return;
            }
            Intent intent = new Intent(requireContext(), QuinielaActivity.class);
            startActivity(intent);
        });

        btnProfileLogout.setOnClickListener(v -> cerrarSesion());
    }

    private void cerrarSesion() {
        FirebaseAuth.getInstance().signOut();
        if (!isAdded()) {
            return;
        }
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void cargarDatosBasicosPerfil() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            tvProfileEmailValue.setText(getString(R.string.profile_value_not_logged));
            tvProfileFavTeamValue.setText(getString(R.string.profile_value_not_available));
            tvProfileStatus.setVisibility(View.VISIBLE);
            tvProfileStatus.setText(R.string.profile_status_not_logged);
            return;
        }

        String email = currentUser.getEmail();
        if (email == null || email.trim().isEmpty()) {
            tvProfileEmailValue.setText(getString(R.string.profile_value_not_available));
        } else {
            tvProfileEmailValue.setText(email);
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) {
                        return;
                    }

                    String equipo = documentSnapshot.getString("equipoFavoritoNombre");
                    if (equipo == null || equipo.trim().isEmpty()) {
                        tvProfileFavTeamValue.setText(getString(R.string.profile_value_not_selected));
                    } else {
                        tvProfileFavTeamValue.setText(equipo);
                    }
                    tvProfileStatus.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    tvProfileFavTeamValue.setText(getString(R.string.profile_value_not_available));
                    tvProfileStatus.setVisibility(View.VISIBLE);
                    tvProfileStatus.setText(R.string.profile_status_error_load);
                });
    }
}
