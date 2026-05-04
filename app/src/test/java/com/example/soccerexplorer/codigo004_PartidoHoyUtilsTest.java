package com.example.soccerexplorer;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.*;

public class codigo004_PartidoHoyUtilsTest {
    // Codigo: 004
    // Por qué: validar utilidades de parsing/formateo usadas por PartidoHoyWorker.

    @Test
    public void normalizarTexto_removesAccentsAndSymbols() {
        String s = "Real-Madrid CF.";
        assertEquals("real madrid cf", PartidoHoyUtils.normalizarTexto(s));
    }

    @Test
    public void coincideEquipo_variousCases() {
        assertTrue(PartidoHoyUtils.coincideEquipo("real madrid", "real madrid"));
        assertTrue(PartidoHoyUtils.coincideEquipo("real madrid", "real madrid cf"));
        assertFalse(PartidoHoyUtils.coincideEquipo("", "real"));
    }

    @Test
    public void formatearHora_validAndInvalid() {
        assertEquals("--:--", PartidoHoyUtils.formatearHora(""));
        String sampleUtc = "2023-10-12T18:30:00Z";
        String hhmm = PartidoHoyUtils.formatearHora(sampleUtc);
        assertTrue(hhmm.matches("\\d{2}:\\d{2}"));
    }

    @Test
    public void leerInputStream_readsCorrectly() throws Exception {
        String payload = "hello\nworld";
        String out = PartidoHoyUtils.leerInputStream(new ByteArrayInputStream(payload.getBytes()));
        // leerInputStream concatena líneas sin añadir separadores, así que el resultado será "helloworld"
        assertEquals("helloworld", out);
    }

    @Test
    public void encontrarPartidoEquipo_parsing() throws Exception {
        String body = "{\"matches\":[{\"utcDate\":\"2023-10-12T18:30:00Z\",\"homeTeam\":{\"name\":\"Real Madrid\"},\"awayTeam\":{\"name\":\"Barca\"}}]}";
        JSONObject res = PartidoHoyUtils.encontrarPartidoEquipo(body, "Real Madrid");
        assertNotNull(res);
        assertEquals("Real Madrid", res.getJSONObject("homeTeam").getString("name"));
    }
}
