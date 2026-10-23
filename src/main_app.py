"""Hauptfenster des Konvink Assistant.

Eigenes Fenster mit:
- Liste der Praxisaufträge (aus Konvink geladen / vorhandene PDFs im data/downloads Ordner)
- Terminkalender zur Einplanung
- Kombiauftrag-Vorschläge
- KI-Themenvorschläge & Textschreibhilfe

Start: python src/main_app.py
"""
from __future__ import annotations

import logging
import sys
from pathlib import Path

from PySide6.QtWidgets import (
    QApplication,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPlainTextEdit,
    QPushButton,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from calendar_window import CalendarPanel
from config import DOWNLOADS_DIR, settings
from kombi_suggester import suggest_kombiauftraege
from pdf_parser import Praxisauftrag, parse_all

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("Konvink Assistant")
        self.resize(1200, 800)

        self.auftraege: list[Praxisauftrag] = []

        self.tabs = QTabWidget()
        self.setCentralWidget(self.tabs)

        self._build_auftraege_tab()
        self._build_calendar_tab()
        self._build_kombi_tab()
        self._build_ai_tab()

        self.load_local_pdfs()

    # --- Tabs -----------------------------------------------------------
    def _build_auftraege_tab(self) -> None:
        tab = QWidget()
        layout = QVBoxLayout(tab)

        actions = QHBoxLayout()
        self.fetch_button = QPushButton("Von Konvink laden (Login + Download)")
        self.fetch_button.clicked.connect(self.fetch_from_konvink)
        self.reload_button = QPushButton("Lokale PDFs neu einlesen")
        self.reload_button.clicked.connect(self.load_local_pdfs)
        actions.addWidget(self.fetch_button)
        actions.addWidget(self.reload_button)

        self.auftrag_list = QListWidget()
        self.auftrag_list.itemSelectionChanged.connect(self._on_auftrag_selected)

        self.detail_view = QPlainTextEdit()
        self.detail_view.setReadOnly(True)

        layout.addLayout(actions)
        layout.addWidget(QLabel("Praxisaufträge"))
        layout.addWidget(self.auftrag_list)
        layout.addWidget(QLabel("Details"))
        layout.addWidget(self.detail_view)

        self.tabs.addTab(tab, "Praxisaufträge")

    def _build_calendar_tab(self) -> None:
        self.calendar_panel = CalendarPanel()
        self.calendar_assign_button = self.calendar_panel.assign_button
        self.calendar_assign_button.clicked.connect(self._assign_selected_to_calendar)
        self.tabs.addTab(self.calendar_panel, "Terminkalender")

    def _build_kombi_tab(self) -> None:
        tab = QWidget()
        layout = QVBoxLayout(tab)
        self.kombi_button = QPushButton("Kombiaufträge vorschlagen")
        self.kombi_button.clicked.connect(self._compute_kombis)
        self.kombi_list = QListWidget()
        layout.addWidget(self.kombi_button)
        layout.addWidget(self.kombi_list)
        self.tabs.addTab(tab, "Kombiaufträge")

    def _build_ai_tab(self) -> None:
        tab = QWidget()
        layout = QVBoxLayout(tab)

        key_row = QHBoxLayout()
        key_row.addWidget(QLabel("AI API-Key:"))
        self.api_key_input = QLineEdit(settings.ai_api_key or "")
        self.api_key_input.setEchoMode(QLineEdit.EchoMode.Password)
        key_row.addWidget(self.api_key_input)
        save_key_btn = QPushButton("Übernehmen")
        save_key_btn.clicked.connect(self._apply_api_key)
        key_row.addWidget(save_key_btn)

        self.topics_button = QPushButton("Themenvorschläge für ausgewählten Auftrag")
        self.topics_button.clicked.connect(self._suggest_topics)
        self.topics_output = QPlainTextEdit()

        self.instruction_input = QLineEdit()
        self.instruction_input.setPlaceholderText(
            "z.B. 'Schreibe die Einleitung meines Praxisauftrags-Berichts'"
        )
        self.write_button = QPushButton("Text schreiben lassen")
        self.write_button.clicked.connect(self._help_write)
        self.write_output = QPlainTextEdit()

        layout.addLayout(key_row)
        layout.addWidget(self.topics_button)
        layout.addWidget(self.topics_output)
        layout.addWidget(QLabel("Anweisung für Textschreibhilfe:"))
        layout.addWidget(self.instruction_input)
        layout.addWidget(self.write_button)
        layout.addWidget(self.write_output)

        self.tabs.addTab(tab, "KI-Hilfe")

    # --- Actions ----------------------------------------------------------
    def load_local_pdfs(self) -> None:
        self.auftraege = parse_all(DOWNLOADS_DIR)
        self.auftrag_list.clear()
        for a in self.auftraege:
            item = QListWidgetItem(f"{a.titel}  [{a.handlungskompetenz or '—'}]")
            self.auftrag_list.addItem(item)
        logger.info("%d Praxisaufträge geladen aus %s", len(self.auftraege), DOWNLOADS_DIR)

    def fetch_from_konvink(self) -> None:
        try:
            from konvink_client import KonvinkClient  # lazy import (Playwright optional)

            with KonvinkClient(headless=False) as client:
                client.fetch_praxisauftraege()
            self.load_local_pdfs()
            QMessageBox.information(self, "Konvink", "Praxisaufträge wurden geladen.")
        except Exception as exc:  # noqa: BLE001
            logger.exception("Fehler beim Laden von Konvink")
            QMessageBox.warning(self, "Konvink", f"Fehler: {exc}")

    def _selected_auftrag(self) -> Praxisauftrag | None:
        row = self.auftrag_list.currentRow()
        if 0 <= row < len(self.auftraege):
            return self.auftraege[row]
        return None

    def _on_auftrag_selected(self) -> None:
        a = self._selected_auftrag()
        if a:
            self.detail_view.setPlainText(
                f"Titel: {a.titel}\nHandlungskompetenz: {a.handlungskompetenz}\n"
                f"Thema: {a.thema}\nLehrjahr: {a.lehrjahr}\nDatei: {a.path}\n\n{a.volltext[:2000]}"
            )

    def _assign_selected_to_calendar(self) -> None:
        a = self._selected_auftrag()
        if a:
            self.calendar_panel.schedule(a)

    def _compute_kombis(self) -> None:
        self.kombi_list.clear()
        vorschlaege = suggest_kombiauftraege(self.auftraege)
        if not vorschlaege:
            self.kombi_list.addItem("Keine Kombi-Vorschläge gefunden (keine gemeinsamen Handlungskompetenzen).")
        for v in vorschlaege:
            self.kombi_list.addItem(str(v))

    def _apply_api_key(self) -> None:
        settings.ai_api_key = self.api_key_input.text().strip() or None
        QMessageBox.information(self, "KI", "API-Key übernommen.")

    def _suggest_topics(self) -> None:
        a = self._selected_auftrag()
        if not a:
            QMessageBox.information(self, "KI", "Bitte zuerst einen Praxisauftrag auswählen.")
            return
        try:
            from ai_helper import suggest_topics

            topics = suggest_topics(a.volltext or a.titel)
            self.topics_output.setPlainText("\n".join(f"- {t}" for t in topics))
        except Exception as exc:  # noqa: BLE001
            self.topics_output.setPlainText(f"Fehler: {exc}")

    def _help_write(self) -> None:
        a = self._selected_auftrag()
        instruction = self.instruction_input.text().strip()
        if not a or not instruction:
            QMessageBox.information(self, "KI", "Bitte Auftrag auswählen und Anweisung eingeben.")
            return
        try:
            from ai_helper import help_write_text

            result = help_write_text(a.volltext or a.titel, instruction)
            self.write_output.setPlainText(result)
        except Exception as exc:  # noqa: BLE001
            self.write_output.setPlainText(f"Fehler: {exc}")


def main() -> None:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
