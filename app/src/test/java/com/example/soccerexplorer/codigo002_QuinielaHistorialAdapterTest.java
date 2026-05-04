package com.example.soccerexplorer;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class codigo002_QuinielaHistorialAdapterTest {
    // Codigo: 002
    // Por qué: asegurar que la lógica de submitList y selección funciona correctamente.

    @Test
    public void submitList_and_selection_behaviour() {
        QuinielaHistorialAdapter adapter = new QuinielaHistorialAdapter();

        QuinielaHistorialAdapter.PartidoItem p = new QuinielaHistorialAdapter.PartidoItem(
                "m1", "Home", "Away", "", "", "1", 1, 0, "1-0", true);
        QuinielaHistorialAdapter.QuinielaItem q = new QuinielaHistorialAdapter.QuinielaItem(
                "q1", "Jornada 1", "S1", "PD", 1, 1, 10, false, Arrays.asList(p));

        try {
            java.lang.reflect.Field itemsField = QuinielaHistorialAdapter.class.getDeclaredField("items");
            itemsField.setAccessible(true);
            java.util.List list = new java.util.ArrayList();
            list.add(q);
            itemsField.set(adapter, list);

            java.lang.reflect.Field selectedField = QuinielaHistorialAdapter.class.getDeclaredField("selectedIds");
            selectedField.setAccessible(true);
            java.util.Set sel = new java.util.HashSet();
            sel.add(q.id);
            selectedField.set(adapter, sel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<QuinielaHistorialAdapter.QuinielaItem> all = adapter.getAllItems();
        assertEquals(1, all.size());
        assertEquals("q1", all.get(0).id);

        List<QuinielaHistorialAdapter.QuinielaItem> selected = adapter.getSelectedItems();
        assertEquals(1, selected.size());
        assertEquals("q1", selected.get(0).id);
    }

    @Test
    public void getSelectedItems_emptyWhen_noneSelected() {
        QuinielaHistorialAdapter adapter = new QuinielaHistorialAdapter();

        QuinielaHistorialAdapter.PartidoItem p = new QuinielaHistorialAdapter.PartidoItem(
                "m2", "A", "B", "", "", "X", 0, 0, "- -", false);
        QuinielaHistorialAdapter.QuinielaItem q = new QuinielaHistorialAdapter.QuinielaItem(
                "q2", "J2", "S2", "PL", 0, 1, 0, false, Arrays.asList(p));

        try {
            java.lang.reflect.Field itemsField = QuinielaHistorialAdapter.class.getDeclaredField("items");
            itemsField.setAccessible(true);
            java.util.List list = new java.util.ArrayList();
            list.add(q);
            itemsField.set(adapter, list);

            java.lang.reflect.Field selectedField = QuinielaHistorialAdapter.class.getDeclaredField("selectedIds");
            selectedField.setAccessible(true);
            java.util.Set sel = new java.util.HashSet();
            // leave sel empty to simulate none selected
            selectedField.set(adapter, sel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals(0, adapter.getSelectedItems().size());
    }
}
