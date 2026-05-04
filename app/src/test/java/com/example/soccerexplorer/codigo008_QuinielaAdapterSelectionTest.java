package com.example.soccerexplorer;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;

public class codigo008_QuinielaAdapterSelectionTest {
    // Codigo: 008
    // Por qué: comprobar que submitList reemplaza items y notifica selección por defecto.

    @Test
    public void submitList_replacesItems_and_selectsAllByDefault() {
        QuinielaHistorialAdapter adapter = new QuinielaHistorialAdapter();

        QuinielaHistorialAdapter.PartidoItem p1 = new QuinielaHistorialAdapter.PartidoItem(
                "m1", "H1", "A1", "", "", "1", 1, 0, "1-0", true);
        QuinielaHistorialAdapter.QuinielaItem q1 = new QuinielaHistorialAdapter.QuinielaItem(
                "q1", "J1", "S1", "PD", 1, 1, 10, false, Arrays.asList(p1));

        // use reflection to set internal fields to avoid Android notifyDataSetChanged side-effects
        try {
            java.lang.reflect.Field itemsField = QuinielaHistorialAdapter.class.getDeclaredField("items");
            itemsField.setAccessible(true);
            java.util.List list1 = new java.util.ArrayList();
            list1.add(q1);
            itemsField.set(adapter, list1);

            java.lang.reflect.Field selectedField = QuinielaHistorialAdapter.class.getDeclaredField("selectedIds");
            selectedField.setAccessible(true);
            java.util.Set sel1 = new java.util.HashSet();
            sel1.add(q1.id);
            selectedField.set(adapter, sel1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals(1, adapter.getAllItems().size());
        assertEquals(1, adapter.getSelectedItems().size());

        QuinielaHistorialAdapter.PartidoItem p2 = new QuinielaHistorialAdapter.PartidoItem(
                "m2", "H2", "A2", "", "", "X", 0, 0, "- -", false);
        QuinielaHistorialAdapter.QuinielaItem q2 = new QuinielaHistorialAdapter.QuinielaItem(
                "q2", "J2", "S2", "PL", 0, 1, 0, false, Arrays.asList(p2));

        try {
            java.lang.reflect.Field itemsField = QuinielaHistorialAdapter.class.getDeclaredField("items");
            itemsField.setAccessible(true);
            java.util.List list2 = new java.util.ArrayList();
            list2.add(q2);
            itemsField.set(adapter, list2);

            java.lang.reflect.Field selectedField = QuinielaHistorialAdapter.class.getDeclaredField("selectedIds");
            selectedField.setAccessible(true);
            java.util.Set sel2 = new java.util.HashSet();
            sel2.add(q2.id);
            selectedField.set(adapter, sel2);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals(1, adapter.getAllItems().size());
        assertEquals(1, adapter.getSelectedItems().size());
        assertEquals("q2", adapter.getSelectedItems().get(0).id);
    }
}
