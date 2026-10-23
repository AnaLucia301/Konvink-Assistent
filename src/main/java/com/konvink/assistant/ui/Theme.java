package com.konvink.assistant.ui;

import com.konvink.assistant.store.AppStore;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zentrale Farbpalette. Hell/Dunkel und die Akzentfarbe kommen aus den
 * gespeicherten Einstellungen (AppStore) und werden beim Start einmalig
 * eingelesen - ein Wechsel wird deshalb mit einem Neustart der App wirksam.
 */
public final class Theme {

    private Theme() {
    }

    /** Auswaehlbare Akzentfarben: Name -> {dunkel, hell}. */
    public static final Map<String, Color[]> AKZENT_AUSWAHL = new LinkedHashMap<>();

    static {
        AKZENT_AUSWAHL.put("Violett", new Color[]{new Color(0x9B8CFF), new Color(0x6D5BE0)});
        AKZENT_AUSWAHL.put("Blau", new Color[]{new Color(0x6EC8F5), new Color(0x1E88D6)});
        AKZENT_AUSWAHL.put("Mint", new Color[]{new Color(0x7FDCC0), new Color(0x1FA37F)});
        AKZENT_AUSWAHL.put("Pfirsich", new Color[]{new Color(0xF5B888), new Color(0xE08A3C)});
        AKZENT_AUSWAHL.put("Rosa", new Color[]{new Color(0xF29BC4), new Color(0xD9588F)});
    }

    public static final boolean DUNKEL = AppStore.get().settings().dunkel;

    // Flaechen
    public static final Color BG_APP = DUNKEL ? new Color(0x15112B) : new Color(0xD9D6E6);
    public static final Color SURFACE = DUNKEL ? new Color(0x1E1940) : new Color(0xE8E5F1);
    public static final Color CARD = DUNKEL ? new Color(0x2B2557) : new Color(0xD7D2EA);
    public static final Color CARD_SELECTED = DUNKEL ? new Color(0x3F3680) : new Color(0xC3BAE6);
    public static final Color BORDER = DUNKEL ? new Color(0x4C4488) : new Color(0xB7B0D6);
    public static final Color PDF_BG = DUNKEL ? new Color(0x0F0C20) : new Color(0xBFBBD3);

    // Text
    public static final Color TEXT = DUNKEL ? new Color(0xF1EFFA) : new Color(0x241F3D);
    public static final Color MUTED = DUNKEL ? new Color(0xA9A4D0) : new Color(0x6B6592);

    // Akzente
    public static final Color ACCENT;
    public static final Color CYAN = DUNKEL ? new Color(0x6EC8F5) : new Color(0x1E88D6);
    public static final Color PINK = DUNKEL ? new Color(0xF29BC4) : new Color(0xD9588F);
    public static final Color LIME = DUNKEL ? new Color(0x7FDCC0) : new Color(0x1FA37F);
    public static final Color ORANGE = DUNKEL ? new Color(0xF5B888) : new Color(0xE08A3C);
    public static final Color VIOLETT = DUNKEL ? new Color(0x9B8CFF) : new Color(0x6D5BE0);

    /** Wechselnde Akzentfarben fuer Karten/Badges - beginnt mit der gewaehlten Akzentfarbe. */
    public static final Color[] AKZENTE;

    static {
        Color[] gewaehlt = AKZENT_AUSWAHL.getOrDefault(AppStore.get().settings().akzent,
                AKZENT_AUSWAHL.get("Violett"));
        ACCENT = DUNKEL ? gewaehlt[0] : gewaehlt[1];

        List<Color> liste = new ArrayList<>();
        liste.add(ACCENT);
        for (Color c : new Color[]{VIOLETT, CYAN, LIME, ORANGE, PINK}) {
            if (!c.equals(ACCENT)) {
                liste.add(c);
            }
        }
        AKZENTE = liste.toArray(new Color[0]);
    }

    public static Color akzent(int index) {
        return AKZENTE[Math.floorMod(index, AKZENTE.length)];
    }

    /** Textfarbe, die auf einer Akzentflaeche gut lesbar ist. */
    public static Color aufAkzent() {
        return DUNKEL ? BG_APP : Color.WHITE;
    }

    /** Akzentfarbe mit Transparenz. */
    public static Color mitAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
