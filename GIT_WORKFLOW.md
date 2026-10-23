# Git-Workflow: Änderungen immer committen

**Regel:** Nach jeder abgeschlossenen Änderung (neues Feature, Bugfix, Designanpassung) wird sie
sofort committed und nach GitHub gepusht. So geht nie etwas verloren, und man kann jederzeit
zu einem funktionierenden Stand zurück.

Repository: https://github.com/AnaLucia301/Konvink-Assistent (Branch `main`)

## Ablauf nach jeder Änderung

1. **App bauen und testen** – nur committen, was funktioniert:
   ```powershell
   mvn -q package
   ```
2. **Änderungen ansehen:**
   ```powershell
   git status
   ```
3. **Alles vormerken und committen** (kurze, klare Nachricht, was sich geändert hat):
   ```powershell
   git add -A
   git commit -m "Kurze Beschreibung, z.B. Fristen-Farben auf Karten"
   ```
4. **Zu GitHub hochladen:**
   ```powershell
   git push
   ```

## Hinweis zu Git auf diesem PC

Git ist hier als portable Version installiert und nicht im normalen Pfad. Vor den Befehlen
einmal pro Terminal-Sitzung ausführen:

```powershell
$env:Path = "C:\Workspace\git\cmd;$env:Path"
```

Oder `C:\Workspace\git\cmd\git.exe` direkt aufrufen.

## Gute Commit-Nachrichten

- Eine Zeile, die sagt, **was** sich geändert hat: `Tags in Detailansicht ergänzt`
- Lieber viele kleine Commits als ein riesiger am Ende.
- Eine Änderung pro Commit, damit man sie bei Bedarf einzeln rückgängig machen kann.

## Was NICHT ins Repo gehört

Das regelt die Datei `.gitignore`. Nicht committen:

- Zugangsdaten: `config/secrets.json`, `config/config.json`
- Build-Ausgaben: `target/`, `build_output.txt`
- Die heruntergeladenen PDFs (`downloads/`) und `ki_chat/`
- Persönliche Daten der App (liegen in `~/.konvink-assistant/`, außerhalb des Projekts)

Vor dem Commit mit `git status` prüfen, dass keine dieser Dateien aufgelistet ist.

## Wenn etwas schiefgeht

| Problem | Lösung |
|---|---|
| Letzten Commit-Text ändern (noch nicht gepusht) | `git commit --amend -m "Neuer Text"` |
| Änderung an einer Datei verwerfen | `git restore <datei>` |
| Alten Stand ansehen | `git log --oneline` |
| Push abgelehnt | erst `git pull`, dann nochmal `git push` |
| Beim Push erscheint eine Anmeldung | Im Browser mit dem GitHub-Konto bestätigen |
