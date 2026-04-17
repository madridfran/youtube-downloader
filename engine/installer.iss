; Inno Setup script para YouTube Downloader v7
; Descarga Inno Setup: https://jrsoftware.org/isdl.php
; Compila:  ISCC installer.iss              (x64)
;           ISCC /DARCH=arm64 installer.iss  (ARM64)

#define AppName      "YouTube Downloader"
#define AppVersion   "7.0"
#define AppPublisher "www.tradervolume.com"
#define AppExeName   "YouTubeDownloader.exe"

#ifndef ARCH
  #define ARCH "x64"
#endif

[Setup]
AppId={{B3D5F2A7-1C4E-4A9B-9F0E-7A2B5C8D1E3F}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL=https://www.tradervolume.com
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
UninstallDisplayIcon={app}\{#AppExeName}
OutputDir=dist_installer
OutputBaseFilename=YouTubeDownloader-{#AppVersion}-{#ARCH}-setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed={#ARCH}compatible
ArchitecturesInstallIn64BitMode={#ARCH}

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Crear icono en el escritorio"; GroupDescription: "Iconos:"

[Files]
; Asume que antes ejecutaste PyInstaller y tienes dist\YouTubeDownloader\
Source: "dist\YouTubeDownloader\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{group}\Desinstalar {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Iniciar {#AppName}"; Flags: nowait postinstall skipifsilent
