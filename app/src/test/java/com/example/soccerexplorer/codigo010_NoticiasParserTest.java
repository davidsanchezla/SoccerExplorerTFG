package com.example.soccerexplorer;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class codigo010_NoticiasParserTest {
    // Codigo: 010
    // Por qué: validar parseo de respuestas de la API y filtrado por equipo/futbol.

    @Test
    public void parsearNoticias_filtersAndParses() throws Exception {
        NoticiasFragment f = new NoticiasFragment();
        java.lang.reflect.Method m = NoticiasFragment.class.getDeclaredMethod("parsearNoticias", String.class, String.class);
        m.setAccessible(true);

        String body = new JSONObject()
                .put("articles", new org.json.JSONArray()
                        .put(new JSONObject()
                                .put("title", "Real Madrid wins")
                                .put("description", "Great gol en el estadio")
                                .put("content", "Resumen del partido")
                                .put("source", new JSONObject().put("name", "Marca"))
                                .put("publishedAt", "2023-10-12T10:00:00Z")
                                .put("urlToImage", "http://img")
                                .put("url", "http://news")))
                .toString();

        List<NoticiasAdapter.NoticiaItem> list = (List<NoticiasAdapter.NoticiaItem>) m.invoke(f, body, "Real Madrid");
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("Real Madrid wins", list.get(0).titulo);
    }
}
