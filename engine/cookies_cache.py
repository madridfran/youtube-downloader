"""
Cache de cookies de navegador para yt-dlp.

Flujo:
1. extraer_cookies(nav)  ->  genera cookies_<nav>.txt desde el navegador.
                             (REQUIERE que el navegador este CERRADO.)
2. ruta_cookies(nav)     ->  devuelve la ruta si existen, None si no.
3. detectar_auth_fallo(out) -> True si yt-dlp dice que las cookies no sirven.
4. invalidar_cookies(nav) -> borra el archivo para forzar re-extraccion.

La GUI usa:
  - Si hay archivo valido -> yt-dlp con --cookies archivo  (navegador abierto, OK)
  - Si no hay archivo     -> extraer (pide cerrar navegador)
  - Si falla auth         -> invalidar + pedir cerrar navegador + reintentar
"""
from pathlib import Path
import os
import subprocess
import sys


APP_DIR = Path(os.environ.get("LOCALAPPDATA", Path.home() / ".local")) / "YouTubeDownloader"
COOKIES_DIR = APP_DIR / "cookies"

# Patrones en la salida de yt-dlp que indican que las cookies ya no sirven
SENIALES_AUTH_FALLO = [
    "sign in to confirm",
    "login required",
    "http error 401",
    "http error 403",
    "this video is only available",
    "members-only",
    "cookies are no longer valid",
    "requested format is not available. use --list-formats",  # a veces por auth
]


def ruta_cookies(navegador: str) -> Path | None:
    """Devuelve la ruta del archivo de cookies si existe y tiene tamano > 0."""
    if navegador in ("ninguno", "", None):
        return None
    f = COOKIES_DIR / f"cookies_{navegador}.txt"
    if f.exists() and f.stat().st_size > 100:  # al menos un par de lineas
        return f
    return None


def extraer_cookies(navegador: str, ytdlp_path: str = "yt-dlp") -> tuple[bool, str]:
    """
    Extrae las cookies del navegador a un archivo Netscape.
    REQUIERE que el navegador este cerrado.
    Devuelve (ok, mensaje).
    """
    if navegador in ("ninguno", "", None):
        return False, "Navegador no seleccionado"

    COOKIES_DIR.mkdir(parents=True, exist_ok=True)
    destino = COOKIES_DIR / f"cookies_{navegador}.txt"
    cf = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0

    # Truco: pedir info de un video cualquiera forzando cookies-from-browser + cookies FILE
    # hace que yt-dlp escriba el archivo. Usamos un video pequeno y --skip-download.
    args = [
        ytdlp_path,
        "--cookies-from-browser", navegador,
        "--cookies", str(destino),
        "--skip-download",
        "--no-warnings",
        "--quiet",
        "--print", "id",
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    ]
    try:
        res = subprocess.run(args, capture_output=True, text=True,
                              creationflags=cf, timeout=60)
        if destino.exists() and destino.stat().st_size > 100:
            return True, f"Cookies extraidas: {destino}"
        return False, f"yt-dlp no escribio cookies. stderr:\n{res.stderr[:500]}"
    except subprocess.TimeoutExpired:
        return False, "Timeout extrayendo cookies (¿navegador abierto?)"
    except Exception as e:
        return False, f"Error: {e}"


def detectar_auth_fallo(salida_ytdlp: str) -> bool:
    """True si la salida de yt-dlp indica cookies caducadas/invalidas."""
    if not salida_ytdlp:
        return False
    bajo = salida_ytdlp.lower()
    return any(s in bajo for s in SENIALES_AUTH_FALLO)


def invalidar_cookies(navegador: str) -> None:
    """Borra el archivo de cookies para forzar re-extraccion."""
    if navegador in ("ninguno", "", None):
        return
    f = COOKIES_DIR / f"cookies_{navegador}.txt"
    try:
        if f.exists():
            f.unlink()
    except Exception:
        pass


def info_cookies(navegador: str) -> str:
    """Texto descriptivo para mostrar en la UI."""
    r = ruta_cookies(navegador)
    if r is None:
        return "Sin cookies cacheadas"
    import datetime
    ts = datetime.datetime.fromtimestamp(r.stat().st_mtime)
    return f"Cookies cacheadas ({ts.strftime('%d/%m/%Y %H:%M')})"
