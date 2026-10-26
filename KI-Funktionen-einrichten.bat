@echo off
chcp 65001 >nul
title Konvink Assistent - KI-Funktionen einrichten
echo ============================================================
echo  Konvink Assistent - KI-Funktionen einrichten (Ollama)
echo ============================================================
echo.
echo Dieses Fenster laedt bei Bedarf Ollama herunter, installiert es,
echo startet es und laedt das KI-Modell "llama3.2:1b" (ca. 1.3 GB).
echo Bitte Internetverbindung sicherstellen und Fenster offen lassen.
echo.

where ollama >nul 2>&1
if %errorlevel%==0 (
  echo [1/4] Ollama ist bereits installiert.
  goto :start_ollama
)

echo [1/4] Ollama ist noch nicht installiert. Lade Installationsprogramm herunter ...
set "SETUP=%TEMP%\OllamaSetup.exe"
curl -L --fail -o "%SETUP%" "https://ollama.com/download/OllamaSetup.exe"
if errorlevel 1 (
  echo.
  echo FEHLER: Download fehlgeschlagen. Bitte Internetverbindung pruefen
  echo oder Ollama manuell von https://ollama.com herunterladen.
  pause
  exit /b 1
)

echo [2/4] Installiere Ollama (bitte kurz warten) ...
start /wait "" "%SETUP%" /SILENT
if errorlevel 1 (
  echo.
  echo FEHLER: Installation fehlgeschlagen. Bitte "%SETUP%" manuell starten.
  pause
  exit /b 1
)
echo Installation abgeschlossen.

:start_ollama
echo [3/4] Starte Ollama und warte, bis es bereit ist ...
start "Ollama" /min cmd /c "ollama serve >nul 2>&1"

set count=0
:waitloop
curl -s -o nul http://localhost:11434 >nul 2>&1
if %errorlevel%==0 goto :ready
set /a count+=1
if %count% GEQ 30 (
  echo.
  echo Ollama antwortet nach 60 Sekunden nicht. Bitte pruefe, ob in der
  echo Taskleiste unten rechts ein Ollama-Symbol erscheint, und fuehre
  echo dieses Skript danach erneut aus.
  pause
  exit /b 1
)
timeout /t 2 >nul
goto :waitloop

:ready
echo Ollama laeuft.
echo.
echo [4/4] Lade das KI-Modell "llama3.2:1b" herunter (ca. 1.3 GB, kann
echo       je nach Internetverbindung einige Minuten dauern) ...
ollama pull llama3.2:1b
if errorlevel 1 (
  echo.
  echo FEHLER beim Herunterladen des Modells. Bitte dieses Skript erneut
  echo ausfuehren, oder manuell in einer Konsole eingeben:
  echo    ollama pull llama3.2:1b
  pause
  exit /b 1
)

echo.
echo ============================================================
echo  Fertig! Der KI-Chat im Konvink Assistenten ist jetzt bereit.
echo  Bitte starte den Konvink Assistenten (neu), falls er bereits
echo  offen war.
echo ============================================================
pause
