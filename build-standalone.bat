@echo off
REM Baut die Windows-App "Konvink-Assistent" als eigenstaendigen Ordner,
REM der ohne Java-Installation und ohne VS Code laeuft, und packt eine ZIP.
REM Benoetigt: JDK 17 (mit jpackage) und Maven im PATH, oder unten anpassen.

setlocal

set JAVA_HOME=C:\Workspace\jdk17\jdk-17.0.20.1+1
set PATH=C:\Workspace\maven\apache-maven-3.9.9\bin;%JAVA_HOME%\bin;%PATH%

echo === 1/3: Projekt bauen (mvn package) ===
call mvn -q package
if errorlevel 1 (
  echo Build fehlgeschlagen.
  exit /b 1
)

echo === 2/3: Eigenstaendige App bauen (jpackage) ===
if exist dist rmdir /s /q dist
if exist jpkg_input rmdir /s /q jpkg_input
mkdir jpkg_input
copy /y target\konvink-assistant.jar jpkg_input\ >nul

"%JAVA_HOME%\bin\jpackage.exe" ^
  --type app-image ^
  --name "Konvink-Assistent" ^
  --input jpkg_input ^
  --main-jar konvink-assistant.jar ^
  --main-class com.konvink.assistant.Main ^
  --dest dist ^
  --app-version 1.0.0 ^
  --vendor "Konvink Assistant" ^
  --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 (
  echo jpackage fehlgeschlagen.
  exit /b 1
)
rmdir /s /q jpkg_input

echo === 3/3: ZIP erstellen ===
copy /y LIESMICH_DOWNLOAD.txt dist\LIESMICH.txt >nul
powershell -NoProfile -Command "Compress-Archive -Path 'dist\Konvink-Assistent','dist\LIESMICH.txt' -DestinationPath 'dist\Konvink-Assistent-Windows.zip' -Force"

echo.
echo Fertig: dist\Konvink-Assistent-Windows.zip
endlocal
