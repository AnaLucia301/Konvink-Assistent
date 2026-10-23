"""
outlook_integration.py
-----------------------
Erstellt Kalendertermine ("Zeitblocker") in Outlook über COM-Automatisierung.

Voraussetzung: Outlook ist installiert und lokal konfiguriert (klassisches
Desktop-Outlook, kein reines Web-Outlook).
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta
from typing import Optional

import win32com.client

logger = logging.getLogger(__name__)

OL_APPOINTMENT_ITEM = 1  # olAppointmentItem
OL_BUSY = 2  # olBusy


def create_time_block(
    subject: str,
    start: datetime,
    duration_minutes: int,
    body: str = "",
    reminder_minutes_before: Optional[int] = 15,
) -> None:
    """
    Erstellt einen neuen Kalendertermin ("Zeitblocker") in Outlook.

    Parameter:
        subject: Betreff des Termins (z.B. "Praxisauftrag: XY").
        start: Startzeitpunkt (datetime-Objekt, lokale Zeit).
        duration_minutes: Dauer in Minuten (frei wählbar, z.B. 30, 60, 90).
        body: Optionaler Beschreibungstext im Termin.
        reminder_minutes_before: Erinnerung X Minuten vorher (None = keine
            Erinnerung).

    Öffnet den Termin NICHT automatisch zur Ansicht, sondern speichert ihn
    direkt in den Standardkalender. Falls du den Termin vor dem Speichern
    noch einmal in Outlook sehen/anpassen möchtest, nutze stattdessen
    create_time_block_and_show().
    """
    appointment = _build_appointment(
        subject, start, duration_minutes, body, reminder_minutes_before
    )
    appointment.Save()
    logger.info(
        "Zeitblocker '%s' am %s (%s Minuten) in Outlook gespeichert.",
        subject, start.strftime("%d.%m.%Y %H:%M"), duration_minutes,
    )


def create_time_block_and_show(
    subject: str,
    start: datetime,
    duration_minutes: int,
    body: str = "",
    reminder_minutes_before: Optional[int] = 15,
) -> None:
    """
    Wie create_time_block(), öffnet aber zusätzlich das Termin-Fenster in
    Outlook, damit der Termin vor dem endgültigen Speichern noch einmal
    manuell geprüft/angepasst werden kann.
    """
    appointment = _build_appointment(
        subject, start, duration_minutes, body, reminder_minutes_before
    )
    appointment.Display()
    logger.info(
        "Zeitblocker '%s' am %s (%s Minuten) in Outlook zur Prüfung geöffnet.",
        subject, start.strftime("%d.%m.%Y %H:%M"), duration_minutes,
    )


def _build_appointment(
    subject: str,
    start: datetime,
    duration_minutes: int,
    body: str,
    reminder_minutes_before: Optional[int],
):
    outlook = win32com.client.Dispatch("Outlook.Application")
    appointment = outlook.CreateItem(OL_APPOINTMENT_ITEM)

    appointment.Subject = subject
    appointment.Start = start
    appointment.Duration = duration_minutes
    appointment.Body = body
    appointment.BusyStatus = OL_BUSY

    if reminder_minutes_before is not None:
        appointment.ReminderSet = True
        appointment.ReminderMinutesBeforeStart = reminder_minutes_before
    else:
        appointment.ReminderSet = False

    return appointment


if __name__ == "__main__":
    # Kleiner manueller Test: Erstellt einen Beispiel-Zeitblocker morgen um
    # 09:00 Uhr für 60 Minuten und zeigt ihn zur Prüfung an.
    logging.basicConfig(level=logging.INFO)
    test_start = datetime.now().replace(
        hour=9, minute=0, second=0, microsecond=0
    ) + timedelta(days=1)
    create_time_block_and_show(
        subject="Praxisauftrag: Testeintrag",
        start=test_start,
        duration_minutes=60,
        body="Dies ist ein automatisch erstellter Test-Zeitblocker.",
    )
