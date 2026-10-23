package com.konvink.assistant.service;

import com.konvink.assistant.model.Praxisauftrag;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Verteilt Praxisauftraege auf Terminvorschlaege (ein Termin pro Woche, optional mit Outlook-Abgleich). */
public final class Terminplaner {

    private static final Logger LOGGER = Logger.getLogger(Terminplaner.class.getName());

    /** Wochen zwischen zwei Terminvorschlaegen. */
    private static final int INTERVALL_WOCHEN = 1;

    /** Ab dieser Belegung (Minuten) im Outlook-Kalender gilt ein Tag als "voll". */
    private static final int BUSY_SCHWELLE_MINUTEN = 180;

    private Terminplaner() {
    }

    /** Ohne Outlook: ein Termin pro Woche ab dem naechsten Werktag. */
    public static Map<LocalDate, Praxisauftrag> berechne(List<Praxisauftrag> auftraege) {
        return einfach(auftraege);
    }

    private static Map<LocalDate, Praxisauftrag> mitOutlook(List<Praxisauftrag> auftraege) {
        Map<LocalDate, Praxisauftrag> termine = new LinkedHashMap<>();
        if (auftraege.isEmpty()) {
            return termine;
        }
        LinkedList<Praxisauftrag> verbleibend = new LinkedList<>(auftraege);
        verbleibend.sort(Comparator.comparingInt(a -> a.getRohtext().length()));

        LocalDate datum = LocalDate.now().plusDays(1);
        long schritt = 7L * INTERVALL_WOCHEN;
        while (!verbleibend.isEmpty()) {
            datum = naechsterWerktag(datum);
            int belegt;
            try {
                belegt = OutlookIntegration.getBusyMinutesForDate(datum);
            } catch (Throwable e) {
                LOGGER.log(Level.FINE, "Outlook-Abfrage fehlgeschlagen fuer " + datum, e);
                belegt = 0;
            }
            // voller Tag -> kleiner Auftrag, freier Tag -> groesserer Auftrag
            Praxisauftrag a = belegt >= BUSY_SCHWELLE_MINUTEN ? verbleibend.removeFirst() : verbleibend.removeLast();
            termine.put(datum, a);
            datum = datum.plusDays(schritt);
        }
        return termine;
    }

    private static Map<LocalDate, Praxisauftrag> einfach(List<Praxisauftrag> auftraege) {
        Map<LocalDate, Praxisauftrag> termine = new LinkedHashMap<>();
        LocalDate datum = LocalDate.now().plusDays(1);
        long schritt = 7L * INTERVALL_WOCHEN;
        for (Praxisauftrag a : auftraege) {
            datum = naechsterWerktag(datum);
            termine.put(datum, a);
            datum = datum.plusDays(schritt);
        }
        return termine;
    }

    private static LocalDate naechsterWerktag(LocalDate d) {
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }
}
