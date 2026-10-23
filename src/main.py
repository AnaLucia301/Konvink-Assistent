"""
main.py
-------
Konvink-Assistant: Eigenständige Desktop-App zur Unterstützung bei der
Planung und Dokumentation von Praxisaufträgen.

Funktionen:
    - Eigener Kalender (Monatsansicht) mit frei wählbarem Datum/Uhrzeit/
      Dauer für Zeitblocker, die per Knopfdruck in Outlook eingetragen
      werden können.
    - Übersicht erkannter Praxisaufträge (aus Konvink-PDFs geparst).
    - Vorschläge für mögliche "Kombi-Aufträge" (gemeinsame Handlung).
    - KI-Unterstützung beim Schreiben von Texten über Copilot in Teams
      (Prompt wird automatisch vorbereitet und in die Zwischenablage
      kopiert, Teams wird geöffnet).

Start:
    python src\\main.py
"""

from __future__ import annotations

import calendar
import json
import logging
import sys
import threading
from datetime import datetime
from pathlib import Path

import customtkinter as ctk

from ai_assist import build_praxisauftrag_prompt, prepare_ai_text_request
from combi_suggester import find_combi_vorschlaege
from outlook_integration import create_time_block_and_show
from praxisauftrag_parser import Praxisauftrag, parse_all_praxisauftraege

CONFIG_PATH = Path(__file__).resolve().parent.parent / "config" / "config.json"
PDF_FOLDER = Path(__file__).resolve().parent.parent / "data" / "pdfs"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger(__name__)

ctk.set_appearance_mode("System")
ctk.set_default_color_theme("blue")


def load_config() -> dict:
    if not CONFIG_PATH.exists():
        logger.warning(
            "Konfigurationsdatei fehlt: %s. Bitte config.example.json "
            "kopieren und als config.json anpassen. App startet trotzdem "
            "mit Standardwerten.",
            CONFIG_PATH,
        )
        return {}
    with CONFIG_PATH.open("r", encoding="utf-8") as f:
        return json.load(f)


class KonvinkAssistantApp(ctk.CTk):
    def __init__(self):
        super().__init__()

        self.config_data = load_config()
        self.praxisauftraege: list[Praxisauftrag] = []

        self.title("Konvink Assistant")
        self.geometry("1000x700")
        self.minsize(800, 600)

        self._build_layout()
        self._load_praxisauftraege_async()

    # ------------------------------------------------------------------
    # Layout
    # ------------------------------------------------------------------
    def _build_layout(self) -> None:
        self.grid_columnconfigure(0, weight=1)
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(1, weight=1)

        # Kopfzeile
        header = ctk.CTkLabel(
            self,
            text="📘 Konvink Assistant",
            font=ctk.CTkFont(size=22, weight="bold"),
        )
        header.grid(row=0, column=0, columnspan=2, pady=(15, 5), sticky="w", padx=20)

        # Linke Spalte: Kalender / Zeitblocker
        self._build_calendar_panel()

        # Rechte Spalte: Praxisaufträge + Kombi-Vorschläge + KI
        self._build_praxisauftrag_panel()

    def _build_calendar_panel(self) -> None:
        panel = ctk.CTkFrame(self)
        panel.grid(row=1, column=0, sticky="nsew", padx=(20, 10), pady=10)
        panel.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(
            panel, text="🗓️ Zeitblocker planen", font=ctk.CTkFont(size=16, weight="bold")
        ).grid(row=0, column=0, pady=(10, 5), padx=10, sticky="w")

        # Betreff
        ctk.CTkLabel(panel, text="Betreff:").grid(row=1, column=0, sticky="w", padx=10)
        self.subject_entry = ctk.CTkEntry(panel, placeholder_text="z.B. Praxisauftrag: XY")
        self.subject_entry.grid(row=2, column=0, sticky="ew", padx=10, pady=(0, 10))

        # Datum
        ctk.CTkLabel(panel, text="Datum (TT.MM.JJJJ):").grid(
            row=3, column=0, sticky="w", padx=10
        )
        today_str = datetime.now().strftime("%d.%m.%Y")
        self.date_entry = ctk.CTkEntry(panel, placeholder_text=today_str)
        self.date_entry.insert(0, today_str)
        self.date_entry.grid(row=4, column=0, sticky="ew", padx=10, pady=(0, 10))

        # Uhrzeit
        ctk.CTkLabel(panel, text="Uhrzeit (HH:MM):").grid(
            row=5, column=0, sticky="w", padx=10
        )
        self.time_entry = ctk.CTkEntry(panel, placeholder_text="09:00")
        self.time_entry.insert(0, "09:00")
        self.time_entry.grid(row=6, column=0, sticky="ew", padx=10, pady=(0, 10))

        # Dauer
        ctk.CTkLabel(panel, text="Dauer (Minuten):").grid(
            row=7, column=0, sticky="w", padx=10
        )
        default_duration = str(
            self.config_data.get("outlook", {}).get("standard_dauer_minuten", 60)
        )
        self.duration_entry = ctk.CTkEntry(panel, placeholder_text=default_duration)
        self.duration_entry.insert(0, default_duration)
        self.duration_entry.grid(row=8, column=0, sticky="ew", padx=10, pady=(0, 15))

        # Button: In Outlook eintragen
        self.outlook_button = ctk.CTkButton(
            panel,
            text="📅 In Outlook eintragen",
            command=self._on_create_outlook_block,
        )
        self.outlook_button.grid(row=9, column=0, sticky="ew", padx=10, pady=(0, 10))

        # Mini-Monatskalender (reine Übersicht, zur Orientierung)
        self.calendar_label = ctk.CTkLabel(panel, text="", justify="left", font=("Consolas", 12))
        self.calendar_label.grid(row=10, column=0, sticky="w", padx=10, pady=(10, 10))
        self._render_mini_calendar()

        # Statuszeile
        self.calendar_status = ctk.CTkLabel(panel, text="", text_color="gray")
        self.calendar_status.grid(row=11, column=0, sticky="w", padx=10, pady=(0, 10))

    def _render_mini_calendar(self) -> None:
        now = datetime.now()
        cal_text = calendar.month(now.year, now.month)
        self.calendar_label.configure(text=cal_text)

    def _build_praxisauftrag_panel(self) -> None:
        panel = ctk.CTkFrame(self)
        panel.grid(row=1, column=1, sticky="nsew", padx=(10, 20), pady=10)
        panel.grid_columnconfigure(0, weight=1)
        panel.grid_rowconfigure(2, weight=1)

        ctk.CTkLabel(
            panel, text="📄 Praxisaufträge", font=ctk.CTkFont(size=16, weight="bold")
        ).grid(row=0, column=0, pady=(10, 5), padx=10, sticky="w")

        self.refresh_button = ctk.CTkButton(
            panel,
            text="🔄 Praxisaufträge neu laden",
            command=self._load_praxisauftraege_async,
        )
        self.refresh_button.grid(row=1, column=0, sticky="ew", padx=10, pady=(0, 10))

        self.auftrag_list_frame = ctk.CTkScrollableFrame(panel, label_text="Erkannte Praxisaufträge")
        self.auftrag_list_frame.grid(row=2, column=0, sticky="nsew", padx=10, pady=(0, 10))

        self.combi_list_frame = ctk.CTkScrollableFrame(panel, label_text="💡 Kombi-Auftrag-Vorschläge")
        self.combi_list_frame.grid(row=3, column=0, sticky="nsew", padx=10, pady=(0, 10))
        panel.grid_rowconfigure(3, weight=1)

    # ------------------------------------------------------------------
    # Datenladen
    # ------------------------------------------------------------------
    def _load_praxisauftraege_async(self) -> None:
        thread = threading.Thread(target=self._load_praxisauftraege, daemon=True)
        thread.start()

    def _load_praxisauftraege(self) -> None:
        if not PDF_FOLDER.exists() or not any(PDF_FOLDER.glob("*.pdf")):
            self.after(0, self._show_no_pdfs_hint)
            return

        self.praxisauftraege = parse_all_praxisauftraege(PDF_FOLDER)
        self.after(0, self._refresh_auftrag_list)
        self.after(0, self._refresh_combi_list)

    def _show_no_pdfs_hint(self) -> None:
        for widget in self.auftrag_list_frame.winfo_children():
            widget.destroy()
        hint = ctk.CTkLabel(
            self.auftrag_list_frame,
            text=(
                f"Noch keine PDFs gefunden in:\n{PDF_FOLDER}\n\n"
                "Bitte Praxisauftrags-PDFs dort ablegen (später per "
                "Konvink-Automatisierung automatisch), dann auf "
                "'Praxisaufträge neu laden' klicken."
            ),
            justify="left",
            wraplength=350,
        )
        hint.pack(anchor="w", padx=5, pady=5)

    def _refresh_auftrag_list(self) -> None:
        for widget in self.auftrag_list_frame.winfo_children():
            widget.destroy()

        if not self.praxisauftraege:
            return

        for auftrag in self.praxisauftraege:
            row = ctk.CTkFrame(self.auftrag_list_frame)
            row.pack(fill="x", padx=5, pady=5)

            ctk.CTkLabel(
                row, text=auftrag.titel, font=ctk.CTkFont(weight="bold"), anchor="w"
            ).pack(fill="x", padx=5, pady=(5, 0))
            ctk.CTkLabel(
                row, text=f"Handlung: {auftrag.handlung or '(nicht erkannt)'}",
                anchor="w", text_color="gray",
            ).pack(fill="x", padx=5)

            ai_button = ctk.CTkButton(
                row,
                text="✍️ KI-Hilfe beim Schreiben",
                command=lambda a=auftrag: self._on_ai_assist(a),
            )
            ai_button.pack(anchor="e", padx=5, pady=5)

    def _refresh_combi_list(self) -> None:
        for widget in self.combi_list_frame.winfo_children():
            widget.destroy()

        vorschlaege = find_combi_vorschlaege(self.praxisauftraege)

        if not vorschlaege:
            ctk.CTkLabel(
                self.combi_list_frame,
                text="Keine Kombi-Auftrag-Vorschläge gefunden.",
                text_color="gray",
            ).pack(anchor="w", padx=5, pady=5)
            return

        for vorschlag in vorschlaege:
            text = (
                f"{vorschlag.auftrag_a.titel}  ⇄  {vorschlag.auftrag_b.titel}\n"
                f"Gemeinsame Handlung: {vorschlag.gemeinsame_handlung} "
                f"(Ähnlichkeit {vorschlag.aehnlichkeit:.0%})"
            )
            ctk.CTkLabel(
                self.combi_list_frame, text=text, justify="left", anchor="w"
            ).pack(fill="x", padx=5, pady=5)

    # ------------------------------------------------------------------
    # Aktionen
    # ------------------------------------------------------------------
    def _on_create_outlook_block(self) -> None:
        subject = self.subject_entry.get().strip() or "Praxisauftrag"
        date_str = self.date_entry.get().strip()
        time_str = self.time_entry.get().strip()
        duration_str = self.duration_entry.get().strip()

        try:
            start = datetime.strptime(f"{date_str} {time_str}", "%d.%m.%Y %H:%M")
            duration_minutes = int(duration_str)
        except ValueError:
            self.calendar_status.configure(
                text="⚠️ Bitte Datum (TT.MM.JJJJ), Uhrzeit (HH:MM) und "
                "Dauer (Minuten, Zahl) korrekt eingeben.",
                text_color="orange",
            )
            return

        try:
            create_time_block_and_show(
                subject=subject, start=start, duration_minutes=duration_minutes
            )
            self.calendar_status.configure(
                text="✅ Termin in Outlook geöffnet - bitte dort final speichern.",
                text_color="green",
            )
        except Exception as exc:  # noqa: BLE001
            logger.exception("Fehler beim Erstellen des Outlook-Termins")
            self.calendar_status.configure(
                text=f"❌ Fehler: {exc}", text_color="red"
            )

    def _on_ai_assist(self, auftrag: Praxisauftrag) -> None:
        prompt = build_praxisauftrag_prompt(
            titel=auftrag.titel,
            handlung=auftrag.handlung or "(nicht erkannt)",
        )
        prepare_ai_text_request(prompt)


def main() -> None:
    app = KonvinkAssistantApp()
    app.mainloop()


if __name__ == "__main__":
    try:
        main()
    except Exception:  # noqa: BLE001
        logger.exception("Unerwarteter Fehler beim Start der App")
        sys.exit(1)
