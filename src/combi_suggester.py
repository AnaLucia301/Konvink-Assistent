"""
combi_suggester.py
--------------------
Schlägt mögliche "Kombi-Aufträge" vor: Zwei (oder mehr) Praxisaufträge,
die thematisch/durch eine gemeinsame "Handlung" verbunden sind und daher
in einer gemeinsamen Praxissituation kombiniert bearbeitet werden könnten.

Kriterium (laut Vorgabe): Zwei Praxisaufträge gelten als "kombinierbar",
wenn historisch eine einzelne Handlung beide Praxisaufträge abgedeckt hat
- d.h. wenn ihre "Handlung"-Felder übereinstimmen oder stark ähnlich sind.
"""

from __future__ import annotations

import difflib
from dataclasses import dataclass
from itertools import combinations

from praxisauftrag_parser import Praxisauftrag


@dataclass
class KombiVorschlag:
    auftrag_a: Praxisauftrag
    auftrag_b: Praxisauftrag
    gemeinsame_handlung: str
    aehnlichkeit: float  # 0.0 - 1.0


def _normalize(text: str) -> str:
    return " ".join(text.strip().lower().split())


def find_combi_vorschlaege(
    auftraege: list[Praxisauftrag],
    aehnlichkeits_schwelle: float = 0.8,
) -> list[KombiVorschlag]:
    """
    Durchsucht alle Paare von Praxisaufträgen nach übereinstimmenden oder
    sehr ähnlichen "Handlung"-Feldern und schlägt diese als möglichen
    Kombi-Auftrag vor.

    aehnlichkeits_schwelle: Wert zwischen 0 und 1 (via difflib
        SequenceMatcher). 1.0 = exakte Übereinstimmung erforderlich,
        niedrigere Werte erlauben auch leicht abweichende Formulierungen
        derselben Handlung.
    """
    vorschlaege: list[KombiVorschlag] = []

    for auftrag_a, auftrag_b in combinations(auftraege, 2):
        handlung_a = _normalize(auftrag_a.handlung)
        handlung_b = _normalize(auftrag_b.handlung)

        if not handlung_a or not handlung_b:
            continue

        aehnlichkeit = difflib.SequenceMatcher(None, handlung_a, handlung_b).ratio()

        if aehnlichkeit >= aehnlichkeits_schwelle:
            vorschlaege.append(
                KombiVorschlag(
                    auftrag_a=auftrag_a,
                    auftrag_b=auftrag_b,
                    gemeinsame_handlung=auftrag_a.handlung,
                    aehnlichkeit=aehnlichkeit,
                )
            )

    # Beste Übereinstimmungen zuerst
    vorschlaege.sort(key=lambda v: v.aehnlichkeit, reverse=True)
    return vorschlaege
