"""
praxisauftrag_parser.py
-------------------------
Extrahiert strukturierte Informationen aus Praxisauftrags-PDFs
(Titel, zugehörige Handlungskompetenz/"Handlung", Beschreibung, etc.).

Die genaue Struktur der Konvink-PDFs muss anhand echter Beispiel-PDFs
verifiziert werden - dieses Modul enthält daher eine erste, anpassbare
Heuristik basierend auf Textmustern. Sobald echte PDFs vorliegen, passen
wir die Regex-Muster gezielt an.
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import pypdf

logger = logging.getLogger(__name__)


@dataclass
class Praxisauftrag:
    """Repräsentiert einen einzelnen, aus einem PDF extrahierten Praxisauftrag."""

    dateiname: str
    titel: str = ""
    handlung: str = ""
    lehrjahr: str = ""
    rohtext: str = ""
    weitere_felder: dict = field(default_factory=dict)


# Diese Muster sind ein erster Ansatz und werden angepasst, sobald echte
# Beispiel-PDFs aus Konvink zur Verfügung stehen.
TITEL_PATTERN = re.compile(r"(?:Titel|Praxisauftrag)\s*:?\s*(.+)", re.IGNORECASE)
HANDLUNG_PATTERN = re.compile(r"Handlung(?:skompetenz)?\s*:?\s*(.+)", re.IGNORECASE)
LEHRJAHR_PATTERN = re.compile(r"(\d\.\s*Lehrjahr[^\n]*)", re.IGNORECASE)


def extract_text_from_pdf(pdf_path: Path) -> str:
    """Liest den vollständigen Text aus einem PDF aus."""
    reader = pypdf.PdfReader(str(pdf_path))
    text_parts = [page.extract_text() or "" for page in reader.pages]
    return "\n".join(text_parts)


def parse_praxisauftrag_pdf(pdf_path: Path) -> Praxisauftrag:
    """
    Parst ein einzelnes Praxisauftrags-PDF und extrahiert die wichtigsten
    Felder (Titel, Handlung, Lehrjahr).
    """
    text = extract_text_from_pdf(pdf_path)

    titel_match = TITEL_PATTERN.search(text)
    handlung_match = HANDLUNG_PATTERN.search(text)
    lehrjahr_match = LEHRJAHR_PATTERN.search(text)

    auftrag = Praxisauftrag(
        dateiname=pdf_path.name,
        titel=titel_match.group(1).strip() if titel_match else pdf_path.stem,
        handlung=handlung_match.group(1).strip() if handlung_match else "",
        lehrjahr=lehrjahr_match.group(1).strip() if lehrjahr_match else "",
        rohtext=text,
    )

    if not auftrag.handlung:
        logger.warning(
            "Konnte keine Handlungskompetenz in '%s' erkennen - bitte "
            "Regex-Muster in praxisauftrag_parser.py anpassen, sobald die "
            "echte PDF-Struktur bekannt ist.",
            pdf_path.name,
        )

    return auftrag


def parse_all_praxisauftraege(pdf_folder: Path) -> list[Praxisauftrag]:
    """Parst alle PDF-Dateien in einem Ordner."""
    auftraege = []
    for pdf_path in sorted(pdf_folder.glob("*.pdf")):
        try:
            auftraege.append(parse_praxisauftrag_pdf(pdf_path))
        except Exception as exc:  # noqa: BLE001
            logger.error("Fehler beim Parsen von '%s': %s", pdf_path.name, exc)
    return auftraege
