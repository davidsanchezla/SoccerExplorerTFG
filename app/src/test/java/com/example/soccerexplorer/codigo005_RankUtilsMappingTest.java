package com.example.soccerexplorer;

import org.junit.Test;
import static org.junit.Assert.*;

public class codigo005_RankUtilsMappingTest {
    // Codigo: 005
    // Por qué: verificar mapeos de recursos y anchos de borde según rango.

    @Test
    public void borderWidth_and_string_drawable_mappings() {
        // border widths
        assertEquals(2f, RankUtils.borderWidthDpDesdeRango(1L), 0.0f);
        assertEquals(2f, RankUtils.borderWidthDpDesdeRango(2L), 0.0f);
        assertEquals(3f, RankUtils.borderWidthDpDesdeRango(3L), 0.0f);
        assertEquals(3f, RankUtils.borderWidthDpDesdeRango(4L), 0.0f);
        assertEquals(4f, RankUtils.borderWidthDpDesdeRango(5L), 0.0f);
        assertEquals(4f, RankUtils.borderWidthDpDesdeRango(6L), 0.0f);
        assertEquals(5f, RankUtils.borderWidthDpDesdeRango(7L), 0.0f);

        // string resource ids (only checking they are positive ints)
        assertTrue(RankUtils.stringNameResDesdeRango(1L) > 0);
        assertTrue(RankUtils.stringNameResDesdeRango(7L) > 0);

        // drawable resource ids
        assertTrue(RankUtils.drawableDesdeRango(1L) > 0);
        assertTrue(RankUtils.drawableDesdeRango(7L) > 0);
    }
}
