# PyInstaller spec. Uso:
#   pip install pyinstaller
#   pyinstaller build.spec --clean
# El .exe queda en dist/YouTubeDownloader/YouTubeDownloader.exe

# -*- mode: python ; coding: utf-8 -*-

block_cipher = None

a = Analysis(
    ['v7.0_descargador_youtube_gui.py'],
    pathex=[],
    binaries=[],
    datas=[],
    hiddenimports=['customtkinter', 'PIL', 'PIL.Image', 'PIL.ImageTk'],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=['matplotlib', 'numpy', 'scipy', 'pandas', 'pytest'],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)
pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='YouTubeDownloader',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,   # GUI sin consola
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    # icon='icon.ico',  # descomenta cuando tengas icono
)
coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name='YouTubeDownloader',
)
