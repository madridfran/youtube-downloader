@echo off
chcp 65001 >nul 2>&1
title Build EXE ARM64 - YouTube Downloader v7.0
color 0D

:: cd al directorio de este bat (sistema/) para que PyInstaller y ISCC encuentren los archivos
cd /d "%~dp0"

echo ============================================================
echo   BUILD EXE + INSTALADOR  [ ARM64 / Snapdragon ]
echo ============================================================
echo.
echo IMPORTANTE: Este script DEBE ejecutarse en una maquina
echo Windows ARM64 (Surface Snapdragon, etc). PyInstaller no
echo hace cross-compile: compila para la arquitectura del host.
echo.

:: --- Verificar que el host es ARM64 ---
if /i not "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
    echo [ERROR] Esta maquina NO es ARM64. Arquitectura: %PROCESSOR_ARCHITECTURE%
    echo         Ejecuta este .bat en un equipo Windows on ARM.
    echo         Para x64 usa build_exe_x64.bat
    pause
    exit /b 1
)
echo [OK] Host ARM64 detectado.
echo.

:: --- Verificar Python ARM64 nativo ---
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python no esta en PATH.
    echo         Instala Python ARM64 desde python.org
    pause
    exit /b 1
)
python --version
python -c "import platform; print('Arch Python:', platform.machine())"
python -c "import platform, sys; sys.exit(0 if platform.machine().lower() in ('arm64','aarch64') else 1)"
if errorlevel 1 (
    echo [AVISO] Python NO es ARM64 nativo. El EXE saldra emulado x64.
    echo         Recomendado: instalar Python ARM64 desde python.org
    echo.
    choice /C SN /M "Continuar igualmente"
    if errorlevel 2 exit /b 1
)
echo.

:: --- Instalar dependencias de build ---
echo [INFO] Instalando/actualizando dependencias de build...
python -m pip install --upgrade pyinstaller customtkinter Pillow
if errorlevel 1 (
    echo [ERROR] pip fallo.
    pause
    exit /b 1
)
echo.

:: --- Limpiar builds anteriores ---
echo [INFO] Limpiando builds anteriores...
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
if exist dist_installer rmdir /s /q dist_installer
echo.

:: --- Empaquetar con PyInstaller ---
echo [INFO] Empaquetando con PyInstaller...
python -m PyInstaller build.spec --clean --noconfirm
if errorlevel 1 (
    echo [ERROR] PyInstaller fallo.
    pause
    exit /b 1
)
echo.
echo [OK] EXE generado: dist\YouTubeDownloader\YouTubeDownloader.exe
echo.

:: --- Generar instalador con Inno Setup ---
where ISCC >nul 2>&1
if errorlevel 1 (
    echo [AVISO] Inno Setup (ISCC) no esta en PATH.
    echo         Instala desde https://jrsoftware.org/isdl.php
    echo         El EXE portable esta listo en dist\YouTubeDownloader\
    echo.
    pause
    exit /b 0
)

echo [INFO] Generando instalador ARM64 con Inno Setup...
ISCC /DARCH=arm64 installer.iss
if errorlevel 1 (
    echo [ERROR] Inno Setup fallo.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   LISTO
echo ============================================================
echo   EXE portable:     dist\YouTubeDownloader\YouTubeDownloader.exe
echo   Instalador ARM64: dist_installer\YouTubeDownloader-7.0-arm64-setup.exe
echo ============================================================
pause
