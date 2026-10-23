"""
ai_assist.py
-------------
Unterstützt beim Schreiben von Texten (z.B. für Praxisauftrags-
Dokumentation) über den firmeneigenen Microsoft Copilot in Teams.

Da es keine offizielle, einfach automatisierbare API für M365 Copilot in
Teams gibt, wird der zuverlässigste Weg genutzt:
    1. Der vorbereitete Prompt/Text wird automatisch in die Windows-
       Zwischenablage kopiert.
    2. Teams wird geöffnet (bzw. in den Vordergrund geholt), idealerweise
       direkt im Copilot-Chat.
    3. Der Benutzer muss nur noch mit Strg+V einfügen und senden.

Das ist deutlich robuster als Browser-/UI-Automatisierung des Copilot-
Chats selbst (der sich häufig ändert) und funktioniert zuverlässig.
"""

from __future__ import annotations

import logging
import subprocess
import time

import pyperclip

logger = logging.getLogger(__name__)

TEAMS_COPILOT_DEEPLINK = "msteams://teams.microsoft.com/l/chat/0/0?users=28:copilot"


def prepare_ai_text_request(prompt: str, open_teams: bool = True) -> None:
    """
    Kopiert den übergebenen Prompt/Text in die Zwischenablage und öffnet
    optional direkt den Copilot-Chat in Teams.

    Parameter:
        prompt: Der vorbereitete Text/Prompt, den Copilot verarbeiten soll
            (z.B. "Schreibe eine Dokumentation für folgenden
            Praxisauftrag: ...").
        open_teams: Wenn True, wird Teams (Copilot-Chat) automatisch
            geöffnet bzw. in den Vordergrund geholt.
    """
    pyperclip.copy(prompt)
    logger.info("Prompt wurde in die Zwischenablage kopiert (%d Zeichen).", len(prompt))

    if open_teams:
        try:
            subprocess.run(["cmd", "/c", "start", "", TEAMS_COPILOT_DEEPLINK], check=False)
            logger.info(
                "Teams Copilot-Chat wird geöffnet. Bitte dort mit Strg+V "
                "einfügen und senden."
            )
        except Exception as exc:  # noqa: BLE001
            logger.warning(
                "Teams konnte nicht automatisch geöffnet werden (%s). Bitte "
                "manuell öffnen und den Text mit Strg+V einfügen.", exc
            )


def build_praxisauftrag_prompt(
    titel: str,
    handlung: str,
    beschreibung: str = "",
) -> str:
    """
    Baut einen fertigen Copilot-Prompt zur Unterstützung beim Schreiben
    der Dokumentation für einen Praxisauftrag.
    """
    prompt_parts = [
        "Hilf mir, die Dokumentation für folgenden Praxisauftrag zu schreiben.",
        f"Titel: {titel}",
        f"Zugehörige Handlungskompetenz: {handlung}",
    ]
    if beschreibung:
        prompt_parts.append(f"Weitere Details: {beschreibung}")
    prompt_parts.append(
        "Bitte formuliere einen strukturierten, professionellen Text "
        "(Ausgangslage, Vorgehen, Ergebnis, Reflexion), der sich direkt "
        "in den Praxisauftrag übernehmen lässt."
    )
    return "\n".join(prompt_parts)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    example_prompt = build_praxisauftrag_prompt(
        titel="Testauftrag",
        handlung="Kundenanfragen bearbeiten",
        beschreibung="Beispielhafte Details zum Testauftrag.",
    )
    prepare_ai_text_request(example_prompt)
    time.sleep(1)
