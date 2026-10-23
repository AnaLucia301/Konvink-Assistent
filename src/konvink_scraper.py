"""
konvink_scraper.py
--------------------
Automatisiert Login und Navigation auf der Konvink-Plattform
(https://app.konvink.ch), um Praxisauftrags-PDFs herunterzuladen.

Nutzt Playwright (Browser-Automatisierung), da Konvink eine Web-App ist.

WICHTIG: Dieses Modul ist ein erstes Grundgerüst. Die exakten CSS-
Selektoren/Linktexte für die Navigation
    "Mein Portfolio" -> "Kompetenzentwicklung im Überblick" -> "Bearbeiten"
    -> "über alle Handlungskompetenzen"
müssen anhand der echten Seite verifiziert/angepasst werden - das machen
wir gemeinsam beim ersten Testlauf mit echten Zugangsdaten.
"""

from __future__ import annotations

import logging
from pathlib import Path

from playwright.sync_api import sync_playwright

logger = logging.getLogger(__name__)


def download_praxisauftraege_pdfs(
    login_url: str,
    username: str,
    download_folder: Path,
    lehrjahr_filter: str = "3. Lehrjahr 3/4",
    headless: bool = False,
) -> list[Path]:
    """
    Loggt sich bei Konvink ein, navigiert zu den Praxisaufträgen und lädt
    alle passenden PDFs in download_folder herunter.

    headless=False (Standard): Browser-Fenster wird sichtbar geöffnet,
    damit man den Ablauf beim ersten Mal beobachten und ggf. manuell
    eingreifen kann (z.B. falls SSO eine zusätzliche Bestätigung braucht).
    """
    download_folder.mkdir(parents=True, exist_ok=True)
    downloaded_files: list[Path] = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=headless)
        context = browser.new_context(accept_downloads=True)
        page = context.new_page()

        logger.info("Öffne Konvink-Login-Seite: %s", login_url)
        page.goto(login_url)

        # TODO: Login-Felder und Button-Selektoren anhand der echten Seite
        # verifizieren. Falls Windows-SSO automatisch greift, ist dieser
        # Schritt evtl. gar nicht nötig (Seite leitet direkt weiter).
        try:
            page.wait_for_load_state("networkidle", timeout=10_000)
        except Exception:  # noqa: BLE001
            pass

        logger.info(
            "Bitte den Login-Status im geöffneten Browserfenster prüfen. "
            "Falls eine manuelle Anmeldung nötig ist, dies jetzt im "
            "Fenster durchführen."
        )

        # Platzhalter für die Navigation durch:
        #   Mein Portfolio -> Kompetenzentwicklung im Überblick ->
        #   Bearbeiten -> über alle Handlungskompetenzen
        # Wird beim ersten echten Testlauf mit konkreten Selektoren
        # ergänzt (z.B. page.click("text=Mein Portfolio")).

        logger.warning(
            "Navigation und PDF-Download sind noch nicht final "
            "implementiert - dies ist ein Platzhalter-Grundgerüst. Bitte "
            "gemeinsam beim ersten Testlauf die echten Selektoren "
            "ermitteln und hier ergänzen."
        )

        browser.close()

    return downloaded_files


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    download_praxisauftraege_pdfs(
        login_url="https://app.konvink.ch/Security/Login",
        username="PLATZHALTER",
        download_folder=Path(__file__).resolve().parent.parent / "data" / "pdfs",
    )
