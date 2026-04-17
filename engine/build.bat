@echo off
:: Build local: genera .exe y luego el instalador.
:: Requiere: python, pyinstaller, Inno Setup (ISCC en PATH).

echo === Instalando dependencias de build ===
python -m pip install --upgrade pyinstaller customtkinter Pillow yt-dlp

echo.
echo === Limpiando builds anteriores ===
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
if exist dist_installer rmdir /s /q dist_installer

echo.
echo === Empaquetando con PyInstaller ===
python -m PyInstaller build.spec --clean --noconfirm
if errorlevel 1 (
    echo [ERROR] PyInstaller fallo.
    pause
    exit /b 1
)

echo.
echo === Generando instalador con Inno Setup ===
where ISCC >nul 2>&1
if errorlevel 1 (
    echo [AVISO] ISCC no esta en PATH. Instala Inno Setup y anadelo al PATH.
    echo         El .exe esta en dist\YouTubeDownloader\
    pause
    exit /b 0
)

:: Detectar arquitectura del host
if /i "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
    set ARCH=arm64
) else (
    set ARCH=x64
)
echo Arquitectura detectada: %ARCH%

ISCC /DARCH=%ARCH% installer.iss
if errorlevel 1 (
    echo [ERROR] Inno Setup fallo.
    pause
    exit /b 1
)

echo.
echo === LISTO ===
echo Instalador en: dist_installer\
pause
