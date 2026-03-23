package com.example.soccerexplorer;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.ColorRes;

final class RankUtils {

    static final int MAX_RANGO = 7;

    private RankUtils() {
    }

    static long normalizarRango(long rango) {
        if (rango < 1L) {
            return 1L;
        }
        if (rango > MAX_RANGO) {
            return MAX_RANGO;
        }
        return rango;
    }

    static long calcularRangoDesdeXp(long xp) {
        if (xp >= 3000L) {
            return 7L;
        }
        if (xp >= 1500L) {
            return 6L;
        }
        if (xp >= 1000L) {
            return 5L;
        }
        if (xp >= 600L) {
            return 4L;
        }
        if (xp >= 300L) {
            return 3L;
        }
        if (xp >= 100L) {
            return 2L;
        }
        return 1L;
    }

    @NonNull
    static RankTier tierDesdeRango(long rango) {
        long r = normalizarRango(rango);
        if (r == 1L) {
            return new RankTier(1L, 0L, 99L);
        }
        if (r == 2L) {
            return new RankTier(2L, 100L, 299L);
        }
        if (r == 3L) {
            return new RankTier(3L, 300L, 599L);
        }
        if (r == 4L) {
            return new RankTier(4L, 600L, 999L);
        }
        if (r == 5L) {
            return new RankTier(5L, 1000L, 1499L);
        }
        if (r == 6L) {
            return new RankTier(6L, 1500L, 2999L);
        }
        return new RankTier(7L, 3000L, Long.MAX_VALUE);
    }

    @DrawableRes
    static int drawableDesdeRango(long rango) {
        long r = normalizarRango(rango);
        if (r == 1L) {
            return R.drawable.canterano;
        }
        if (r == 2L) {
            return R.drawable.amateur;
        }
        if (r == 3L) {
            return R.drawable.profesional;
        }
        if (r == 4L) {
            return R.drawable.estrella;
        }
        if (r == 5L) {
            return R.drawable.elite;
        }
        if (r == 6L) {
            return R.drawable.maestro;
        }
        return R.drawable.leyenda;
    }

    static int stringNameResDesdeRango(long rango) {
        long r = normalizarRango(rango);
        if (r == 1L) {
            return R.string.rank_canterano;
        }
        if (r == 2L) {
            return R.string.rank_amateur;
        }
        if (r == 3L) {
            return R.string.rank_profesional;
        }
        if (r == 4L) {
            return R.string.rank_estrella;
        }
        if (r == 5L) {
            return R.string.rank_elite;
        }
        if (r == 6L) {
            return R.string.rank_maestro;
        }
        return R.string.rank_leyenda;
    }

    @ColorRes
    static int borderColorResDesdeRango(long rango) {
        long r = normalizarRango(rango);
        if (r == 1L) {
            return R.color.rank_border_canterano;
        }
        if (r == 2L) {
            return R.color.rank_border_amateur;
        }
        if (r == 3L) {
            return R.color.rank_border_profesional;
        }
        if (r == 4L) {
            return R.color.rank_border_estrella;
        }
        if (r == 5L) {
            return R.color.rank_border_elite;
        }
        if (r == 6L) {
            return R.color.rank_border_maestro;
        }
        return R.color.rank_border_leyenda;
    }

    static float borderWidthDpDesdeRango(long rango) {
        long r = normalizarRango(rango);
        if (r <= 2L) {
            return 2f;
        }
        if (r <= 4L) {
            return 3f;
        }
        if (r <= 6L) {
            return 4f;
        }
        return 5f;
    }

    static final class RankTier {
        final long rank;
        final long minXp;
        final long maxXp;

        RankTier(long rank, long minXp, long maxXp) {
            this.rank = rank;
            this.minXp = minXp;
            this.maxXp = maxXp;
        }

        boolean isMaxRank() {
            return rank >= MAX_RANGO;
        }

        long progressCurrent() {
            if (isMaxRank()) {
                return 1L;
            }
            return Math.max(1L, (maxXp - minXp + 1L));
        }
    }
}
