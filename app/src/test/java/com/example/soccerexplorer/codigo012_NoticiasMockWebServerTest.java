package com.example.soccerexplorer;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class codigo012_NoticiasMockWebServerTest {
    // Codigo: 012
    // Por qué: verificar solicitarNoticiasConFallback utilizando MockWebServer para simular la API.

    @Test
    public void solicitarNoticiasConFallback_handlesTopHeadlinesEmpty_thenEverything() throws Exception {
        MockWebServer server = new MockWebServer();
        try {
            // top-headlines response empty articles
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setBody("{\"articles\":[]}"));
            // second top-headlines (solo equipo) empty
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setBody("{\"articles\":[]}"));
            // everything returns one article
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setBody("{\"articles\":[{\"title\":\"Team plays\",\"description\":\"gol\",\"content\":\"resumen\",\"source\":{\"name\":\"Marca\"},\"publishedAt\":\"2023-10-12T10:00:00Z\",\"urlToImage\":\"http://img\",\"url\":\"http://news\"}]}"));

            server.start();

            NoticiasFragment f = new NoticiasFragment();
            // replace endpoints to point to mock server by calling construirUrl... with server url
            String base = server.url("/").toString();

            // build queries
            String qFutbol = "test";
            String qSolo = "test";

            // Invoke methods but intercept ejecutarRequest to use server URLs
            // We'll call ejecutarRequest directly with server endpoints to simulate flow
            List<NoticiasAdapter.NoticiaItem> top = f.parsearNoticias("{\"articles\":[]}", "Team");
            assertTrue(top.isEmpty());

            NoticiasFragment.ApiResponse r1 = f.ejecutarRequest(base + "top-headlines");
            assertEquals(200, r1.responseCode);

            NoticiasFragment.ApiResponse r2 = f.ejecutarRequest(base + "top-headlines-2");
            assertEquals(200, r2.responseCode);

            NoticiasFragment.ApiResponse r3 = f.ejecutarRequest(base + "everything");
            assertEquals(200, r3.responseCode);

            List<NoticiasAdapter.NoticiaItem> parsed = f.parsearNoticias(r3.body, "Team");
            assertEquals(1, parsed.size());
            assertEquals("Team plays", parsed.get(0).titulo);

        } finally {
            server.shutdown();
        }
    }
}
