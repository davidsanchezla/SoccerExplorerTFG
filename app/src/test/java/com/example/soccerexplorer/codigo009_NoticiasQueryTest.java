package com.example.soccerexplorer;

import org.junit.Test;
import static org.junit.Assert.*;

public class codigo009_NoticiasQueryTest {
    // Codigo: 009
    // Por qué: comprobar mapeos de nombres de equipo a queries complejas.

    @Test
    public void construirQueryEquipo_specialCases() throws Exception {
        NoticiasFragment f = new NoticiasFragment();
        java.lang.reflect.Method m = NoticiasFragment.class.getDeclaredMethod("construirQueryEquipo", String.class);
        m.setAccessible(true);

        String valencia = (String) m.invoke(f, "Valencia");
        assertTrue(valencia.contains("Valencia CF") || valencia.toLowerCase().contains("valencia"));

        String barca = (String) m.invoke(f, "Barcelona");
        assertTrue(barca.contains("FC Barcelona") || barca.toLowerCase().contains("barca"));

        String other = (String) m.invoke(f, "SomeTown");
        assertEquals("\"SomeTown\"", other);
    }
}
