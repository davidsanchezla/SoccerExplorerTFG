package com.example.soccerexplorer;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.*;

public class codigo011_NoticiasFilterTest {
    // Codigo: 011
    // Por qué: comprobar los métodos de filtrado de texto (contiene terminos y esNoticiaRelacionada...).

    @Test
    public void contieneAlgunTermino_and_esNoticiaRelacionada() throws Exception {
        NoticiasFragment f = new NoticiasFragment();
        Method contieneList = NoticiasFragment.class.getDeclaredMethod("contieneAlgunTermino", String.class, java.util.List.class);
        contieneList.setAccessible(true);

        boolean res = (boolean) contieneList.invoke(f, "este texto menciona real madrid en el estadio", Arrays.asList("real madrid"));
        assertTrue(res);

        Method esRel = NoticiasFragment.class.getDeclaredMethod("esNoticiaRelacionadaConEquipoYFutbol", String.class, String.class, String.class, java.util.List.class);
        esRel.setAccessible(true);
        boolean rel = (boolean) esRel.invoke(f, "Real Madrid scores", "desc", "contenido gol", Arrays.asList("real madrid"));
        assertTrue(rel);
    }
}
