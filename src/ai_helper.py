"""KI-Integration: Themenvorschläge & Textschreibhilfe.

Nutzt die OpenAI-kompatible SDK. API-Key/Model/Base-URL kommen aus `config.settings`,
können aber auch zur Laufzeit über die UI gesetzt werden (siehe main_app.py).
"""
from __future__ import annotations

from openai import OpenAI

from config import settings


def _client() -> OpenAI:
    if not settings.ai_api_key:
        raise RuntimeError(
            "Kein AI API-Key gesetzt. Bitte KONVINK_AI_API_KEY setzen oder in der App eingeben."
        )
    kwargs = {"api_key": settings.ai_api_key}
    if settings.ai_base_url:
        kwargs["base_url"] = settings.ai_base_url
    return OpenAI(**kwargs)


def suggest_topics(praxisauftrag_text: str, n: int = 3) -> list[str]:
    client = _client()
    prompt = (
        "Du hilfst einer Lernenden (Detailhandel/Lehre, 3. Lehrjahr) passende, konkrete "
        f"Themenvorschläge für folgenden Praxisauftrag zu finden. Gib {n} kurze, "
        "prägnante Themenvorschläge als Liste zurück.\n\nPraxisauftrag:\n" + praxisauftrag_text[:4000]
    )
    resp = client.chat.completions.create(
        model=settings.ai_model,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.7,
    )
    content = resp.choices[0].message.content or ""
    return [line.strip("-• ").strip() for line in content.splitlines() if line.strip()]


def help_write_text(context: str, instruction: str) -> str:
    """Hilft beim Schreiben/Überarbeiten eines Textabschnitts für den Praxisauftrag."""
    client = _client()
    prompt = (
        "Du hilfst beim Verfassen von Texten für einen Praxisauftrag (Lehre, Schweiz). "
        "Schreibe klar, sachlich und in Ich-Form, wo passend.\n\n"
        f"Kontext/Praxisauftrag:\n{context[:3000]}\n\n"
        f"Aufgabe: {instruction}"
    )
    resp = client.chat.completions.create(
        model=settings.ai_model,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.6,
    )
    return resp.choices[0].message.content or ""
