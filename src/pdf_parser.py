"""Extrahiert Metadaten aus Praxisauftrags-PDFs (Titel, Handlungskompetenz, Thema)."""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

import fitz  # PyMuPDF


@dataclass
class Praxisauftrag:
    path: Path
    titel: str = ""
    handlungskompetenz: str = ""
    thema: str = ""
    lehrjahr: str = ""
    volltext: str = field(default="", repr=False)

    @property
    def kombi_key(self) -> str:
        """Gruppierungsschlüssel für Kombiauftrag-Vorschläge: gleiche
        Handlungskompetenz = potenziell kombinierbar (wie bisher von Hand gemacht)."""
        return self.handlungskompetenz.strip().lower() or self.titel.strip().lower()


_HANDLUNG_PATTERN = re.compile(r"Handlungskompetenz[:\s]*([^\n]+)", re.IGNORECASE)
_THEMA_PATTERN = re.compile(r"Thema[:\s]*([^\n]+)", re.IGNORECASE)
_LEHRJAHR_PATTERN = re.compile(r"(\d)\.\s*Lehrjahr", re.IGNORECASE)


def parse_pdf(path: Path) -> Praxisauftrag:
    doc = fitz.open(path)
    text = "\n".join(page.get_text() for page in doc)
    doc.close()

    titel = path.stem
    first_line = next((line.strip() for line in text.splitlines() if line.strip()), "")
    if first_line:
        titel = first_line

    handlung_match = _HANDLUNG_PATTERN.search(text)
    thema_match = _THEMA_PATTERN.search(text)
    lehrjahr_match = _LEHRJAHR_PATTERN.search(text)

    return Praxisauftrag(
        path=path,
        titel=titel,
        handlungskompetenz=handlung_match.group(1).strip() if handlung_match else "",
        thema=thema_match.group(1).strip() if thema_match else "",
        lehrjahr=lehrjahr_match.group(1) if lehrjahr_match else "",
        volltext=text,
    )


def parse_all(directory: Path) -> list[Praxisauftrag]:
    return [parse_pdf(p) for p in sorted(directory.glob("*.pdf"))]
