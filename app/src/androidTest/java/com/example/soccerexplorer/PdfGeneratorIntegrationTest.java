package com.example.soccerexplorer;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class PdfGeneratorIntegrationTest {

    private File created = null;

    @Test
    public void generateHistorialPdf_createsFile_onDevice() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PdfGenerator gen = new PdfGenerator(ctx);

        QuinielaHistorialAdapter.PartidoItem p = new QuinielaHistorialAdapter.PartidoItem(
                "m1", "HomeTeam", "AwayTeam", "", "", "1", 1, 0, "1-0", true);
        QuinielaHistorialAdapter.QuinielaItem q = new QuinielaHistorialAdapter.QuinielaItem(
                "q1", "Jornada 1", "S1", "PD", 1, 1, 10, true, Arrays.asList(p));

        File f = gen.generateHistorialPdf("instrumented@test", Arrays.asList(q));
        created = f;

        assertNotNull(f);
        assertTrue(f.exists());
        assertTrue(f.length() > 0);

        gen.shutdown();
    }

    @After
    public void cleanup() {
        if (created != null && created.exists()) created.delete();
    }
}
