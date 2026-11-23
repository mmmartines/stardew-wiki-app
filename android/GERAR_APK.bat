@echo off
cd /d "%~dp0"
echo Gerando APK do Stardew Wiki PT...
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo.
  echo Falhou. Verifique se o Android Studio esta instalado.
  pause
  exit /b 1
)
copy /Y "app\build\outputs\apk\debug\app-debug.apk" "StardewWikiPT.apk" >nul
echo.
echo Pronto! O arquivo StardewWikiPT.apk esta nesta pasta.
pause
