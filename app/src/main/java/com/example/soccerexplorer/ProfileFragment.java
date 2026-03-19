package com.example.soccerexplorer;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private TextView tvProfileEmailValue;
    private TextView tvProfileFavTeamValue;
    private TextView tvProfileStatus;
    private TextView tvProfileHeaderTeam;
    private TextView tvProfileLevelValue;
    private TextView tvProfileExperienceValue;
    private TextView tvProfileExperienceProgress;
    private FrameLayout flProfileAvatarShirt;
    private AppCompatImageView ivProfileAvatarBase;
    private AppCompatImageView ivProfileAvatarStripes;
    private AppCompatImageView ivProfileTeamShield;
    private MaterialCardView cardProfileAvatar;
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
        flProfileAvatarShirt = view.findViewById(R.id.flProfileAvatarShirt);
        ivProfileAvatarBase = view.findViewById(R.id.ivProfileAvatarBase);
        ivProfileAvatarStripes = view.findViewById(R.id.ivProfileAvatarStripes);
        ivProfileTeamShield = view.findViewById(R.id.ivProfileTeamShield);
        cardProfileAvatar = view.findViewById(R.id.cardProfileAvatar);
        pbProfileExperience = view.findViewById(R.id.pbProfileExperience);
        btnProfileQuiniela = view.findViewById(R.id.btnProfileQuiniela);
        btnProfileChangeTeam = view.findViewById(R.id.btnProfileChangeTeam);
        btnProfileLogout = view.findViewById(R.id.btnProfileLogout);

        aplicarEstiloCamiseta(null, null);

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
            aplicarEstiloCamiseta(null, null);
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
                    String equipoId = documentSnapshot.getString("equipoFavoritoId");
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
                    aplicarEstiloCamiseta(equipoId, equipo);

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
                    aplicarEstiloCamiseta(null, null);
                    tvProfileStatus.setVisibility(View.VISIBLE);
                    tvProfileStatus.setText(R.string.profile_status_error_load);
                });
    }

    private void setExperienciaUI(@Nullable Long rango, @Nullable Long experienciaTotal) {
        long nivel = RankUtils.normalizarRango(rango == null ? 1L : rango);
        long xpTotal = (experienciaTotal == null || experienciaTotal < 0L) ? 0L : experienciaTotal;

        RankUtils.RankTier tier = RankUtils.tierDesdeRango(nivel);
        String rankName = getString(RankUtils.stringNameResDesdeRango(nivel));

        long xpProgresoActual;
        long xpParaSiguiente;
        int progress;

        if (tier.isMaxRank()) {
            xpProgresoActual = 1L;
            xpParaSiguiente = 1L;
            progress = 100;
            tvProfileExperienceProgress.setText(
                    getString(R.string.profile_experience_progress_max_format, xpTotal)
            );
        } else {
            long rangoMin = tier.minXp;
            long rangoMax = tier.maxXp;
            long xpClamped = Math.max(rangoMin, Math.min(xpTotal, rangoMax + 1L));
            xpProgresoActual = (xpClamped - rangoMin);
            xpParaSiguiente = tier.progressCurrent();
            progress = (int) Math.min(100L, Math.max(0L, (xpProgresoActual * 100L) / xpParaSiguiente));

            long siguienteRango = RankUtils.normalizarRango(nivel + 1L);
            String siguienteNombre = getString(RankUtils.stringNameResDesdeRango(siguienteRango));
            tvProfileExperienceProgress.setText(
                    getString(
                            R.string.profile_experience_progress_format,
                            xpProgresoActual,
                            xpParaSiguiente,
                            siguienteNombre
                    )
            );
        }

        tvProfileLevelValue.setText(getString(R.string.profile_rank_with_number_format, rankName, nivel));
        tvProfileExperienceValue.setText(getString(R.string.profile_experience_value_format, xpTotal));

        int strokeColor = ContextCompat.getColor(requireContext(), RankUtils.borderColorResDesdeRango(nivel));
        int strokeWidthPx = (int) (RankUtils.borderWidthDpDesdeRango(nivel)
                * requireContext().getResources().getDisplayMetrics().density);
        cardProfileAvatar.setStrokeColor(strokeColor);
        cardProfileAvatar.setStrokeWidth(strokeWidthPx);

        pbProfileExperience.setProgress(progress);
    }

    private void aplicarEstiloCamiseta(@Nullable String equipoId, @Nullable String equipoNombre) {
        TeamPalette colores = obtenerColoresCamiseta(equipoId, equipoNombre);

        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{colores.topColor, colores.bottomColor}
        );
        gradient.setShape(GradientDrawable.OVAL);
        flProfileAvatarShirt.setBackground(gradient);

        ivProfileAvatarBase.setImageTintList(ColorStateList.valueOf(colores.baseColor));
        ivProfileAvatarStripes.setImageTintList(ColorStateList.valueOf(colores.stripeColor));
    }

    @NonNull
    private TeamPalette obtenerColoresCamiseta(@Nullable String equipoId, @Nullable String equipoNombre) {
        String keyId = equipoId == null ? "" : equipoId.trim().toLowerCase(Locale.ROOT);
        String keyName = equipoNombre == null ? "" : equipoNombre.trim().toLowerCase(Locale.ROOT);

        if (keyId.contains("atlmadrid")
                || keyName.contains("atletico")
                || keyName.contains("atlético")) {
            return TeamPalette.of(0xFF0C2340, 0xFF1D3557, 0xFFFFFFFF, 0xFFC8102E);
        }
        if (keyId.contains("realmadrid") || keyName.contains("real madrid")) {
            return TeamPalette.of(0xFFEDF2FA, 0xFFC9D8F2, 0xFFFFFFFF, 0xFF0A1B4D);
        }
        if (keyId.contains("barcelona") || keyName.contains("barcelona") || keyName.contains("barca")) {
            return TeamPalette.of(0xFF38003A, 0xFF004D98, 0xFFA50044, 0xFF004D98);
        }
        if (keyId.contains("valencia") || keyName.contains("valencia")) {
            return TeamPalette.of(0xFF2B2B2B, 0xFF4A4A4A, 0xFFFFFFFF, 0xFF111111);
        }
        if (keyId.contains("chelsea") || keyName.contains("chelsea")) {
            return TeamPalette.of(0xFF023E7D, 0xFF022A5E, 0xFF034694, 0xFFFFFFFF);
        }
        if (keyId.contains("mancity") || keyId.contains("man_city") || keyName.contains("manchester city")) {
            return TeamPalette.of(0xFF5AA9E6, 0xFF8DCFF0, 0xFF6CABDD, 0xFF1C4E80);
        }
        if (keyId.contains("liverpool") || keyName.contains("liverpool")) {
            return TeamPalette.of(0xFF9E1B32, 0xFF7A1323, 0xFFC8102E, 0xFFF6F6F6);
        }
        if (keyId.contains("bayern") || keyName.contains("bayern")) {
            return TeamPalette.of(0xFF9B1C3F, 0xFF0D2B6B, 0xFFFFFFFF, 0xFFDC052D);
        }
        if (keyId.contains("juventus") || keyName.contains("juventus")) {
            return TeamPalette.of(0xFF303030, 0xFF111111, 0xFFFFFFFF, 0xFF111111);
        }

        return TeamPalette.of(
                ContextCompat.getColor(requireContext(), R.color.primary),
                ContextCompat.getColor(requireContext(), R.color.primary_variant),
                0xFFFFFFFF,
                ContextCompat.getColor(requireContext(), R.color.background_primary)
        );
    }

    private static final class TeamPalette {
        final int topColor;
        final int bottomColor;
        final int baseColor;
        final int stripeColor;

        private TeamPalette(int topColor, int bottomColor, int baseColor, int stripeColor) {
            this.topColor = topColor;
            this.bottomColor = bottomColor;
            this.baseColor = baseColor;
            this.stripeColor = stripeColor;
        }

        @NonNull
        static TeamPalette of(int topColor, int bottomColor, int baseColor, int stripeColor) {
            return new TeamPalette(topColor, bottomColor, baseColor, stripeColor);
        }
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
