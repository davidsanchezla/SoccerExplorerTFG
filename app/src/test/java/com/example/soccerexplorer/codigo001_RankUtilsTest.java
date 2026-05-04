package com.example.soccerexplorer;

import org.junit.Test;
import static org.junit.Assert.*;

public class codigo001_RankUtilsTest {
    // Codigo: 001
    // Por qué: validar la normalización de rango y el cálculo de rangos desde XP,
    // porque influyen en la presentación de niveles en la aplicación.

    @Test
    public void normalizarRango_limites() {
        assertEquals(1L, RankUtils.normalizarRango(0L));
        assertEquals(1L, RankUtils.normalizarRango(1L));
        assertEquals(RankUtils.MAX_RANGO, RankUtils.normalizarRango(999L));
    }

    @Test
    public void calcularRangoDesdeXp_bordes() {
        assertEquals(1L, RankUtils.calcularRangoDesdeXp(0L));
        assertEquals(2L, RankUtils.calcularRangoDesdeXp(100L));
        assertEquals(3L, RankUtils.calcularRangoDesdeXp(300L));
        assertEquals(4L, RankUtils.calcularRangoDesdeXp(600L));
        assertEquals(5L, RankUtils.calcularRangoDesdeXp(1000L));
        assertEquals(6L, RankUtils.calcularRangoDesdeXp(1500L));
        assertEquals(7L, RankUtils.calcularRangoDesdeXp(3000L));
        assertEquals(7L, RankUtils.calcularRangoDesdeXp(99999L));
    }

    @Test
    public void rankTier_progressAndMax() {
        RankUtils.RankTier tMax = RankUtils.tierDesdeRango(7L);
        assertTrue(tMax.isMaxRank());
        assertEquals(1L, tMax.progressCurrent());

        RankUtils.RankTier tMid = RankUtils.tierDesdeRango(3L);
        assertFalse(tMid.isMaxRank());
        assertTrue(tMid.progressCurrent() >= 1L);
    }
}
