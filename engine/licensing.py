"""
Modulo de licencias. Stub preparado para un sistema de licencias futuro.

Contrato publico (NO cambiar firmas):
    verify_license() -> LicenseStatus
    get_license_key() -> str | None
    set_license_key(key: str) -> LicenseStatus

La GUI debe llamar SOLO a estas funciones. Asi, en el futuro, cambias la
implementacion interna (HMAC offline, servidor, Keygen.sh, Cryptolens, etc.)
sin tocar la GUI ni el empaquetado.
"""
from dataclasses import dataclass
from pathlib import Path
import json
import os


LICENSE_FILE = Path(os.environ.get("APPDATA", Path.home())) / "YouTubeDownloader" / "license.json"


@dataclass
class LicenseStatus:
    valid: bool
    tier: str = "free"          # free | pro | enterprise
    expires: str | None = None  # ISO date o None
    message: str = ""


def get_license_key() -> str | None:
    try:
        if LICENSE_FILE.exists():
            data = json.loads(LICENSE_FILE.read_text(encoding="utf-8"))
            return data.get("key")
    except Exception:
        pass
    return None


def set_license_key(key: str) -> LicenseStatus:
    """Guarda la clave y la valida. Por ahora solo almacena."""
    try:
        LICENSE_FILE.parent.mkdir(parents=True, exist_ok=True)
        LICENSE_FILE.write_text(json.dumps({"key": key}), encoding="utf-8")
    except Exception as e:
        return LicenseStatus(False, message=f"No se pudo guardar: {e}")
    return verify_license()


def verify_license() -> LicenseStatus:
    """
    STUB: hoy siempre devuelve valido en modo 'free'.
    En el futuro, aqui se verifica la clave con HMAC/servidor/etc.
    """
    key = get_license_key()
    if not key:
        return LicenseStatus(valid=True, tier="free",
                             message="Sin licencia (modo gratuito)")
    # TODO: implementar validacion real
    return LicenseStatus(valid=True, tier="pro",
                         message="Licencia activa (stub)")
