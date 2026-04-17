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

:: --- Verificar Python ---
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python no esta instalado o no esta en PATH.
    echo.
    echo Descarga Python desde: https://www.python.org/downloads/
    echo Asegurate de marcar "Add Python to PATH" durante la instalacion.
    echo.
    pause
    exit /b 1
)

echo [OK] Python detectado:
python --version
echo.

:: --- Dependencias Python (solo paquetes pip) ---
python -c "import customtkinter" >nul 2>&1
if errorlevel 1 (
    echo [INFO] Instalando customtkinter...
    python -m pip install customtkinter --user --quiet
    if errorlevel 1 python -m pip install customtkinter --quiet
)

python -c "from PIL import Image" >nul 2>&1
if errorlevel 1 (
    echo [INFO] Instalando Pillow para thumbnails...
    python -m pip install Pillow --user --quiet
    if errorlevel 1 python -m pip install Pillow --quiet
)

echo [OK] Dependencias Python listas.
echo.
echo [INFO] yt-dlp y ffmpeg se descargaran automaticamente si faltan
echo        o si hay version mas reciente disponible.
echo ============================================================
echo.

:: --- Ejecutar aplicacion ---
cd /d "%ENGINE%"
python "%SCRIPT%"

if errorlevel 1 (
    echo.
    echo [ERROR] La aplicacion termino con errores.
    pause
)
