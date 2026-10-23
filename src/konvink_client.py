"""Playwright-basierter Client für Konvink: Login + Navigation + PDF-Download.

Nutzt ein persistentes Browser-Profil (`data/browser_profile`), damit nach dem
ersten erfolgreichen Login die Session erhalten bleibt ("Angemeldet bleiben") und
nicht bei jedem Start neu eingeloggt werden muss.

Hinweis: Die genauen CSS/Text-Selektoren für die Portfolio-Navigation sind
Best-Effort-Annahmen basierend auf den sichtbaren Linktexten. Falls Konvink sein
UI ändert oder andere Labels nutzt, müssen die `page.get_by_text(...)`-Aufrufe
unten angepasst werden.
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Iterable

from playwright.sync_api import BrowserContext, Page, sync_playwright

from config import (
    BROWSER_PROFILE_DIR,
    DOWNLOADS_DIR,
    KONVINK_LOGIN_URL,
    PORTFOLIO_NAV_PATH,
    TARGET_LEHRJAHR,
    settings,
)

logger = logging.getLogger(__name__)


class KonvinkClient:
    """Verwaltet eine persistente Playwright-Browsersession für Konvink."""

    def __init__(self, headless: bool = False) -> None:
        self.headless = headless
        self._playwright = None
        self._context: BrowserContext | None = None
        self.page: Page | None = None

    def __enter__(self) -> "KonvinkClient":
        self.start()
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()

    def start(self) -> None:
        self._playwright = sync_playwright().start()
        self._context = self._playwright.chromium.launch_persistent_context(
            user_data_dir=str(BROWSER_PROFILE_DIR),
            headless=self.headless,
            accept_downloads=True,
            viewport={"width": 1400, "height": 900},
        )
        self.page = self._context.new_page()

    def close(self) -> None:
        if self._context:
            self._context.close()
        if self._playwright:
            self._playwright.stop()

    def ensure_logged_in(self) -> None:
        """Navigiert zur Login-Seite. Falls bereits eine gültige Session im
        persistenten Profil existiert, leitet Konvink automatisch weiter und
        die Login-Felder sind nicht sichtbar -> wir überspringen den Login.
        """
        assert self.page is not None
        page = self.page
        page.goto(KONVINK_LOGIN_URL, wait_until="domcontentloaded")

        try:
            email_input = page.get_by_label("E-Mail", exact=False).first
            email_input.wait_for(state="visible", timeout=4000)
            already_logged_in = False
        except Exception:
            already_logged_in = True

        if already_logged_in:
            logger.info("Bereits angemeldet (persistente Session erkannt).")
            return

        logger.info("Login-Formular erkannt, melde an...")
        if settings.konvink_email:
            email_input.fill(settings.konvink_email)
        if settings.konvink_password:
            pw_input = page.get_by_label("Passwort", exact=False).first
            pw_input.fill(settings.konvink_password)

        page.get_by_role("checkbox", name="Angemeldet bleiben").check(force=True)
        page.get_by_role("button", name="Anmelden").click()
        page.wait_for_load_state("networkidle")

    def navigate_portfolio_path(self, steps: Iterable[str] = PORTFOLIO_NAV_PATH) -> None:
        """Klickt sich durch Mein Portfolio -> Kompetenzentwicklung im Überblick
        -> Bearbeiten -> Über alle Handlungskompetenzen."""
        assert self.page is not None
        page = self.page
        for label in steps:
            logger.info("Navigiere zu: %s", label)
            page.get_by_text(label, exact=False).first.click()
            page.wait_for_load_state("networkidle")

    def download_praxisauftrag_pdfs(self, lehrjahr: str = TARGET_LEHRJAHR) -> list[Path]:
        """Filtert nach Lehrjahr und lädt alle sichtbaren Praxisauftrags-PDFs herunter.

        Annahme: Es gibt pro Praxisauftrag einen Download/PDF-Link bzw. -Icon.
        Diese Selektoren müssen ggf. beim ersten echten Durchlauf justiert werden.
        """
        assert self.page is not None
        page = self.page
        saved_files: list[Path] = []

        # Optionaler Filter nach Lehrjahr, falls ein entsprechendes Dropdown/Filter existiert.
        try:
            filter_control = page.get_by_text(f"{lehrjahr}. Lehrjahr", exact=False).first
            filter_control.click(timeout=3000)
            page.wait_for_load_state("networkidle")
        except Exception:
            logger.warning("Kein Lehrjahr-Filter gefunden, lade alle sichtbaren Aufträge.")

        pdf_links = page.locator("a[href$='.pdf'], a:has-text('PDF'), [aria-label*='Download']")
        count = pdf_links.count()
        logger.info("%d PDF-Links gefunden.", count)

        for i in range(count):
            link = pdf_links.nth(i)
            try:
                with page.expect_download(timeout=10000) as download_info:
                    link.click()
                download = download_info.value
                target_path = DOWNLOADS_DIR / download.suggested_filename
                download.save_as(str(target_path))
                saved_files.append(target_path)
                logger.info("Gespeichert: %s", target_path.name)
            except Exception as exc:  # noqa: BLE001
                logger.warning("Download %d fehlgeschlagen: %s", i, exc)

        return saved_files

    def fetch_praxisauftraege(self) -> list[Path]:
        """High-Level Ablauf: Login -> Navigation -> Download."""
        self.ensure_logged_in()
        self.navigate_portfolio_path()
        return self.download_praxisauftrag_pdfs()
