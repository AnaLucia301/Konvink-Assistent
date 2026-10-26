package com.konvink.assistant.service;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Legt Termine/Zeitbloecke im Outlook-Kalender an, ueber COM-Automatisierung
 * (Windows-only). Java-Pendant zu outlook_integration.py (dort via
 * win32com.client.Dispatch, hier via JACOB).
 *
 * Erfordert die native jacob-*.dll im java.library.path.
 */
public final class OutlookIntegration {

    private static final Logger LOGGER = Logger.getLogger(OutlookIntegration.class.getName());

    private static final DateTimeFormatter OUTLOOK_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

    private static final int OL_APPOINTMENT_ITEM = 1;
    private static final int OL_MAIL_ITEM = 0;
    private static final int OL_BUSY = 2;
    private static final int OL_FREE = 0;
    private static final int OL_FOLDER_CALENDAR = 9;

    private OutlookIntegration() {
    }

    /**
     * Erstellt eine E-Mail in Outlook und sendet sie direkt.
     *
     * @param show falls true, wird die Mail nur geoeffnet (zur Kontrolle durch den Nutzer),
     *             statt automatisch gesendet zu werden.
     */
    public static void sendeMail(String an, String betreff, String text, boolean show) {
        ActiveXComponent outlook = new ActiveXComponent("Outlook.Application");
        try {
            Dispatch mail = Dispatch.call(outlook, "CreateItem", OL_MAIL_ITEM).toDispatch();
            Dispatch.put(mail, "To", an);
            Dispatch.put(mail, "Subject", betreff);
            Dispatch.put(mail, "Body", text == null ? "" : text);
            if (show) {
                Dispatch.call(mail, "Display");
            } else {
                Dispatch.call(mail, "Send");
            }
        } finally {
            outlook.safeRelease();
        }
    }

    /** Erstellt einen Termin in Outlook und speichert ihn direkt (ohne Anzeige). */
    public static void createTimeBlock(String subject, LocalDateTime start, int durationMinutes, String body) {
        createTimeBlockInternal(subject, start, durationMinutes, body, false);
    }

    /** Erstellt einen Termin in Outlook und oeffnet ihn zur Bearbeitung/Bestaetigung. */
    public static void createTimeBlockAndShow(String subject, LocalDateTime start, int durationMinutes, String body) {
        createTimeBlockInternal(subject, start, durationMinutes, body, true);
    }

    private static void createTimeBlockInternal(
            String subject, LocalDateTime start, int durationMinutes, String body, boolean show) {

        ActiveXComponent outlook = new ActiveXComponent("Outlook.Application");
        try {
            Dispatch appointment = Dispatch.call(outlook, "CreateItem",
                    OL_APPOINTMENT_ITEM).toDispatch();

            Dispatch.put(appointment, "Subject", subject);
            Dispatch.put(appointment, "Start", start.format(OUTLOOK_DATE_FORMAT));
            Dispatch.put(appointment, "Duration", durationMinutes);
            Dispatch.put(appointment, "Body", body == null ? "" : body);
            Dispatch.put(appointment, "BusyStatus", OL_BUSY);
            Dispatch.put(appointment, "ReminderSet", true);
            Dispatch.put(appointment, "ReminderMinutesBeforeStart", 15);

            if (show) {
                Dispatch.call(appointment, "Display");
            } else {
                Dispatch.call(appointment, "Save");
            }
        } finally {
            outlook.safeRelease();
        }
    }

    /**
     * Ermittelt die Summe der belegten Minuten (BusyStatus != frei) im Outlook-Kalender
     * fuer den angegebenen Tag. Wird genutzt, um Praxisauftraege an ruhigen Tagen
     * groesser und an vollen Tagen kleiner zu planen. Liefert 0 zurueck (statt eine
     * Exception zu werfen), falls Outlook nicht erreichbar ist - der Tag gilt dann als frei.
     */
    public static int getBusyMinutesForDate(LocalDate date) {
        ActiveXComponent outlook = null;
        try {
            outlook = new ActiveXComponent("Outlook.Application");
            Dispatch namespace = Dispatch.call(outlook, "GetNamespace", "MAPI").toDispatch();
            Dispatch calendar = Dispatch.call(namespace, "GetDefaultFolder", OL_FOLDER_CALENDAR).toDispatch();
            Dispatch items = Dispatch.call(calendar, "Items").toDispatch();
            Dispatch.put(items, "IncludeRecurrences", true);
            Dispatch.call(items, "Sort", "[Start]");

            String von = date.atStartOfDay().format(OUTLOOK_DATE_FORMAT);
            String bis = date.plusDays(1).atStartOfDay().format(OUTLOOK_DATE_FORMAT);
            String filter = "[Start] >= '" + von + "' AND [Start] < '" + bis + "'";
            Dispatch gefiltert = Dispatch.call(items, "Restrict", filter).toDispatch();

            int summeMinuten = 0;
            Variant current = Dispatch.call(gefiltert, "GetFirst");
            while (current != null && !current.isNull() && current.getDispatch() != null) {
                Dispatch termin = current.toDispatch();
                int busyStatus = Dispatch.get(termin, "BusyStatus").getInt();
                if (busyStatus != OL_FREE) {
                    summeMinuten += Dispatch.get(termin, "Duration").getInt();
                }
                current = Dispatch.call(gefiltert, "GetNext");
            }
            return summeMinuten;
        } catch (Throwable e) {
            // Bewusst Throwable statt Exception: JACOB/COM kann bei fehlender
            // COM-Initialisierung im aktuellen Thread auch Error-Subklassen
            // werfen (z.B. UnsatisfiedLinkError) - diese duerfen die
            // Terminvorschlag-Berechnung NICHT komplett abbrechen.
            LOGGER.log(Level.WARNING, "Outlook-Kalender konnte nicht gelesen werden fuer " + date, e);
            return 0;
        } finally {
            if (outlook != null) {
                try {
                    outlook.safeRelease();
                } catch (Throwable ignore) {
                    // COM-Release-Fehler hier bewusst ignorieren, damit die
                    // eigentliche Terminvorschlag-Logik nicht gestoert wird.
                }
            }
        }
    }

    /** Einfacher manueller Test, analog zum __main__-Block in outlook_integration.py. */
    public static void main(String[] args) {
        createTimeBlockAndShow(
                "Test-Zeitblock (Konvink Assistant Java)",
                LocalDateTime.now().plusHours(1),
                60,
                "Automatisch erstellt vom Java-Konvink-Assistant zu Testzwecken."
        );
    }
}
