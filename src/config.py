"""Zentrale Konfiguration für den Konvink Assistant.

Secrets werden bevorzugt aus Umgebungsvariablen gelesen. Als Fallback (nur lokal!)
können sie hier/als .env hinterlegt werden. Für Produktivbetrieb empfiehlt sich
`keyring` statt Klartext.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = PROJECT_ROOT / "data"
DOWNLOADS_DIR = DATA_DIR / "downloads"
BROWSER_PROFILE_DIR = DATA_DIR / "browser_profile"

KONVINK_LOGIN_URL = "https://app.konvink.ch/Security/Login?ReturnUrl=%2F"
KONVINK_BASE_URL = "https://app.konvink.ch"

# Navigationspfad laut Nutzerangabe:
# Mein Portfolio -> Kompetenzentwicklung im Überblick -> Bearbeiten -> Über alle Handlungskompetenzen
PORTFOLIO_NAV_PATH = [
    "Mein Portfolio",
    "Kompetenzentwicklung im Überblick",
    "Bearbeiten",
    "Über alle Handlungskompetenzen",
]

TARGET_LEHRJAHR = "3"  # 3. Lehrjahr -> 3/4 Semester


def _env(name: str, default: str | None = None) -> str | None:
    return os.environ.get(name, default)


@dataclass
class Settings:
    konvink_email: str | None = field(default_factory=lambda: _env("KONVINK_EMAIL", "ana-lucia.kljaic@bmw.ch"))
    konvink_password: str | None = field(default_factory=lambda: _env("KONVINK_PASSWORD"))
    ai_api_key: str | None = field(default_factory=lambda: _env("KONVINK_AI_API_KEY"))
    ai_model: str = field(default_factory=lambda: _env("KONVINK_AI_MODEL", "gpt-4o-mini") or "gpt-4o-mini")
    ai_base_url: str | None = field(default_factory=lambda: _env("KONVINK_AI_BASE_URL"))

    def ensure_dirs(self) -> None:
        DOWNLOADS_DIR.mkdir(parents=True, exist_ok=True)
        BROWSER_PROFILE_DIR.mkdir(parents=True, exist_ok=True)


settings = Settings()
settings.ensure_dirs()
