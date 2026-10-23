@echo off
rem Startet den Konvink Assistant (ohne Konsolenfenster).
cd /d "%~dp0"
if not exist "target\konvink-assistant.jar" (
    echo JAR nicht gefunden - baue Projekt mit Maven ...
    call mvn -q package -DskipTests
)
start "" javaw -jar "target\konvink-assistant.jar"
