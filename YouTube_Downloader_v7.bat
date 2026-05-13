@echo off
chcp 65001 >nul 2>&1
title YouTube Downloader v7.0 - www.tradervolume.com
color 0A

:: %~dp0 = carpeta donde esta este .bat. Funciona desde cualquier disco o ruta.
set "RAIZ=%~dp0"
set "ENGINE=%RAIZ%engine"
set "SCRIPT=%ENGINE%\v7.0_descargador_youtube_gui.py"

echo ============================================================
echo   YouTube Downloader v7.0 - www.tradervolume.com
echo ============================================================
echo.

:: --- Verificar que existe la carpeta sistema ---
if not exist "%SCRIPT%" (
    echo [ERROR] No se encuentra:
    echo   %SCRIPT%
    echo.
    echo La carpeta "engine" debe estar junto a este .bat.
    echo.
    pause
    exit /b 1
)

:: --- Detectar Python real (evita stubs de Microsoft Store) ---
set "PYEXE="
for /f "delims=" %%i in ('where python 2^>nul ^| findstr /i /v "WindowsApps"') do (
    if not defined PYEXE set "PYEXE=%%i"
)
if not defined PYEXE (
    where py >nul 2>&1
    if not errorlevel 1 set "PYEXE=py -3"
)
if not defined PYEXE (
    echo [ERROR] No se encontro Python real.
    echo.
    echo En PATH solo hay stubs de Microsoft Store. Soluciones:
    echo  1. Settings ^> Apps ^> Advanced app settings ^> App execution aliases
    echo     Desactiva los alias de python.exe y python3.exe
    echo  2. O reinstala Python desde https://www.python.org/downloads/
    echo     marcando "Add Python to PATH".
    echo.
    pause
    exit /b 1
)

echo [OK] Python detectado:
"%PYEXE%" --version
echo   Ruta: %PYEXE%
echo.

:: --- Dependencias Python (solo paquetes pip) ---
"%PYEXE%" -c "import customtkinter" >nul 2>&1
if errorlevel 1 (
    echo [INFO] Instalando customtkinter...
    "%PYEXE%" -m pip install customtkinter --user --quiet
    if errorlevel 1 "%PYEXE%" -m pip install customtkinter --quiet
)

"%PYEXE%" -c "from PIL import Image" >nul 2>&1
if errorlevel 1 (
    echo [INFO] Instalando Pillow para thumbnails...
    "%PYEXE%" -m pip install Pillow --user --quiet
    if errorlevel 1 "%PYEXE%" -m pip install Pillow --quiet
)

echo [OK] Dependencias Python listas.
echo.
echo [INFO] yt-dlp y ffmpeg se descargaran automaticamente si faltan
echo        o si hay version mas reciente disponible.
echo ============================================================
echo.

:: --- Ejecutar aplicacion ---
cd /d "%ENGINE%"
"%PYEXE%" "%SCRIPT%"

if errorlevel 1 (
    echo.
    echo [ERROR] La aplicacion termino con errores.
    pause
)
