package com.example.soccerexplorer;

import android.content.Intent;
import android.util.Base64;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.button.MaterialButton;

public class ProfileFragment extends Fragment {

    private TextView tvProfileEmailValue;
    private TextView tvProfileFavTeamValue;
    private TextView tvProfileStatus;
    private TextView tvProfileHeaderTeam;
    private TextView tvProfileLevelValue;
    private TextView tvProfileExperienceValue;
    private TextView tvProfileExperienceProgress;
    private AppCompatImageView ivProfileTeamShield;
    private ProgressBar pbProfileExperience;
    private MaterialButton btnProfileQuiniela;
    private MaterialButton btnProfileChangeTeam;
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
        tvProfileHeaderTeam = view.findViewById(R.id.tvProfileHeaderTeam);
        tvProfileLevelValue = view.findViewById(R.id.tvProfileLevelValue);
        tvProfileExperienceValue = view.findViewById(R.id.tvProfileExperienceValue);
        tvProfileExperienceProgress = view.findViewById(R.id.tvProfileExperienceProgress);
        ivProfileTeamShield = view.findViewById(R.id.ivProfileTeamShield);
        pbProfileExperience = view.findViewById(R.id.pbProfileExperience);
        btnProfileQuiniela = view.findViewById(R.id.btnProfileQuiniela);
        btnProfileChangeTeam = view.findViewById(R.id.btnProfileChangeTeam);
        btnProfileLogout = view.findViewById(R.id.btnProfileLogout);

        configurarAcciones();

        cargarDatosBasicosPerfil();
        return view;
    }

    private void configurarAcciones() {
        btnProfileQuiniela.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.profile_quiniela_placeholder, Toast.LENGTH_SHORT).show()
        );

        btnProfileChangeTeam.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ElegirEquipoFavActivity.class);
            startActivity(intent);
        });

        btnProfileLogout.setOnClickListener(v -> cerrarSesion());
    }

    @Override
    public void onResume() {
        super.onResume();
        cargarDatosBasicosPerfil();
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
            tvProfileHeaderTeam.setText(getString(R.string.profile_value_not_selected));
            ivProfileTeamShield.setImageResource(R.mipmap.ic_launcher_round);
            setExperienciaUI(1L, 0L);
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
                    String escudo = documentSnapshot.getString("equipoFavoritoEscudo");
                    Long rango = documentSnapshot.getLong("rango");
                    Long experienciaTotal = documentSnapshot.getLong("experienciaTotal");
                    if (equipo == null || equipo.trim().isEmpty()) {
                        tvProfileFavTeamValue.setText(getString(R.string.profile_value_not_selected));
                        tvProfileHeaderTeam.setText(getString(R.string.profile_value_not_selected));
                    } else {
                        tvProfileFavTeamValue.setText(equipo);
                        tvProfileHeaderTeam.setText(equipo);
                    }

                    setExperienciaUI(rango, experienciaTotal);

                    if (escudo == null || escudo.trim().isEmpty()) {
                        ivProfileTeamShield.setImageResource(R.mipmap.ic_launcher_round);
                    } else {
                        Object modeloEscudo = construirModeloSolicitudEscudo(escudo);
                        Glide.with(requireContext())
                                .load(modeloEscudo)
                                .apply(RequestOptions.circleCropTransform())
                                .placeholder(R.mipmap.ic_launcher_round)
                                .error(R.mipmap.ic_launcher_round)
                                .into(ivProfileTeamShield);
                    }

                    tvProfileStatus.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    tvProfileFavTeamValue.setText(getString(R.string.profile_value_not_available));
                    tvProfileHeaderTeam.setText(getString(R.string.profile_value_not_available));
                    ivProfileTeamShield.setImageResource(R.mipmap.ic_launcher_round);
                    setExperienciaUI(1L, 0L);
                    tvProfileStatus.setVisibility(View.VISIBLE);
                    tvProfileStatus.setText(R.string.profile_status_error_load);
                });
    }

    private void setExperienciaUI(@Nullable Long rango, @Nullable Long experienciaTotal) {
        long nivel = (rango == null || rango < 1L) ? 1L : rango;
        long xpTotal = (experienciaTotal == null || experienciaTotal < 0L) ? 0L : experienciaTotal;

        long xpActualNivel = xpTotal % 1000L;
        long xpParaSiguiente = 1000L;
        int progress = (int) Math.min(100L, Math.max(0L, (xpActualNivel * 100L) / xpParaSiguiente));

        tvProfileLevelValue.setText(String.valueOf(nivel));
        tvProfileExperienceValue.setText(getString(R.string.profile_experience_value_format, xpTotal));
        tvProfileExperienceProgress.setText(
                getString(
                        R.string.profile_experience_progress_format,
                        xpActualNivel,
                        xpParaSiguiente,
                        (nivel + 1L)
                )
        );
        pbProfileExperience.setProgress(progress);
    }

    @Nullable
    private Object construirModeloSolicitudEscudo(@Nullable String escudoUrl) {
        if (escudoUrl == null) {
            return null;
        }

        String normalizedUrl = escudoUrl.trim();
        if (normalizedUrl.isEmpty()) {
            return null;
        }

        String schemePrefix = "https://";
        int atIndex = normalizedUrl.indexOf('@');

        if (normalizedUrl.startsWith(schemePrefix)
                && atIndex > schemePrefix.length()
                && normalizedUrl.contains("@raw.githubusercontent.com/")) {
            String token = normalizedUrl.substring(schemePrefix.length(), atIndex);
            String cleanUrl = schemePrefix + normalizedUrl.substring(atIndex + 1);
            String basicAuth = Base64.encodeToString(
                    ("x-access-token:" + token).getBytes(),
                    Base64.NO_WRAP
            );

            return new GlideUrl(
                    cleanUrl,
                    new LazyHeaders.Builder()
                            .addHeader("Authorization", "Basic " + basicAuth)
                            .addHeader("User-Agent", "SoccerExplorer")
                            .build()
            );
        }

        return normalizedUrl;
    }
}
