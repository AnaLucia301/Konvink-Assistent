"""Findet kompatible Praxisaufträge für Kombiaufträge.

Kriterium (laut Nutzerangabe): Zwei Praxisaufträge gelten als kompatibel, wenn
eine einzelne Handlung/ein einzelner Handlungsnachweis BEIDE Praxisaufträge
abdecken kann. Als pragmatische Näherung gruppieren wir nach identischer
Handlungskompetenz (ggf. später verfeinerbar um Themen-Ähnlichkeit).
"""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from itertools import combinations

from pdf_parser import Praxisauftrag


@dataclass
class KombiVorschlag:
    auftrag_a: Praxisauftrag
    auftrag_b: Praxisauftrag
    gemeinsame_handlungskompetenz: str

    def __str__(self) -> str:  # pragma: no cover - reine Darstellung
        return (
            f"Kombi: '{self.auftrag_a.titel}' + '{self.auftrag_b.titel}' "
            f"(gemeinsam: {self.gemeinsame_handlungskompetenz})"
        )


def suggest_kombiauftraege(auftraege: list[Praxisauftrag]) -> list[KombiVorschlag]:
    groups: dict[str, list[Praxisauftrag]] = defaultdict(list)
    for a in auftraege:
        if a.kombi_key:
            groups[a.kombi_key].append(a)

    vorschlaege: list[KombiVorschlag] = []
    for key, group in groups.items():
        if len(group) < 2:
            continue
        for a, b in combinations(group, 2):
            vorschlaege.append(
                KombiVorschlag(auftrag_a=a, auftrag_b=b, gemeinsame_handlungskompetenz=key)
            )
    return vorschlaege
