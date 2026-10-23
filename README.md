# Konvink Assistant

Ein eigenständiges Desktop-Tool, das:

1. Sich bei [Konvink](https://app.konvink.ch/Security/Login) anmeldet (wiederverwendet nach Möglichkeit eine bestehende angemeldete Browser-Session über ein persistentes Chrome-Profil).
2. Über **Mein Portfolio → Kompetenzentwicklung im Überblick → Bearbeiten → Über alle Handlungskompetenzen** alle Praxisaufträge (PDFs) des 3. Lehrjahres herunterlädt.
3. Die PDFs analysiert (Titel, Handlungskompetenz, Thema) und offene Aufträge in einem Terminkalender einplant.
4. Themen vorschlägt und kompatible Praxisaufträge (gleiche Handlungskompetenz) als **Kombiauftrag** vorschlägt.
5. Mit KI-Unterstützung beim Schreiben der Texte hilft.

Alles läuft in einem eigenen Fenster (separat von Konvink/Browser).

## Setup

```powershell
cd c:\Workspace\Projekte\Konvink_Assistant
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
playwright install chromium
```

Zugangsdaten und AI-Key über Umgebungsvariablen oder `.env` (siehe `src/config.py`):

- `KONVINK_EMAIL`
- `KONVINK_PASSWORD`
- `KONVINK_AI_API_KEY` (OpenAI-kompatibel; optional, kann in der App auch eingegeben werden)

## Start

```powershell
python src\main_app.py
```

Beim ersten Login öffnet sich ein sichtbares Chromium-Fenster mit einem persistenten Profil
(`data/browser_profile`). Danach bleibt die Session gespeichert, du musst dich nicht jedes Mal
neu anmelden (ähnlich "Angemeldet bleiben").

## Projektstruktur

```
src/
  config.py            Konfiguration & Secrets-Handling
  konvink_client.py     Playwright-Login + Navigation + PDF-Download
  pdf_parser.py         Extrahiert Metadaten aus Praxisauftrags-PDFs
  kombi_suggester.py    Findet kompatible Kombiaufträge (gleiche Handlungskompetenz)
  ai_helper.py          KI-Integration für Themenvorschläge & Textschreibhilfe
  calendar_window.py    Kalender-Widget zur Terminplanung
  main_app.py           Hauptfenster, verbindet alles
data/
  downloads/            Heruntergeladene Praxisauftrags-PDFs
  browser_profile/      Persistentes Chromium-Profil (entsteht automatisch)
```

## Offene Punkte / Annahmen

- Kombi-Kriterium aktuell: zwei Praxisaufträge gelten als kompatibel, wenn sie **dieselbe
  Handlungskompetenz** abdecken (laut deiner bisherigen Praxis). Kann später verfeinert werden
  (z.B. zusätzlich Thema/Schlüsselwörter).
- AI-Provider ist standardmäßig OpenAI-kompatibel (per `openai` SDK), Modell und Endpoint lassen
  sich in `config.py` anpassen, falls ihr z.B. Azure OpenAI nutzt.
- Die genauen DOM-Selektoren für die Konvink-Navigation (Portfolio-Pfad) müssen beim ersten echten
  Lauf noch verifiziert/angepasst werden, da ich die Seite nicht automatisiert einsehen kann.
