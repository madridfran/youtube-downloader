@echo off
chcp 65001 >nul 2>&1
title Build EXE x64 - YouTube Downloader v7.0
color 0B

:: cd al directorio de este bat (sistema/) para que PyInstaller y ISCC encuentren los archivos
cd /d "%~dp0"

:: Redirigir TODA la salida a build.log (y mostrarla tambien en pantalla al final)
set LOGFILE=%~dp0build.log
if exist "%LOGFILE%" del "%LOGFILE%"

:: Reejecutar el propio bat con redireccion a log si aun no se ha hecho
if not defined _LOGGING (
    set _LOGGING=1
    call "%~f0" %* > "%LOGFILE%" 2>&1
    echo.
    echo ============================================================
    echo   LOG GUARDADO EN:  %LOGFILE%
    echo ============================================================
    echo   Abrelo y pegamelo si hubo errores.
    echo.
    pause
    exit /b %errorlevel%
)

echo ============================================================
echo   BUILD EXE + INSTALADOR  [ x64 ]
echo ============================================================
echo.

:: --- Verificar arquitectura del host ---
if /i not "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
    if /i not "%PROCESSOR_ARCHITEW6432%"=="AMD64" (
        echo [ERROR] Esta maquina NO es x64. Arquitectura: %PROCESSOR_ARCHITECTURE%
        echo         Usa build_exe_arm64.bat en una maquina ARM.
        pause
        exit /b 1
    )
)
echo [OK] Host x64 detectado.
echo.

:: --- Verificar Python ---
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python no esta en PATH.
    exit /b 1
)
python --version
echo.

:: --- Instalar dependencias de build ---
echo [INFO] Instalando/actualizando dependencias de build...
python -m pip install --upgrade pyinstaller customtkinter Pillow
if errorlevel 1 (
    echo [ERROR] pip fallo.
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
    exit /b 1
)
echo.
echo [OK] EXE generado: dist\YouTubeDownloader\YouTubeDownloader.exe
echo.

:: --- Generar instalador con Inno Setup (si esta disponible) ---
where ISCC >nul 2>&1
if errorlevel 1 (
    echo [AVISO] Inno Setup (ISCC) no esta en PATH.
    echo         Instala desde https://jrsoftware.org/isdl.php
    echo         El EXE portable esta listo en dist\YouTubeDownloader\
    echo.
    exit /b 0
)

echo [INFO] Generando instalador x64 con Inno Setup...
ISCC /DARCH=x64 installer.iss
if errorlevel 1 (
    echo [ERROR] Inno Setup fallo.
    exit /b 1
)

echo.
echo ============================================================
echo   LISTO
echo ============================================================
echo   EXE portable:   dist\YouTubeDownloader\YouTubeDownloader.exe
echo   Instalador x64: dist_installer\YouTubeDownloader-7.0-x64-setup.exe
echo ============================================================
