"""
Bootstrap de herramientas externas (yt-dlp, ffmpeg).

Descarga las ultimas versiones en el primer arranque y las guarda en
%LOCALAPPDATA%\\YouTubeDownloader\\bin\\

Uso desde la GUI:
    from bootstrap import ensure_tools, BIN_DIR
    ensure_tools(progress_cb=lambda msg, pct: ...)

Luego, al llamar a yt-dlp/ffmpeg, busca primero en BIN_DIR.
"""
from pathlib import Path
import json
import os
import platform
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile
import io


APP_DIR = Path(os.environ.get("LOCALAPPDATA", Path.home() / ".local")) / "YouTubeDownloader"
BIN_DIR = APP_DIR / "bin"
STATE_FILE = APP_DIR / "bootstrap_state.json"

# Cada cuanto volver a preguntar a GitHub por la ultima version (segundos).
# 24 h es un buen equilibrio: yt-dlp suele publicar a lo sumo 1 vez al dia.
CHECK_INTERVAL_SEC = 24 * 3600

# APIs de GitHub para resolver la ultima version publicada
YTDLP_API = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
FFMPEG_API = "https://api.github.com/repos/BtbN/FFmpeg-Builds/releases/latest"

# URLs de descarga. yt-dlp publica binarios por arquitectura.
YTDLP_URLS = {
    "x64":   "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe",
    "arm64": "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_arm64.exe",
    "x86":   "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_x86.exe",
}

# ffmpeg: BtbN publica builds estables x64 y arm64 en zip.
FFMPEG_URLS = {
    "x64":   "https://github.com/BtbN/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-win64-gpl.zip",
    "arm64": "https://github.com/BtbN/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-winarm64-gpl.zip",
}


def detectar_arquitectura() -> str:
    """Devuelve 'x64', 'arm64' o 'x86'."""
    m = platform.machine().lower()
    if m in ("arm64", "aarch64"):
        return "arm64"
    if m in ("amd64", "x86_64"):
        return "x64"
    return "x86"


def _descargar(url: str, destino: Path, progress_cb=None, etiqueta: str = "") -> None:
    destino.parent.mkdir(parents=True, exist_ok=True)
    tmp = destino.with_suffix(destino.suffix + ".part")

    req = urllib.request.Request(url, headers={"User-Agent": "YouTubeDownloader/7.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        total = int(resp.headers.get("Content-Length", 0))
        bajado = 0
        chunk = 64 * 1024
        with open(tmp, "wb") as f:
            while True:
                buf = resp.read(chunk)
                if not buf:
                    break
                f.write(buf)
                bajado += len(buf)
                if progress_cb and total:
                    pct = min(100, int(bajado * 100 / total))
                    progress_cb(f"Descargando {etiqueta}... {pct}%", pct)
    tmp.replace(destino)


def _extraer_ffmpeg_del_zip(zip_path: Path, destino_bin: Path) -> bool:
    """Extrae solo ffmpeg.exe y ffprobe.exe del zip."""
    try:
        with zipfile.ZipFile(zip_path) as z:
            encontrados = 0
            for nombre in z.namelist():
                base = Path(nombre).name.lower()
                if base in ("ffmpeg.exe", "ffprobe.exe") and not nombre.endswith("/"):
                    with z.open(nombre) as src, open(destino_bin / base, "wb") as dst:
                        shutil.copyfileobj(src, dst)
                    encontrados += 1
            return encontrados >= 1
    except Exception:
        return False


def _herramienta_en_path(nombre: str) -> Path | None:
    """Busca en BIN_DIR primero, luego en PATH del sistema."""
    candidato = BIN_DIR / nombre
    if candidato.exists():
        return candidato
    p = shutil.which(nombre)
    return Path(p) if p else None


def ruta_ytdlp() -> Path | None:
    return _herramienta_en_path("yt-dlp.exe" if sys.platform == "win32" else "yt-dlp")


def ruta_ffmpeg() -> Path | None:
    return _herramienta_en_path("ffmpeg.exe" if sys.platform == "win32" else "ffmpeg")


def _cargar_estado() -> dict:
    try:
        if STATE_FILE.exists():
            return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    except Exception:
        pass
    return {}


def _guardar_estado(data: dict) -> None:
    try:
        STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
        STATE_FILE.write_text(json.dumps(data, indent=2), encoding="utf-8")
    except Exception:
        pass


def _fetch_json(url: str, timeout: int = 10) -> dict | None:
    try:
        req = urllib.request.Request(url, headers={
            "User-Agent": "YouTubeDownloader/7.0",
            "Accept": "application/vnd.github+json",
        })
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8"))
    except Exception:
        return None


def _ultima_version_ytdlp() -> str | None:
    data = _fetch_json(YTDLP_API)
    return data.get("tag_name") if data else None


def _ultima_version_ffmpeg() -> str | None:
    """Usa la fecha de publicacion como identificador (BtbN es rolling)."""
    data = _fetch_json(FFMPEG_API)
    return data.get("published_at") if data else None


def _version_local_ytdlp(path: Path) -> str | None:
    try:
        cf = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
        out = subprocess.check_output([str(path), "--version"],
                                       stderr=subprocess.STDOUT,
                                       creationflags=cf, timeout=5)
        return out.decode("utf-8", errors="ignore").strip()
    except Exception:
        return None


def ensure_tools(progress_cb=None, forzar=False) -> dict:
    """
    Asegura que yt-dlp y ffmpeg estan presentes Y actualizados.
    - Si faltan: descarga.
    - Si hay una version mas nueva en GitHub: actualiza.
    - Si se comprobo hace menos de CHECK_INTERVAL_SEC: usa cache.
    progress_cb(msg: str, pct: int) se llama con avance (pct -1 = indeterminado).
    """
    BIN_DIR.mkdir(parents=True, exist_ok=True)
    arch = detectar_arquitectura()
    errores = []
    estado = _cargar_estado()
    ahora = int(time.time())
    ultima_check = estado.get("ultima_check", 0)
    debe_comprobar = forzar or (ahora - ultima_check) > CHECK_INTERVAL_SEC

    # ---------- yt-dlp ----------
    ytdlp = ruta_ytdlp()
    necesita_ytdlp = ytdlp is None
    if not necesita_ytdlp and debe_comprobar:
        if progress_cb:
            progress_cb("Comprobando version de yt-dlp...", -1)
        latest = _ultima_version_ytdlp()
        local = _version_local_ytdlp(ytdlp)
        if latest and local and latest.lstrip("v") != local.lstrip("v"):
            necesita_ytdlp = True
            if progress_cb:
                progress_cb(f"yt-dlp {local} -> {latest}", -1)
        estado["ytdlp_version"] = latest or estado.get("ytdlp_version")

    if forzar or necesita_ytdlp:
        if progress_cb:
            progress_cb("Descargando yt-dlp (ultima version)...", -1)
        try:
            url = YTDLP_URLS.get(arch, YTDLP_URLS["x64"])
            destino = BIN_DIR / "yt-dlp.exe"
            _descargar(url, destino, progress_cb=progress_cb, etiqueta="yt-dlp")
            ytdlp = destino
        except Exception as e:
            errores.append(f"yt-dlp: {e}")
            ytdlp = ruta_ytdlp()

    # ---------- ffmpeg ----------
    ffmpeg = ruta_ffmpeg()
    necesita_ffmpeg = ffmpeg is None
    if not necesita_ffmpeg and debe_comprobar:
        if progress_cb:
            progress_cb("Comprobando version de ffmpeg...", -1)
        latest = _ultima_version_ffmpeg()
        guardada = estado.get("ffmpeg_published_at")
        if latest and latest != guardada:
            necesita_ffmpeg = True
            if progress_cb:
                progress_cb("ffmpeg tiene nueva build", -1)
        if latest:
            estado["ffmpeg_published_at"] = latest

    if forzar or necesita_ffmpeg:
        if progress_cb:
            progress_cb("Descargando ffmpeg (ultima version)...", -1)
        try:
            url = FFMPEG_URLS.get(arch, FFMPEG_URLS["x64"])
            zip_dest = BIN_DIR / "ffmpeg.zip"
            _descargar(url, zip_dest, progress_cb=progress_cb, etiqueta="ffmpeg")
            if progress_cb:
                progress_cb("Extrayendo ffmpeg...", -1)
            ok = _extraer_ffmpeg_del_zip(zip_dest, BIN_DIR)
            try:
                zip_dest.unlink()
            except Exception:
                pass
            ffmpeg = ruta_ffmpeg() if ok else None
            if not ok:
                errores.append("ffmpeg: no se encontro ffmpeg.exe en el zip")
        except Exception as e:
            errores.append(f"ffmpeg: {e}")
            ffmpeg = ruta_ffmpeg()

    # Guardar cache de comprobacion
    if debe_comprobar and not errores:
        estado["ultima_check"] = ahora
    _guardar_estado(estado)

    # Anadir BIN_DIR al PATH para que subprocess encuentre las herramientas
    path_actual = os.environ.get("PATH", "")
    if str(BIN_DIR) not in path_actual:
        os.environ["PATH"] = f"{BIN_DIR}{os.pathsep}{path_actual}"

    return {"ytdlp": ytdlp, "ffmpeg": ffmpeg, "errores": errores, "arch": arch}


def actualizar_ytdlp(progress_cb=None) -> bool:
    """Fuerza re-descarga de yt-dlp. Util para un boton 'Actualizar yt-dlp'."""
    arch = detectar_arquitectura()
    try:
        url = YTDLP_URLS.get(arch, YTDLP_URLS["x64"])
        _descargar(url, BIN_DIR / "yt-dlp.exe",
                   progress_cb=progress_cb, etiqueta="yt-dlp")
        return True
    except Exception:
        return False


if __name__ == "__main__":
    def cb(msg, pct):
        print(f"[{pct:>3}%] {msg}")
    res = ensure_tools(progress_cb=cb)
    print("\nResultado:", res)
