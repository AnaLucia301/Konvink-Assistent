package com.konvink.assistant.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AufgabenExtraktionTest {

    @Test
    void erkenntNummerierteZeilen() {
        String text = "Einleitung\n1. Beschreibe den Ablauf im Betrieb\n2. Erlaeutere die wichtigsten Kunden\nSchluss";

        List<String> aufgaben = DetailView.extrahiereAufgaben(text);

        assertEquals(2, aufgaben.size());
        assertTrue(aufgaben.get(0).startsWith("1."));
    }

    @Test
    void nullUndLeerSindUnkritisch() {
        assertTrue(DetailView.extrahiereAufgaben(null).isEmpty());
        assertTrue(DetailView.extrahiereAufgaben("").isEmpty());
    }

    @Test
    void doppelteZeilenWerdenEntfernt() {
        String z = "- Immer dieselbe Aufgabe hier";
        assertEquals(1, DetailView.extrahiereAufgaben(z + "\n" + z).size());
    }
}
