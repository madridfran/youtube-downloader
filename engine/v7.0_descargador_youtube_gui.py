#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DESCARGADOR DE SUBTITULOS Y VIDEO — YOUTUBE  v7.0
GUI grafica avanzada con CustomTkinter — Layout de 2 columnas
Autor: Fran — www.tradervolume.com
Requiere: Python 3.10+, customtkinter, yt-dlp, ffmpeg (para video)
Opcional: Pillow (para thumbnails)

Novedades v7.0:
- Selector de calidad de descarga integrado en zona de videos (columna derecha)
- Nota de uso sobre autenticacion (cerrar navegador)
- BAT con deteccion e instalacion automatica de dependencias
- Analisis separado: Analizar video / Analizar lista
- Banner de playlist detectada con carga automatica
- Fallback automatico sin cookies si el navegador esta abierto
- Branding: www.tradervolume.com
"""

import customtkinter as ctk
import subprocess
import threading
import os
import re
import json
import sys
import shutil
import io
import urllib.request
from datetime import datetime
from pathlib import Path
from tkinter import filedialog, messagebox
import webbrowser

# Pillow opcional para thumbnails
try:
    from PIL import Image as PILImage
    HAS_PILLOW = True
except ImportError:
    HAS_PILLOW = False

# === CONFIGURACION ============================================================

ctk.set_appearance_mode("light")
ctk.set_default_color_theme("blue")

VERSION = "7.0"
APP_TITLE = f"YouTube Downloader v{VERSION}"
WEB_URL = "https://www.tradervolume.com"
CONFIG_FILE = "yt_downloader_config.json"

DEFAULT_CONFIG = {
    "output_folder": "",
    "output_folder_subs": "",
    "output_folder_video": "",
    "tipo_descarga": "subs",
    "idioma": "es",
    "formato_subs": "txt",
    "autosubs": True,
    "calidad_video": "1080",
    "normalizar_audio": True,
    "navegador_auth": "ninguno",
}

C = {  # Colors
    "bg":             "#FFFFFF",
    "panel":          "#F5F7FA",
    "panel_alt":      "#EEF2F7",
    "accent":         "#2563EB",
    "accent_hover":   "#1D4ED8",
    "success":        "#16A34A",
    "success_hover":  "#15803D",
    "warning":        "#D97706",
    "error":          "#DC2626",
    "text":           "#1F2937",
    "text_sec":       "#6B7280",
    "border":         "#E5E7EB",
    "input_bg":       "#FFFFFF",
    "log_bg":         "#F9FAFB",
    "cancel":         "#EF4444",
    "cancel_hover":   "#DC2626",
    "neutral":        "#9CA3AF",
    "neutral_hover":  "#6B7280",
    "selected_row":   "#EFF6FF",
    "header_row":     "#F1F5F9",
}

CALIDAD_OPTIONS = [
    ("best",  "Maxima disponible"),
    ("2160",  "2160p (4K)"),
    ("1080",  "1080p (Full HD)"),
    ("720",   "720p (HD)"),
    ("480",   "480p"),
    ("360",   "360p"),
]

CALIDAD_MAP = {
    "best": "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
    "2160": "bestvideo[height<=2160][ext=mp4]+bestaudio[ext=m4a]/best[height<=2160][ext=mp4]/best[height<=2160]",
    "1080": "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080][ext=mp4]/best[height<=1080]",
    "720":  "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best[height<=720]",
    "480":  "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best[height<=480]",
    "360":  "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[height<=360][ext=mp4]/best[height<=360]",
    "audio": "bestaudio[ext=m4a]/bestaudio",
}

NAVEGADORES = ["ninguno", "chrome", "edge", "firefox"]

THUMB_SIZE = (80, 45)


# === UTILIDADES ===============================================================

def detectar_herramienta(nombre):
    return shutil.which(nombre) is not None


def parsear_urls(texto):
    urls = []
    for linea in texto.splitlines():
        linea = linea.strip()
        if linea and not linea.startswith("#") and re.match(r"https?://", linea):
            urls.append(linea)
    return urls


def url_tiene_playlist(url):
    """Detecta si una URL de video tambien contiene parametro de playlist."""
    return bool(re.search(r"[?&]list=", url))


def url_es_solo_playlist(url):
    """Detecta si la URL es exclusivamente de playlist (no un video concreto)."""
    return bool(re.search(r"playlist\?list=", url))


def extraer_playlist_id(url):
    m = re.search(r"[?&]list=([^&]+)", url)
    return m.group(1) if m else None


def descargar_thumbnail(video_id):
    """Descarga thumbnail de YouTube. Retorna CTkImage o None."""
    if not HAS_PILLOW or not video_id:
        return None
    try:
        thumb_url = f"https://img.youtube.com/vi/{video_id}/mqdefault.jpg"
        req = urllib.request.Request(thumb_url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = resp.read()
        img = PILImage.open(io.BytesIO(data))
        img = img.resize(THUMB_SIZE, PILImage.LANCZOS)
        return ctk.CTkImage(light_image=img, dark_image=img,
                            size=THUMB_SIZE)
    except Exception:
        return None


# === CLASE PRINCIPAL ==========================================================

class App(ctk.CTk):
    def __init__(self):
        super().__init__()

        self.title(APP_TITLE)
        self.geometry("1200x820")
        self.minsize(1000, 700)
        self.configure(fg_color=C["bg"])

        # Estado
        self.videos_info = []  # lista de dicts con info de cada video
        self.video_checkboxes = {}  # id -> BooleanVar
        self.descarga_activa = False
        self.cancelar_solicitado = False
        self.proceso_actual = None
        self.cookies_fallaron = False  # se activa si las cookies no se pudieron leer
        self._playlist_detectadas = {}  # {pl_id: pl_title}
        self.var_calidad = ctk.StringVar(value="1080")  # default hasta que _build_right lo sobreescriba
        self.config = dict(DEFAULT_CONFIG)
        self.script_dir = str(Path(__file__).parent.resolve())

        # Dependencias
        self.tiene_ytdlp = detectar_herramienta("yt-dlp")
        self.tiene_ffmpeg = detectar_herramienta("ffmpeg")

        self._cargar_config()
        default_base = str(Path.home() / "Downloads" / "subtitulosyoutube")
        if not self.config["output_folder"]:
            self.config["output_folder"] = default_base
        if not self.config["output_folder_subs"]:
            self.config["output_folder_subs"] = os.path.join(
                self.config["output_folder"], "subtitulos")
        if not self.config["output_folder_video"]:
            self.config["output_folder_video"] = os.path.join(
                self.config["output_folder"], "videos")
        self.carpeta_salida = self.config["output_folder"]
        self.carpeta_subs = self.config["output_folder_subs"]
        self.carpeta_video = self.config["output_folder_video"]

        self._build_ui()
        self._mostrar_deps()

    # === CONFIG ===============================================================

    def _cargar_config(self):
        for base in [Path(self.script_dir), Path.cwd()]:
            cfg = base / CONFIG_FILE
            if cfg.exists():
                try:
                    with open(cfg, "r", encoding="utf-8") as f:
                        self.config.update(json.load(f))
                    break
                except Exception:
                    pass

    def _guardar_config(self):
        self._leer_gui()
        try:
            cfg = Path(self.script_dir) / CONFIG_FILE
            with open(cfg, "w", encoding="utf-8") as f:
                json.dump(self.config, f, indent=2, ensure_ascii=False)
        except Exception:
            pass

    def _leer_gui(self):
        self.config["output_folder"] = self.carpeta_salida
        self.config["output_folder_subs"] = self.carpeta_subs
        self.config["output_folder_video"] = self.carpeta_video
        self.config["tipo_descarga"] = self.var_tipo.get()
        self.config["idioma"] = self.var_idioma.get()
        self.config["formato_subs"] = self.var_formato.get()
        self.config["autosubs"] = self.var_autosubs.get()
        self.config["calidad_video"] = self.var_calidad.get()
        self.config["normalizar_audio"] = self.var_normalizar.get()
        self.config["navegador_auth"] = self.var_navegador.get()

    # === GUI PRINCIPAL ========================================================

    def _build_ui(self):
        self.grid_columnconfigure(0, weight=0, minsize=300)
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(0, weight=1)

        # --- COLUMNA IZQUIERDA (solo opciones) --------------------------------
        left = ctk.CTkScrollableFrame(self, width=300, fg_color=C["panel"],
                                       corner_radius=0)
        left.grid(row=0, column=0, sticky="nsew")
        left.grid_columnconfigure(0, weight=1)
        self._build_left(left)

        # --- COLUMNA DERECHA (URLs arriba + tabla + progreso + log) -----------
        right = ctk.CTkFrame(self, fg_color=C["bg"], corner_radius=0)
        right.grid(row=0, column=1, sticky="nsew")
        right.grid_columnconfigure(0, weight=1)
        right.grid_rowconfigure(0, weight=0)  # URLs
        right.grid_rowconfigure(1, weight=0)  # separador
        right.grid_rowconfigure(2, weight=0)  # banner playlist
        right.grid_rowconfigure(3, weight=0)  # header tabla
        right.grid_rowconfigure(4, weight=3)  # tabla videos
        right.grid_rowconfigure(7, weight=2)  # log
        self._build_right(right)

    # --- COLUMNA IZQUIERDA ----------------------------------------------------

    def _build_left(self, parent):
        pad = {"padx": 10, "pady": (0, 4)}
        r = 0

        # Header
        header = ctk.CTkFrame(parent, fg_color=C["accent"], corner_radius=6)
        header.grid(row=r, column=0, sticky="ew", padx=8, pady=(8, 8))
        ctk.CTkLabel(header, text=APP_TITLE,
                     font=ctk.CTkFont(size=16, weight="bold"),
                     text_color="#FFFFFF").pack(padx=12, pady=(8, 2))
        web_lbl = ctk.CTkLabel(header, text="www.tradervolume.com",
                               font=ctk.CTkFont(size=11, underline=True),
                               text_color="#BFDBFE", cursor="hand2")
        web_lbl.pack(padx=12, pady=(0, 8))
        web_lbl.bind("<Button-1>", lambda e: webbrowser.open(WEB_URL))
        r += 1

        # Deps
        self.lbl_deps = ctk.CTkLabel(parent, text="",
                                      font=ctk.CTkFont(size=10),
                                      text_color=C["text_sec"])
        self.lbl_deps.grid(row=r, column=0, sticky="w", **pad)
        r += 1

        # --- QUE DESCARGAR ---
        ctk.CTkLabel(parent, text="Que descargar",
                     font=ctk.CTkFont(size=13, weight="bold"),
                     text_color=C["text"]).grid(row=r, column=0, sticky="w", **pad)
        r += 1

        self.var_tipo = ctk.StringVar(value=self.config["tipo_descarga"])
        for val, txt in [("subs", "Solo subtitulos"),
                         ("video", "Solo video (MP4)"),
                         ("audio", "Solo audio (MP3)"),
                         ("ambos", "Subtitulos + Video")]:
            ctk.CTkRadioButton(parent, text=txt, variable=self.var_tipo,
                               value=val, font=ctk.CTkFont(size=11),
                               command=self._on_tipo_cambio
                               ).grid(row=r, column=0, sticky="w", padx=14, pady=1)
            r += 1

        # --- OPCIONES SUBTITULOS ---
        self.frame_subs = ctk.CTkFrame(parent, fg_color="transparent")
        self.frame_subs.grid(row=r, column=0, sticky="ew", padx=10, pady=(6, 0))
        r += 1

        ctk.CTkLabel(self.frame_subs, text="Idioma:",
                     font=ctk.CTkFont(size=11, weight="bold"),
                     text_color=C["text"]).pack(anchor="w")
        self.var_idioma = ctk.StringVar(value=self.config["idioma"])
        idioma_row = ctk.CTkFrame(self.frame_subs, fg_color="transparent")
        idioma_row.pack(anchor="w", fill="x")
        for val, txt in [("es", "ES"), ("en", "EN"), ("es,en", "ES+EN")]:
            ctk.CTkRadioButton(idioma_row, text=txt, variable=self.var_idioma,
                               value=val, font=ctk.CTkFont(size=11), width=70
                               ).pack(side="left", padx=(0, 4), pady=2)

        ctk.CTkLabel(self.frame_subs, text="Formato:",
                     font=ctk.CTkFont(size=11, weight="bold"),
                     text_color=C["text"]).pack(anchor="w", pady=(6, 0))
        self.var_formato = ctk.StringVar(value=self.config["formato_subs"])
        fmt_row = ctk.CTkFrame(self.frame_subs, fg_color="transparent")
        fmt_row.pack(anchor="w", fill="x")
        for val, txt in [("txt", "TXT (plano)"), ("srt", "SRT (tiempos)")]:
            ctk.CTkRadioButton(fmt_row, text=txt, variable=self.var_formato,
                               value=val, font=ctk.CTkFont(size=11), width=120
                               ).pack(side="left", padx=(0, 4), pady=2)

        self.var_autosubs = ctk.BooleanVar(value=self.config["autosubs"])
        ctk.CTkCheckBox(self.frame_subs, text="Incluir auto-generados",
                        variable=self.var_autosubs,
                        font=ctk.CTkFont(size=11)).pack(anchor="w", pady=(4, 0))

        # --- OPCIONES VIDEO ---
        self.frame_video = ctk.CTkFrame(parent, fg_color="transparent")
        self.frame_video.grid(row=r, column=0, sticky="ew", padx=10, pady=(6, 0))
        r += 1

        self.var_normalizar = ctk.BooleanVar(value=self.config["normalizar_audio"])
        ctk.CTkCheckBox(self.frame_video, text="Normalizar audio (EBU R128)",
                        variable=self.var_normalizar,
                        font=ctk.CTkFont(size=11)).pack(anchor="w", pady=(6, 0))

        # Separador
        ctk.CTkFrame(parent, fg_color=C["border"], height=1
                      ).grid(row=r, column=0, sticky="ew", padx=10, pady=8)
        r += 1

        # --- AUTENTICACION ---
        ctk.CTkLabel(parent, text="Autenticacion YouTube",
                     font=ctk.CTkFont(size=13, weight="bold"),
                     text_color=C["text"]).grid(row=r, column=0, sticky="w", **pad)
        r += 1

        auth_frame = ctk.CTkFrame(parent, fg_color="transparent")
        auth_frame.grid(row=r, column=0, sticky="ew", padx=10)
        r += 1

        ctk.CTkLabel(auth_frame, text="Cookies de:",
                     font=ctk.CTkFont(size=11),
                     text_color=C["text"]).pack(side="left", padx=(0, 6))
        self.var_navegador = ctk.StringVar(value=self.config["navegador_auth"])
        self.combo_nav = ctk.CTkComboBox(
            auth_frame, values=NAVEGADORES,
            font=ctk.CTkFont(size=11), width=120,
            state="readonly",
            command=lambda v: self.var_navegador.set(v))
        self.combo_nav.pack(side="left")
        self.combo_nav.set(self.config["navegador_auth"])

        ctk.CTkLabel(parent,
                     text="Permite acceder a videos privados/ocultos.\nDebes tener sesion abierta en el navegador.\n\nModo de uso: CIERRA el navegador antes de\nanalizar/descargar para que yt-dlp pueda\nleer las cookies. Si esta abierto, se\nreintentara sin autenticacion.",
                     font=ctk.CTkFont(size=9),
                     text_color=C["text_sec"], justify="left"
                     ).grid(row=r, column=0, sticky="w", padx=14, pady=(0, 4))
        r += 1

        # Separador
        ctk.CTkFrame(parent, fg_color=C["border"], height=1
                      ).grid(row=r, column=0, sticky="ew", padx=10, pady=8)
        r += 1

        # --- CARPETAS DE SALIDA ---
        ctk.CTkLabel(parent, text="Carpetas de salida",
                     font=ctk.CTkFont(size=13, weight="bold"),
                     text_color=C["text"]).grid(row=r, column=0, sticky="w", **pad)
        r += 1

        # Carpeta subtitulos
        ctk.CTkLabel(parent, text="Subtitulos:",
                     font=ctk.CTkFont(size=10, weight="bold"),
                     text_color=C["text"]).grid(row=r, column=0, sticky="w", padx=14, pady=(2, 0))
        r += 1

        self.lbl_carpeta_subs = ctk.CTkLabel(parent, text=self.carpeta_subs,
                                              font=ctk.CTkFont(size=9),
                                              text_color=C["text_sec"],
                                              wraplength=280, anchor="w", justify="left")
        self.lbl_carpeta_subs.grid(row=r, column=0, sticky="w", padx=14, pady=(0, 2))
        r += 1

        subs_btns = ctk.CTkFrame(parent, fg_color="transparent")
        subs_btns.grid(row=r, column=0, sticky="w", padx=10)
        r += 1

        ctk.CTkButton(subs_btns, text="Cambiar", width=70, height=24,
                       font=ctk.CTkFont(size=9),
                       fg_color=C["neutral"], hover_color=C["neutral_hover"],
                       command=lambda: self._on_cambiar_carpeta("subs")
                       ).pack(side="left", padx=(0, 4))
        ctk.CTkButton(subs_btns, text="Abrir", width=50, height=24,
                       font=ctk.CTkFont(size=9),
                       fg_color=C["neutral"], hover_color=C["neutral_hover"],
                       command=lambda: self._on_abrir_carpeta("subs")
                       ).pack(side="left")
        ctk.CTkButton(subs_btns, text="Borrar anteriores", width=110, height=24,
                       font=ctk.CTkFont(size=9),
                       fg_color=C["error"], hover_color="#B91C1C",
                       command=lambda: self._on_borrar_anteriores("subs")
                       ).pack(side="left", padx=(4, 0))

        # Carpeta videos/audio
        ctk.CTkLabel(parent, text="Videos / Audio:",
                     font=ctk.CTkFont(size=10, weight="bold"),
                     text_color=C["text"]).grid(row=r, column=0, sticky="w", padx=14, pady=(6, 0))
        r += 1

        self.lbl_carpeta_video = ctk.CTkLabel(parent, text=self.carpeta_video,
                                               font=ctk.CTkFont(size=9),
                                               text_color=C["text_sec"],
                                               wraplength=280, anchor="w", justify="left")
        self.lbl_carpeta_video.grid(row=r, column=0, sticky="w", padx=14, pady=(0, 2))
        r += 1

        vid_btns = ctk.CTkFrame(parent, fg_color="transparent")
        vid_btns.grid(row=r, column=0, sticky="w", padx=10)
        r += 1

        ctk.CTkButton(vid_btns, text="Cambiar", width=70, height=24,
                       font=ctk.CTkFont(size=9),
                       fg_color=C["neutral"], hover_color=C["neutral_hover"],
                       command=lambda: self._on_cambiar_carpeta("video")
                       ).pack(side="left", padx=(0, 4))
        ctk.CTkButton(vid_btns, text="Abrir", width=50, height=24,
                       font=ctk.CTkFont(size=9),
                       fg_color=C["neutral"], hover_color=C["neutral_hover"],
                       command=lambda: self._on_abrir_carpeta("video")
                       ).pack(side="left")
        ctk.CTkButton(vid_btns, text="Borrar anteriores", width=110, height=24,
                       font=ctk.CTkFont(size=9),
                       fg_color=C["error"], hover_color="#B91C1C",
                       command=lambda: self._on_borrar_anteriores("video")
                       ).pack(side="left", padx=(4, 0))

        # Visibilidad inicial
        self._on_tipo_cambio()

    # --- COLUMNA DERECHA ------------------------------------------------------

    def _build_right(self, parent):
        r = 0

        # --- URLs de YouTube (arriba del todo) ---
        url_frame = ctk.CTkFrame(parent, fg_color=C["panel"], corner_radius=0)
        url_frame.grid(row=r, column=0, sticky="ew", padx=0, pady=0)
        url_frame.grid_columnconfigure(0, weight=1)

        url_hdr = ctk.CTkFrame(url_frame, fg_color="transparent")
        url_hdr.pack(fill="x", padx=12, pady=(8, 2))
        url_hdr.columnconfigure(1, weight=1)

        ctk.CTkLabel(url_hdr, text="URLs de YouTube",
                     font=ctk.CTkFont(size=13, weight="bold"),
                     text_color=C["text"]).grid(row=0, column=0, sticky="w")

        self.lbl_estado = ctk.CTkLabel(url_hdr, text="",
                                        font=ctk.CTkFont(size=10),
                                        text_color=C["text_sec"])
        self.lbl_estado.grid(row=0, column=1, sticky="e", padx=(8, 0))

        self.txt_urls = ctk.CTkTextbox(url_frame, height=80,
                                        font=ctk.CTkFont(size=11),
                                        fg_color=C["input_bg"],
                                        border_color=C["border"],
                                        border_width=1, corner_radius=4)
        self.txt_urls.pack(fill="x", padx=12, pady=(0, 4))

        btn_urls = ctk.CTkFrame(url_frame, fg_color="transparent")
        btn_urls.pack(fill="x", padx=12, pady=(0, 8))

        self.btn_analizar = ctk.CTkButton(
            btn_urls, text="Analizar video", width=120, height=32,
            font=ctk.CTkFont(size=12, weight="bold"),
            fg_color=C["accent"], hover_color=C["accent_hover"],
            command=lambda: self._on_analizar(modo="video"))
        self.btn_analizar.pack(side="left", padx=(0, 4))

        self.btn_analizar_lista = ctk.CTkButton(
            btn_urls, text="Analizar lista", width=120, height=32,
            font=ctk.CTkFont(size=12, weight="bold"),
            fg_color=C["success"], hover_color=C["success_hover"],
            command=lambda: self._on_analizar(modo="lista"))
        self.btn_analizar_lista.pack(side="left", padx=(0, 8))

        ctk.CTkButton(
            btn_urls, text="Cargar .txt", width=90, height=32,
            font=ctk.CTkFont(size=11),
            fg_color=C["neutral"], hover_color=C["neutral_hover"],
            command=self._on_cargar_txt
        ).pack(side="left", padx=(0, 4))

        ctk.CTkButton(
            btn_urls, text="Limpiar", width=70, height=32,
            font=ctk.CTkFont(size=11),
            fg_color=C["neutral"], hover_color=C["neutral_hover"],
            command=self._on_limpiar
        ).pack(side="left")

        r += 1

        # Separador URLs / tabla
        ctk.CTkFrame(parent, fg_color=C["border"], height=1
                      ).grid(row=r, column=0, sticky="ew")
        r += 1

        # Banner de playlist detectada (oculto por defecto)
        self.playlist_banner = ctk.CTkFrame(parent, fg_color="#FEF3C7",
                                             corner_radius=0, height=36)
        self.playlist_banner.grid(row=r, column=0, sticky="ew", padx=0, pady=0)
        self.playlist_banner.grid_columnconfigure(1, weight=1)
        self.playlist_banner.grid_remove()

        self.lbl_playlist_info = ctk.CTkLabel(
            self.playlist_banner, text="",
            font=ctk.CTkFont(size=11),
            text_color="#92400E", anchor="w")
        self.lbl_playlist_info.grid(row=0, column=0, sticky="w", padx=12, pady=6)

        self.btn_cargar_playlist = ctk.CTkButton(
            self.playlist_banner, text="Cargar playlist completa",
            width=180, height=28,
            font=ctk.CTkFont(size=11, weight="bold"),
            fg_color=C["success"], hover_color=C["success_hover"],
            command=self._on_cargar_playlist_detectada)
        self.btn_cargar_playlist.grid(row=0, column=1, sticky="e", padx=12, pady=6)
        r += 1

        # Header tabla
        hdr = ctk.CTkFrame(parent, fg_color=C["header_row"], corner_radius=0)
        hdr.grid(row=r, column=0, sticky="ew", padx=0, pady=0)
        hdr.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(hdr, text="Videos detectados",
                     font=ctk.CTkFont(size=14, weight="bold"),
                     text_color=C["text"]).grid(row=0, column=0, padx=12, pady=8)

        # Selector de calidad integrado en el header
        cal_frame = ctk.CTkFrame(hdr, fg_color="transparent")
        cal_frame.grid(row=0, column=1, sticky="w", padx=(8, 0))

        ctk.CTkLabel(cal_frame, text="Calidad:",
                     font=ctk.CTkFont(size=11, weight="bold"),
                     text_color=C["text"]).pack(side="left", padx=(0, 4))

        self.var_calidad = ctk.StringVar(value=self.config["calidad_video"])
        cal_values = [f"{v[0]} — {v[1]}" for v in CALIDAD_OPTIONS]
        self.combo_calidad = ctk.CTkComboBox(
            cal_frame, values=cal_values,
            font=ctk.CTkFont(size=11), width=220,
            state="readonly", command=self._on_calidad_combo)
        self.combo_calidad.pack(side="left", padx=(0, 4))
        # Set initial value
        initial_cal = "1080 — 1080p (Full HD)"
        for code, label in CALIDAD_OPTIONS:
            if code == self.config["calidad_video"]:
                initial_cal = f"{code} — {label}"
                break
        self.combo_calidad.set(initial_cal)

        # Botones seleccion
        sel_btns = ctk.CTkFrame(hdr, fg_color="transparent")
        sel_btns.grid(row=0, column=2, sticky="e", padx=12)

        ctk.CTkButton(sel_btns, text="Todos", width=60, height=26,
                       font=ctk.CTkFont(size=10),
                       fg_color=C["accent"], hover_color=C["accent_hover"],
                       command=self._seleccionar_todos).pack(side="left", padx=2)
        ctk.CTkButton(sel_btns, text="Ninguno", width=60, height=26,
                       font=ctk.CTkFont(size=10),
                       fg_color=C["neutral"], hover_color=C["neutral_hover"],
                       command=self._deseleccionar_todos).pack(side="left", padx=2)
        ctk.CTkButton(sel_btns, text="Invertir", width=60, height=26,
                       font=ctk.CTkFont(size=10),
                       fg_color=C["neutral"], hover_color=C["neutral_hover"],
                       command=self._invertir_seleccion).pack(side="left", padx=2)

        self.lbl_seleccion = ctk.CTkLabel(sel_btns, text="",
                                           font=ctk.CTkFont(size=10),
                                           text_color=C["text_sec"])
        self.lbl_seleccion.pack(side="left", padx=(8, 0))
        r += 1

        # Tabla de videos (scrollable)
        self.tabla_frame = ctk.CTkScrollableFrame(parent, fg_color=C["bg"],
                                                   corner_radius=0)
        self.tabla_frame.grid(row=r, column=0, sticky="nsew", padx=0, pady=0)
        self.tabla_frame.grid_columnconfigure(2, weight=1)

        self.lbl_tabla_vacia = ctk.CTkLabel(
            self.tabla_frame,
            text="Pega URLs arriba y pulsa Analizar\npara ver los videos disponibles",
            font=ctk.CTkFont(size=12),
            text_color=C["text_sec"])
        self.lbl_tabla_vacia.grid(row=0, column=0, pady=40)
        r += 1

        # Resumen pre-descarga
        self.resumen_frame = ctk.CTkFrame(parent, fg_color=C["panel_alt"],
                                           corner_radius=0, height=40)
        self.resumen_frame.grid(row=r, column=0, sticky="ew", padx=0, pady=0)
        self.resumen_frame.grid_columnconfigure(0, weight=1)
        self.resumen_frame.grid_remove()

        self.lbl_resumen = ctk.CTkLabel(self.resumen_frame, text="",
                                         font=ctk.CTkFont(size=11, weight="bold"),
                                         text_color=C["text"])
        self.lbl_resumen.grid(row=0, column=0, sticky="w", padx=12, pady=6)
        r += 1

        # Botones descarga + progreso
        self.dl_frame = ctk.CTkFrame(parent, fg_color=C["panel"], corner_radius=0)
        self.dl_frame.grid(row=r, column=0, sticky="ew", padx=0, pady=0)
        self.dl_frame.grid_columnconfigure(0, weight=1)
        self.dl_frame.grid_remove()

        dl_btns = ctk.CTkFrame(self.dl_frame, fg_color="transparent")
        dl_btns.grid(row=0, column=0, sticky="ew", padx=12, pady=(8, 4))
        dl_btns.grid_columnconfigure(0, weight=1)

        self.btn_descargar = ctk.CTkButton(
            dl_btns, text="DESCARGAR", height=40,
            font=ctk.CTkFont(size=14, weight="bold"),
            fg_color=C["success"], hover_color=C["success_hover"],
            command=self._on_descargar)
        self.btn_descargar.grid(row=0, column=0, sticky="ew", padx=(0, 8))

        self.btn_cancelar = ctk.CTkButton(
            dl_btns, text="CANCELAR", height=40, width=120,
            font=ctk.CTkFont(size=12, weight="bold"),
            fg_color=C["cancel"], hover_color=C["cancel_hover"],
            command=self._on_cancelar, state="disabled")
        self.btn_cancelar.grid(row=0, column=1)

        # Progreso global
        ctk.CTkLabel(self.dl_frame, text="Global:",
                     font=ctk.CTkFont(size=10),
                     text_color=C["text_sec"]).grid(row=1, column=0, sticky="w", padx=12)
        self.progress_global = ctk.CTkProgressBar(
            self.dl_frame, fg_color=C["border"],
            progress_color=C["accent"], height=6)
        self.progress_global.grid(row=2, column=0, sticky="ew", padx=12)
        self.progress_global.set(0)

        # Progreso actual
        ctk.CTkLabel(self.dl_frame, text="Actual:",
                     font=ctk.CTkFont(size=10),
                     text_color=C["text_sec"]).grid(row=3, column=0, sticky="w", padx=12, pady=(4, 0))
        self.progress_actual = ctk.CTkProgressBar(
            self.dl_frame, fg_color=C["border"],
            progress_color=C["success"], height=6)
        self.progress_actual.grid(row=4, column=0, sticky="ew", padx=12)
        self.progress_actual.set(0)

        self.lbl_progreso = ctk.CTkLabel(self.dl_frame, text="",
                                          font=ctk.CTkFont(size=10),
                                          text_color=C["text_sec"])
        self.lbl_progreso.grid(row=5, column=0, sticky="w", padx=12, pady=(2, 8))
        r += 1

        # Log
        self.log_frame = ctk.CTkFrame(parent, fg_color=C["bg"], corner_radius=0)
        self.log_frame.grid(row=r, column=0, sticky="nsew", padx=0, pady=0)
        self.log_frame.grid_columnconfigure(0, weight=1)
        self.log_frame.grid_rowconfigure(1, weight=1)
        self.log_frame.grid_remove()

        ctk.CTkLabel(self.log_frame, text="Log",
                     font=ctk.CTkFont(size=12, weight="bold"),
                     text_color=C["text"]).grid(row=0, column=0, sticky="w", padx=12, pady=(6, 2))

        self.txt_log = ctk.CTkTextbox(
            self.log_frame, height=120,
            font=ctk.CTkFont(family="Consolas", size=10),
            fg_color=C["log_bg"], border_color=C["border"],
            border_width=1, corner_radius=4, state="disabled")
        self.txt_log.grid(row=1, column=0, sticky="nsew", padx=8, pady=(0, 8))

    # === VISIBILIDAD OPCIONES =================================================

    def _on_tipo_cambio(self):
        tipo = self.var_tipo.get()
        if tipo in ("subs", "ambos"):
            self.frame_subs.grid()
        else:
            self.frame_subs.grid_remove()
        if tipo in ("video", "ambos"):
            self.frame_video.grid()
        else:
            self.frame_video.grid_remove()
        if tipo == "audio":
            self.frame_video.grid_remove()
            self.frame_subs.grid_remove()
        self._actualizar_resumen()

    def _on_calidad_combo(self, valor):
        code = valor.split(" — ")[0].strip()
        self.var_calidad.set(code)
        self._actualizar_resumen()

    def _actualizar_combo_calidad(self):
        """Actualiza el combo de calidad mostrando la maxima real y opciones menores."""
        # Encontrar la maxima resolucion de todos los videos analizados
        max_h = 0
        for v in self.videos_info:
            h = v.get("max_height", 0)
            if h > max_h:
                max_h = h

        if max_h == 0:
            # Sin info de resolucion, mostrar todas las opciones
            cal_values = [f"{c} — {l}" for c, l in CALIDAD_OPTIONS]
            self.combo_calidad.configure(values=cal_values)
            self.combo_calidad.set(cal_values[0])
            self.var_calidad.set("best")
            return

        # Mapear height a opciones: solo las que sean <= max_height
        resol_map = {"2160": 2160, "1080": 1080, "720": 720, "480": 480, "360": 360}
        cal_values = []

        # Primera entrada: calidad original del video
        # Determinar etiqueta de la maxima
        if max_h >= 2160:
            orig_label = f"Original ({max_h}p — 4K)"
        elif max_h >= 1080:
            orig_label = f"Original ({max_h}p — Full HD)"
        elif max_h >= 720:
            orig_label = f"Original ({max_h}p — HD)"
        else:
            orig_label = f"Original ({max_h}p)"
        cal_values.append(f"best — {orig_label}")

        # Resoluciones menores a la maxima
        for code, label in CALIDAD_OPTIONS:
            if code == "best":
                continue
            limit = resol_map.get(code, 0)
            if limit < max_h:
                cal_values.append(f"{code} — {label}")

        self.combo_calidad.configure(values=cal_values)
        # Seleccionar la original por defecto
        self.combo_calidad.set(cal_values[0])
        self.var_calidad.set("best")

    # === DEPS =================================================================

    def _mostrar_deps(self):
        partes = []
        partes.append(f"yt-dlp: {'OK' if self.tiene_ytdlp else 'NO'}")
        partes.append(f"ffmpeg: {'OK' if self.tiene_ffmpeg else 'no'}")
        partes.append(f"Pillow: {'OK' if HAS_PILLOW else 'no (sin thumbnails)'}")
        self.lbl_deps.configure(
            text=" | ".join(partes),
            text_color=C["success"] if self.tiene_ytdlp else C["error"])
        if not self.tiene_ytdlp:
            self.btn_analizar.configure(state="disabled")

    # === ANALISIS =============================================================

    def _on_analizar(self, modo="video"):
        """modo: 'video' = solo videos individuales, 'lista' = expandir playlists."""
        texto = self.txt_urls.get("1.0", "end").strip()
        if not texto:
            self.lbl_estado.configure(text="Pega al menos una URL",
                                       text_color=C["error"])
            return
        urls = parsear_urls(texto)
        if not urls:
            self.lbl_estado.configure(text="No hay URLs validas",
                                       text_color=C["error"])
            return

        urls_a_procesar = []
        for url in urls:
            if modo == "lista":
                # En modo lista: expandir playlists
                if url_tiene_playlist(url) or url_es_solo_playlist(url):
                    pl_id = extraer_playlist_id(url)
                    if pl_id:
                        urls_a_procesar.append(
                            f"https://www.youtube.com/playlist?list={pl_id}")
                    else:
                        urls_a_procesar.append(url)
                else:
                    # URL sin list= param: la analizamos normal,
                    # el thread intentara detectar playlist del JSON
                    urls_a_procesar.append(url)
            else:
                # En modo video: siempre individual, quitar list= si existe
                if url_tiene_playlist(url) and not url_es_solo_playlist(url):
                    clean_url = re.sub(r"[&?]list=[^&]*", "", url)
                    urls_a_procesar.append(clean_url)
                else:
                    urls_a_procesar.append(url)

        if not urls_a_procesar:
            return

        self.cookies_fallaron = False
        self._playlist_detectadas = {}  # {pl_id: pl_title} detectadas en JSON
        self.btn_analizar.configure(state="disabled", text="Analizando...")
        self.btn_analizar_lista.configure(state="disabled")
        self.lbl_estado.configure(text=f"Analizando {len(urls_a_procesar)} URL(s)...",
                                   text_color=C["text_sec"])

        threading.Thread(target=self._analizar_thread,
                         args=(urls_a_procesar, modo), daemon=True).start()

    def _on_cargar_txt(self):
        archivo = filedialog.askopenfilename(
            title="Cargar archivo de URLs",
            filetypes=[("Texto", "*.txt"), ("Todos", "*.*")])
        if archivo:
            try:
                with open(archivo, "r", encoding="utf-8") as f:
                    contenido = f.read()
                self.txt_urls.delete("1.0", "end")
                self.txt_urls.insert("1.0", contenido)
            except Exception as e:
                messagebox.showerror("Error", f"No se pudo leer: {e}")

    def _analizar_thread(self, urls, modo="video"):
        creation_flags = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
        todos_videos = []
        nav = self.var_navegador.get()
        playlists_detectadas = {}  # {pl_id: pl_title}

        for idx, url in enumerate(urls):
            self.after(0, lambda i=idx, t=len(urls): self.lbl_estado.configure(
                text=f"Analizando {i+1}/{t}..."))

            es_pl = url_es_solo_playlist(url)
            usar_cookies = (nav != "ninguno")

            # En modo lista sin list= param: intentar primero con --yes-playlist
            forzar_playlist = (modo == "lista" and not es_pl
                               and not url_tiene_playlist(url))

            resultado = self._ejecutar_ytdlp_analisis(
                url, es_pl, nav if usar_cookies else None, creation_flags,
                detectar_playlist=(modo == "video"),
                forzar_playlist=forzar_playlist)

            # Fallback: si fallo con cookies (db bloqueada), reintentar sin ellas
            detalle_err = resultado.get("detalle", "") if isinstance(resultado, dict) else ""
            if isinstance(resultado, dict) and resultado.get("tipo") == "error" \
               and usar_cookies and "cookie" in detalle_err.lower():
                self.after(0, lambda: self.lbl_estado.configure(
                    text="Cookies bloqueadas, reintentando sin auth..."))
                self.cookies_fallaron = True
                resultado_sin = self._ejecutar_ytdlp_analisis(
                    url, es_pl, None, creation_flags,
                    detectar_playlist=(modo == "video"),
                    forzar_playlist=forzar_playlist)
                if isinstance(resultado_sin, list):
                    resultado = resultado_sin
                elif isinstance(resultado_sin, dict) and resultado_sin.get("tipo") != "error":
                    resultado = resultado_sin

            # Recoger info de playlists detectadas
            if isinstance(resultado, list):
                for v in resultado:
                    pl_id = v.get("_playlist_id")
                    if pl_id:
                        playlists_detectadas[pl_id] = v.get("_playlist_title", "")
                todos_videos.extend(resultado)
            else:
                pl_id = resultado.get("_playlist_id")
                if pl_id:
                    playlists_detectadas[pl_id] = resultado.get("_playlist_title", "")
                todos_videos.append(resultado)

        self.videos_info = todos_videos
        self._playlist_detectadas = playlists_detectadas
        self.after(0, self._mostrar_tabla)

        # Cargar thumbnails en segundo plano
        if HAS_PILLOW:
            self.after(0, lambda: threading.Thread(
                target=self._cargar_thumbnails, daemon=True).start())

    def _ejecutar_ytdlp_analisis(self, url, es_pl, navegador, creation_flags,
                                 detectar_playlist=False, forzar_playlist=False):
        """Ejecuta yt-dlp para analizar una URL.
        Retorna un dict (error/video) o una lista de dicts (playlist).
        Si detectar_playlist=True, incluye _playlist_id/_playlist_title en cada video."""
        args = ["yt-dlp", "--dump-json", "--no-download", "--no-warnings"]
        if navegador:
            args.extend(self._args_cookies(navegador))
        if es_pl or forzar_playlist:
            args.extend(["--flat-playlist", "--yes-playlist"])
        else:
            args.append("--no-playlist")
        args.append(url)

        try:
            result = subprocess.run(
                args, capture_output=True, text=True, timeout=120,
                creationflags=creation_flags)

            if result.returncode != 0:
                stderr = (result.stderr or "").strip()
                return {
                    "url": url, "tipo": "error", "id": "",
                    "titulo": "Error al analizar",
                    "detalle": stderr[:200] if stderr else "Error desconocido",
                    "duracion": 0, "subs_m": [], "subs_a": [],
                }

            lineas = [l for l in result.stdout.strip().splitlines() if l.strip()]
            if not lineas:
                return {
                    "url": url, "tipo": "error", "id": "",
                    "titulo": "Sin datos", "detalle": "Sin respuesta",
                    "duracion": 0, "subs_m": [], "subs_a": [],
                }

            videos = []
            for lj in lineas:
                try:
                    d = json.loads(lj)
                    vid_id = d.get("id", "")
                    dur = d.get("duration", 0) or 0
                    # Detectar resolucion maxima del video
                    max_height = d.get("height", 0) or 0
                    formatos = d.get("formats") or []
                    for fmt in formatos:
                        h = fmt.get("height") or 0
                        if isinstance(h, int) and h > max_height:
                            max_height = h

                    entry = {
                        "url": d.get("webpage_url", d.get("url", url)),
                        "tipo": "video",
                        "id": vid_id,
                        "titulo": d.get("title", vid_id or "Sin titulo"),
                        "duracion": int(dur),
                        "subs_m": list(d.get("subtitles", {}).keys()),
                        "subs_a": list(d.get("automatic_captions", {}).keys()),
                        "detalle": "",
                        "max_height": max_height,
                    }
                    # Capturar info de playlist si existe en el JSON
                    pl_id = d.get("playlist_id") or d.get("playlist")
                    if pl_id and pl_id != vid_id:
                        entry["_playlist_id"] = pl_id
                        entry["_playlist_title"] = d.get("playlist_title", "")
                    videos.append(entry)
                except json.JSONDecodeError:
                    pass

            if len(videos) == 1:
                return videos[0]
            return videos if videos else {
                "url": url, "tipo": "error", "id": "",
                "titulo": "Sin datos", "detalle": "No se pudo parsear JSON",
                "duracion": 0, "subs_m": [], "subs_a": [],
            }

        except subprocess.TimeoutExpired:
            return {
                "url": url, "tipo": "error", "id": "",
                "titulo": "Timeout", "detalle": ">60s",
                "duracion": 0, "subs_m": [], "subs_a": [],
            }
        except FileNotFoundError:
            return {
                "url": url, "tipo": "error", "id": "",
                "titulo": "yt-dlp no encontrado",
                "detalle": "winget install yt-dlp",
                "duracion": 0, "subs_m": [], "subs_a": [],
            }
        except Exception as e:
            return {
                "url": url, "tipo": "error", "id": "",
                "titulo": "Error", "detalle": str(e)[:200],
                "duracion": 0, "subs_m": [], "subs_a": [],
            }

    def _cargar_thumbnails(self):
        """Carga thumbnails en segundo plano y actualiza la tabla."""
        for i, info in enumerate(self.videos_info):
            if info["tipo"] != "video" or not info["id"]:
                continue
            thumb = descargar_thumbnail(info["id"])
            if thumb and i in self._thumb_labels:
                self.after(0, lambda lbl=self._thumb_labels[i], img=thumb:
                           lbl.configure(image=img))

    # === TABLA DE VIDEOS ======================================================

    def _mostrar_tabla(self):
        # Limpiar
        for w in self.tabla_frame.winfo_children():
            w.destroy()
        self.video_checkboxes = {}
        self._thumb_labels = {}

        n_ok = sum(1 for v in self.videos_info if v["tipo"] == "video")
        n_err = sum(1 for v in self.videos_info if v["tipo"] == "error")

        if not self.videos_info:
            ctk.CTkLabel(self.tabla_frame, text="No se encontraron videos",
                         font=ctk.CTkFont(size=12),
                         text_color=C["text_sec"]).grid(row=0, column=0, pady=20)
            self.btn_analizar.configure(state="normal", text="Analizar")
            return

        for i, info in enumerate(self.videos_info):
            row_bg = C["bg"] if i % 2 == 0 else C["panel"]
            row_f = ctk.CTkFrame(self.tabla_frame, fg_color=row_bg,
                                  corner_radius=0)
            row_f.grid(row=i, column=0, sticky="ew", padx=0, pady=0)
            row_f.grid_columnconfigure(2, weight=1)
            self.tabla_frame.grid_columnconfigure(0, weight=1)

            if info["tipo"] == "video":
                # Checkbox
                var = ctk.BooleanVar(value=True)
                self.video_checkboxes[i] = var
                ctk.CTkCheckBox(row_f, text="", variable=var, width=24,
                                command=self._actualizar_resumen
                                ).grid(row=0, column=0, rowspan=2, padx=(8, 4), pady=4)

                # Thumbnail placeholder
                thumb_lbl = ctk.CTkLabel(row_f, text="", width=THUMB_SIZE[0],
                                          height=THUMB_SIZE[1],
                                          fg_color=C["border"], corner_radius=4)
                thumb_lbl.grid(row=0, column=1, rowspan=2, padx=(0, 8), pady=4)
                self._thumb_labels[i] = thumb_lbl

                # Titulo
                ctk.CTkLabel(row_f, text=info["titulo"],
                             font=ctk.CTkFont(size=11),
                             text_color=C["text"], anchor="w",
                             wraplength=500
                             ).grid(row=0, column=2, sticky="w", pady=(4, 0))

                # Info
                dur = info["duracion"]
                dur_str = f"{dur//60}:{dur%60:02d}" if dur else "?"
                max_h = info.get("max_height", 0)
                res_str = f"{max_h}p" if max_h else "?"
                subs_str = f"Subs: {len(info['subs_m'])}m/{len(info['subs_a'])}a"
                ctk.CTkLabel(row_f, text=f"{dur_str}  |  {res_str}  |  {subs_str}",
                             font=ctk.CTkFont(size=9),
                             text_color=C["text_sec"], anchor="w"
                             ).grid(row=1, column=2, sticky="w", pady=(0, 4))

            else:  # error
                ctk.CTkLabel(row_f, text="X", width=24,
                             font=ctk.CTkFont(size=12, weight="bold"),
                             text_color=C["error"]
                             ).grid(row=0, column=0, padx=(8, 4), pady=4)

                ctk.CTkLabel(row_f, text=info["titulo"],
                             font=ctk.CTkFont(size=11),
                             text_color=C["error"], anchor="w"
                             ).grid(row=0, column=2, sticky="w", pady=(4, 0))
                ctk.CTkLabel(row_f, text=info.get("detalle", ""),
                             font=ctk.CTkFont(size=9),
                             text_color=C["text_sec"], anchor="w"
                             ).grid(row=1, column=2, sticky="w", pady=(0, 4))

        # Estado
        resumen = f"{n_ok} video(s)"
        if n_err:
            resumen += f", {n_err} error(es)"
        self.lbl_estado.configure(text=resumen,
                                   text_color=C["success"] if n_err == 0 else C["warning"])
        self.btn_analizar.configure(state="normal", text="Analizar video")
        self.btn_analizar_lista.configure(state="normal")

        # Banner de playlist detectada (solo si analizamos videos individuales)
        self._mostrar_banner_playlist()

        # Actualizar combo de calidad segun resolucion maxima detectada
        self._actualizar_combo_calidad()

        # Mostrar secciones
        if n_ok > 0:
            self.resumen_frame.grid()
            self.dl_frame.grid()
            self._actualizar_resumen()

    def _seleccionar_todos(self):
        for var in self.video_checkboxes.values():
            var.set(True)
        self._actualizar_resumen()

    def _deseleccionar_todos(self):
        for var in self.video_checkboxes.values():
            var.set(False)
        self._actualizar_resumen()

    def _invertir_seleccion(self):
        for var in self.video_checkboxes.values():
            var.set(not var.get())
        self._actualizar_resumen()

    def _actualizar_resumen(self):
        n_sel = sum(1 for v in self.video_checkboxes.values() if v.get())
        n_total = len(self.video_checkboxes)

        if n_total == 0:
            return

        tipo = self.var_tipo.get()
        tipo_txt = {"subs": "Subtitulos", "video": "Video MP4",
                    "audio": "Audio MP3", "ambos": "Subs + Video"}
        idioma = self.var_idioma.get().upper()
        cal = self.var_calidad.get()

        partes = [f"{n_sel}/{n_total} seleccionados", tipo_txt.get(tipo, tipo)]
        if tipo in ("subs", "ambos"):
            partes.append(f"Idioma: {idioma}")
            partes.append(f"Formato: {self.var_formato.get().upper()}")
        if tipo in ("video", "ambos"):
            partes.append(f"Calidad: {cal}")

        self.lbl_resumen.configure(text="  |  ".join(partes))
        self.lbl_seleccion.configure(text=f"{n_sel} sel.")

    # === PLAYLIST BANNER ======================================================

    def _mostrar_banner_playlist(self):
        """Muestra banner si se detectaron playlists en los videos analizados."""
        playlists = getattr(self, "_playlist_detectadas", {})
        if not playlists:
            self.playlist_banner.grid_remove()
            return

        # Mostrar la primera playlist detectada (caso comun: una sola)
        pl_id = list(playlists.keys())[0]
        pl_title = playlists[pl_id] or pl_id
        n_current = sum(1 for v in self.videos_info if v.get("tipo") == "video")

        self.lbl_playlist_info.configure(
            text=f"Playlist: \"{pl_title}\"  ({n_current} video(s) cargados)")
        self.btn_cargar_playlist.configure(
            text=f"Cargar playlist completa",
            command=self._on_cargar_playlist_detectada)
        self.playlist_banner.grid()

    def _on_cargar_playlist_detectada(self):
        """Recarga usando la playlist completa detectada."""
        playlists = getattr(self, "_playlist_detectadas", {})
        if not playlists:
            return
        pl_id = list(playlists.keys())[0]
        pl_url = f"https://www.youtube.com/playlist?list={pl_id}"

        # Poner la URL de playlist en el textbox y lanzar analisis en modo lista
        self.txt_urls.delete("1.0", "end")
        self.txt_urls.insert("1.0", pl_url)
        self._on_analizar(modo="lista")

    # === CARPETA ==============================================================

    def _on_cambiar_carpeta(self, tipo="subs"):
        if tipo == "subs":
            inicial = self.carpeta_subs
            titulo = "Carpeta para subtitulos"
        else:
            inicial = self.carpeta_video
            titulo = "Carpeta para videos / audio"

        nueva = filedialog.askdirectory(initialdir=inicial, title=titulo)
        if nueva:
            if tipo == "subs":
                self.carpeta_subs = nueva
                self.lbl_carpeta_subs.configure(text=nueva)
            else:
                self.carpeta_video = nueva
                self.lbl_carpeta_video.configure(text=nueva)

    def _on_abrir_carpeta(self, tipo="subs"):
        carpeta = self.carpeta_subs if tipo == "subs" else self.carpeta_video
        os.makedirs(carpeta, exist_ok=True)
        if sys.platform == "win32":
            os.startfile(carpeta)
        elif sys.platform == "darwin":
            subprocess.Popen(["open", carpeta])
        else:
            subprocess.Popen(["xdg-open", carpeta])

    def _on_borrar_anteriores(self, tipo="subs"):
        if tipo == "subs":
            carpeta = self.carpeta_subs
            extensiones = (".srt", ".vtt", ".txt")
        else:
            carpeta = self.carpeta_video
            extensiones = (".mp4", ".mkv", ".webm", ".mp3", ".m4a", ".opus", ".part")

        if not os.path.isdir(carpeta):
            messagebox.showinfo("Borrar anteriores",
                                 f"La carpeta no existe:\n{carpeta}")
            return

        archivos = [f for f in os.listdir(carpeta)
                    if f.lower().endswith(extensiones)
                    and os.path.isfile(os.path.join(carpeta, f))]

        if not archivos:
            messagebox.showinfo("Borrar anteriores",
                                 "No hay archivos anteriores para borrar.")
            return

        if not messagebox.askyesno(
                "Confirmar borrado",
                f"Se borraran {len(archivos)} archivo(s) en:\n{carpeta}\n\n"
                f"Esta accion no se puede deshacer. Continuar?"):
            return

        borrados = 0
        errores = 0
        for nombre in archivos:
            try:
                os.remove(os.path.join(carpeta, nombre))
                borrados += 1
            except Exception as e:
                errores += 1
                self._log(f"[ERROR borrando {nombre}] {e}")

        msg = f"Borrados: {borrados}"
        if errores:
            msg += f"\nErrores: {errores} (ver log)"
        messagebox.showinfo("Borrar anteriores", msg)
        self._log(f"[LIMPIEZA {tipo}] {borrados} archivo(s) borrado(s) en {carpeta}")

    # === DESCARGA =============================================================

    def _on_descargar(self):
        seleccionados = [self.videos_info[i] for i, var
                         in self.video_checkboxes.items() if var.get()]
        if not seleccionados:
            messagebox.showwarning("Sin seleccion",
                                    "Selecciona al menos un video.")
            return

        tipo = self.var_tipo.get()
        if tipo in ("video", "ambos") and not self.tiene_ffmpeg:
            if not messagebox.askyesno("ffmpeg no detectado",
                                        "ffmpeg no esta en PATH. Continuar?"):
                return

        self._guardar_config()
        self.descarga_activa = True
        self.cancelar_solicitado = False
        self.btn_descargar.configure(state="disabled", text="Descargando...",
                                      fg_color=C["text_sec"])
        self.btn_cancelar.configure(state="normal")
        self.btn_analizar.configure(state="disabled")
        self.btn_analizar_lista.configure(state="disabled")
        self.log_frame.grid()
        self.progress_global.set(0)
        self.progress_actual.set(0)

        threading.Thread(target=self._descargar_thread,
                         args=(seleccionados,), daemon=True).start()

    def _on_cancelar(self):
        self.cancelar_solicitado = True
        if self.proceso_actual:
            try:
                self.proceso_actual.terminate()
            except Exception:
                pass
        self._log("[CANCELACION SOLICITADA]")

    def _descargar_thread(self, videos):
        tipo = self.var_tipo.get()
        dl_subs = tipo in ("subs", "ambos")
        dl_video = tipo in ("video", "ambos")
        dl_audio = tipo == "audio"
        nav = self.var_navegador.get()
        # Si las cookies fallaron durante el analisis, no intentar usarlas en descarga
        if self.cookies_fallaron and nav != "ninguno":
            self._log("Aviso: cookies no disponibles (navegador abierto?), descargando sin auth")
            nav = "ninguno"

        carpeta_subs = self.carpeta_subs
        carpeta_video = self.carpeta_video
        carpeta_audio = self.carpeta_video  # audio va a la misma carpeta de video

        if dl_subs:
            os.makedirs(carpeta_subs, exist_ok=True)
        if dl_video:
            os.makedirs(carpeta_video, exist_ok=True)
        if dl_audio:
            os.makedirs(carpeta_audio, exist_ok=True)

        # Log file
        fecha = datetime.now().strftime("%Y%m%d_%H%M%S")
        log_dir = carpeta_subs if dl_subs else (carpeta_video if dl_video else carpeta_audio)
        log_path = os.path.join(log_dir, f"log_{fecha}.txt")
        with open(log_path, "w", encoding="utf-8") as f:
            f.write(f"=== LOG v{VERSION} === {fecha}\n")
            f.write(f"Tipo: {tipo} | Videos: {len(videos)}\n\n")

        total = len(videos)
        stats = {"ok_s": 0, "ok_v": 0, "ok_a": 0, "fail": 0,
                 "sin_s": 0, "files_s": 0, "files_v": 0, "files_a": 0}

        self._log(f"Descarga iniciada: {total} video(s) | Tipo: {tipo}")

        for i, vid in enumerate(videos):
            if self.cancelar_solicitado:
                self._log("\nCancelado por el usuario.")
                break

            url = vid["url"]
            self._log(f"\n[{i+1}/{total}] {vid['titulo']}")
            self.after(0, lambda i=i, t=total: self._set_progreso_global(i, t))
            self.after(0, lambda: self.progress_actual.set(0))

            prefijo = self._prefijo_archivo(i + 1, total, vid.get("titulo", ""))

            if dl_subs:
                r = self._dl_subs(url, carpeta_subs, nav, prefijo)
                self._log(f"  Subs: {r['estado']} (+{r['n']})")
                if r["estado"] == "OK":
                    stats["ok_s"] += 1; stats["files_s"] += r["n"]
                elif r["estado"] == "SIN_SUBS":
                    stats["sin_s"] += 1
                else:
                    stats["fail"] += 1
                self._log_file(log_path, f"[SUBS {r['estado']}] +{r['n']} | {url}")

            if self.cancelar_solicitado:
                break

            if dl_video:
                r = self._dl_video(url, carpeta_video, nav, prefijo)
                self._log(f"  Video: {r['estado']} (+{r['n']})")
                if r["estado"] == "OK":
                    stats["ok_v"] += 1; stats["files_v"] += r["n"]
                else:
                    stats["fail"] += 1
                self._log_file(log_path, f"[VIDEO {r['estado']}] +{r['n']} | {url}")

            if dl_audio:
                r = self._dl_audio(url, carpeta_audio, nav, prefijo)
                self._log(f"  Audio: {r['estado']} (+{r['n']})")
                if r["estado"] == "OK":
                    stats["ok_a"] += 1; stats["files_a"] += r["n"]
                else:
                    stats["fail"] += 1
                self._log_file(log_path, f"[AUDIO {r['estado']}] +{r['n']} | {url}")

            self._log_file(log_path, "---")
            if i < total - 1 and not self.cancelar_solicitado:
                import time; time.sleep(3)

        # Resumen
        self._log("\n" + "=" * 50)
        self._log("RESUMEN")
        if dl_subs:
            self._log(f"  Subs OK: {stats['ok_s']} | Sin: {stats['sin_s']} | Archivos: {stats['files_s']}")
        if dl_video:
            self._log(f"  Video OK: {stats['ok_v']} | Archivos: {stats['files_v']}")
        if dl_audio:
            self._log(f"  Audio OK: {stats['ok_a']} | Archivos: {stats['files_a']}")
        if stats["fail"]:
            self._log(f"  Errores: {stats['fail']}")
        self._log(f"  Log: {log_path}")
        self._log("=" * 50)

        self.after(0, self._descarga_fin)

    def _prefijo_archivo(self, idx, total, titulo):
        """Construye un prefijo '01 - Titulo saneado' para el nombre de archivo.
        Escapa % para que yt-dlp no lo interprete como plantilla."""
        ancho = max(2, len(str(total)))
        titulo = (titulo or "sin_titulo").strip()
        # Reemplazar caracteres invalidos en Windows
        titulo = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", titulo)
        titulo = re.sub(r"\s+", " ", titulo)[:120].strip(" .")
        if not titulo:
            titulo = "sin_titulo"
        prefijo = f"{idx:0{ancho}d} - {titulo}"
        # Escapar % para que yt-dlp no lo confunda con su plantilla
        return prefijo.replace("%", "%%")

    def _dl_subs(self, url, carpeta, nav, prefijo=None):
        idiomas = self.var_idioma.get()
        gen_txt = self.var_formato.get() == "txt"
        auto = self.var_autosubs.get()
        cf = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0

        antes = self._contar(carpeta, "*.srt")
        args = ["yt-dlp", "--skip-download", "--write-subs",
                "--sub-langs", idiomas, "--convert-subs", "srt",
                "--no-progress", "--ignore-errors",
                "--sleep-interval", "2", "--max-sleep-interval", "4",
                "--no-playlist"]
        if auto:
            args.append("--write-auto-subs")
        if nav != "ninguno":
            args.extend(self._args_cookies(nav))
        nombre = prefijo if prefijo else "%(title)s"
        args.extend(["--output", os.path.join(carpeta, f"{nombre} [%(id)s].%(ext)s")])
        args.append(url)

        out = self._ejecutar_proceso(args, cf)
        despues = self._contar(carpeta, "*.srt")
        nuevos = despues - antes

        if gen_txt and nuevos > 0:
            self._srt_a_txt(carpeta, nuevos)

        if nuevos > 0:
            return {"estado": "OK", "n": nuevos}
        elif "429" in out:
            return {"estado": "ERROR_429", "n": 0}
        else:
            return {"estado": "SIN_SUBS", "n": 0}

    def _dl_video(self, url, carpeta, nav, prefijo=None):
        cal = CALIDAD_MAP.get(self.var_calidad.get(), CALIDAD_MAP["1080"])
        cf = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0

        antes = self._contar(carpeta, "*.mp4")
        args = ["yt-dlp", "--format", cal, "--merge-output-format", "mp4",
                "--ignore-errors", "--no-playlist",
                "--sleep-interval", "2", "--max-sleep-interval", "4"]
        if self.var_normalizar.get():
            args.extend(["--postprocessor-args",
                         "ffmpeg:-af loudnorm=I=-16:TP=-1.5:LRA=11"])
        if nav != "ninguno":
            args.extend(self._args_cookies(nav))
        nombre = prefijo if prefijo else "%(title)s"
        args.extend(["--output", os.path.join(carpeta, f"{nombre} [%(id)s].%(ext)s")])
        args.append(url)

        out = self._ejecutar_proceso(args, cf, parsear_progreso=True)
        despues = self._contar(carpeta, "*.mp4")
        nuevos = despues - antes

        if nuevos > 0:
            return {"estado": "OK", "n": nuevos}
        elif "429" in out:
            return {"estado": "ERROR_429", "n": 0}
        else:
            return {"estado": "ERROR", "n": 0}

    def _dl_audio(self, url, carpeta, nav, prefijo=None):
        cf = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
        antes = self._contar(carpeta, "*.mp3") + self._contar(carpeta, "*.m4a")

        args = ["yt-dlp", "--format", "bestaudio[ext=m4a]/bestaudio",
                "--extract-audio", "--audio-format", "mp3",
                "--audio-quality", "0",
                "--ignore-errors", "--no-playlist",
                "--sleep-interval", "2", "--max-sleep-interval", "4"]
        if nav != "ninguno":
            args.extend(self._args_cookies(nav))
        nombre = prefijo if prefijo else "%(title)s"
        args.extend(["--output", os.path.join(carpeta, f"{nombre} [%(id)s].%(ext)s")])
        args.append(url)

        out = self._ejecutar_proceso(args, cf, parsear_progreso=True)
        despues = self._contar(carpeta, "*.mp3") + self._contar(carpeta, "*.m4a")
        nuevos = despues - antes

        if nuevos > 0:
            return {"estado": "OK", "n": nuevos}
        elif "429" in out:
            return {"estado": "ERROR_429", "n": 0}
        else:
            return {"estado": "ERROR", "n": 0}

    def _ejecutar_proceso(self, args, cf, parsear_progreso=False):
        """Ejecuta un proceso y retorna la salida como string."""
        try:
            proc = subprocess.Popen(
                args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, creationflags=cf)
            self.proceso_actual = proc
            lines = []
            for line in proc.stdout:
                lines.append(line)
                if parsear_progreso and "[download]" in line and "%" in line:
                    m = re.search(r"(\d+\.?\d*)%", line)
                    if m:
                        pct = float(m.group(1)) / 100
                        self.after(0, lambda p=pct: self.progress_actual.set(p))
                if self.cancelar_solicitado:
                    proc.terminate()
                    break
            proc.wait()
            self.proceso_actual = None
            salida = "".join(lines)

            # Deteccion de cookies caducadas
            try:
                import cookies_cache
                if cookies_cache.detectar_auth_fallo(salida):
                    nav = self.var_navegador.get()
                    if nav and nav != "ninguno" and cookies_cache.ruta_cookies(nav):
                        self._log(f"  [AVISO] Cookies de {nav} caducadas. Solicitando recaptura...")
                        cookies_cache.invalidar_cookies(nav)
                        self.after(0, lambda: self._pedir_recapturar_cookies(nav))
            except Exception:
                pass

            return salida
        except Exception as e:
            self._log(f"  ERROR: {e}")
            return str(e)

    # --- COOKIES: cache y recaptura -----------------------------------------

    def _args_cookies(self, navegador):
        """Devuelve la lista de args para yt-dlp respecto a cookies.
        - Si hay archivo cacheado valido: usa --cookies FILE (sin cerrar navegador).
        - Si no: extrae del navegador en vivo (requiere navegador cerrado).
        """
        if not navegador or navegador == "ninguno":
            return []
        try:
            import cookies_cache
        except ImportError:
            return ["--cookies-from-browser", navegador]

        cache = cookies_cache.ruta_cookies(navegador)
        if cache:
            return ["--cookies", str(cache)]

        # No hay cache: intentar extraer ahora
        from bootstrap import ruta_ytdlp
        ytdlp = str(ruta_ytdlp() or "yt-dlp")
        self._log(f"  [INFO] Extrayendo cookies de {navegador} por primera vez...")
        ok, msg = cookies_cache.extraer_cookies(navegador, ytdlp)
        self._log(f"  [COOKIES] {msg}")
        cache = cookies_cache.ruta_cookies(navegador)
        if cache:
            return ["--cookies", str(cache)]
        # Fallback al metodo en vivo
        return ["--cookies-from-browser", navegador]

    def _pedir_recapturar_cookies(self, navegador):
        """Dialogo cuando las cookies han caducado."""
        if messagebox.askyesno(
                "Cookies caducadas",
                f"Las cookies de {navegador.upper()} han caducado o no sirven.\n\n"
                f"Para recapturarlas necesitas CERRAR {navegador} completamente.\n\n"
                f"¿Has cerrado {navegador}?  (Si/No)"):
            from bootstrap import ruta_ytdlp
            import cookies_cache
            ytdlp = str(ruta_ytdlp() or "yt-dlp")
            ok, msg = cookies_cache.extraer_cookies(navegador, ytdlp)
            self._log(f"  [RECAPTURA] {msg}")
            if ok:
                messagebox.showinfo("Cookies actualizadas",
                                     "Cookies recapturadas correctamente.\n"
                                     "La proxima descarga las usara.")
            else:
                messagebox.showwarning("No se pudo recapturar",
                                        f"No se pudieron extraer las cookies:\n\n{msg}\n\n"
                                        f"Asegurate de que {navegador} esta cerrado.")

    # === UTILIDADES ===========================================================

    def _contar(self, carpeta, patron):
        try:
            return len(list(Path(carpeta).rglob(patron)))
        except Exception:
            return 0

    def _srt_a_txt(self, carpeta, n):
        try:
            srts = sorted(Path(carpeta).rglob("*.srt"),
                          key=lambda p: p.stat().st_mtime, reverse=True)[:n]
            for srt in srts:
                lineas = srt.read_text(encoding="utf-8").splitlines()
                salida, ultimo = [], ""
                for l in lineas:
                    if re.match(r"^\d+$", l.strip()) or "-->" in l or not l.strip():
                        continue
                    limpia = l.strip()
                    if limpia != ultimo:
                        salida.append(limpia)
                        ultimo = limpia
                srt.with_suffix(".txt").write_text("\n".join(salida), encoding="utf-8")
                srt.unlink()
        except Exception as e:
            self._log(f"  Error SRT->TXT: {e}")

    def _log(self, texto):
        def _a():
            self.txt_log.configure(state="normal")
            self.txt_log.insert("end", texto + "\n")
            self.txt_log.see("end")
            self.txt_log.configure(state="disabled")
        self.after(0, _a)

    def _log_file(self, path, texto):
        try:
            with open(path, "a", encoding="utf-8") as f:
                f.write(texto + "\n")
        except Exception:
            pass

    def _set_progreso_global(self, current, total):
        if total > 0:
            self.progress_global.set((current + 1) / total)
            self.lbl_progreso.configure(
                text=f"Video {current+1} de {total}")

    def _descarga_fin(self):
        self.descarga_activa = False
        self.btn_descargar.configure(state="normal", text="DESCARGAR",
                                      fg_color=C["success"])
        self.btn_cancelar.configure(state="disabled")
        self.btn_analizar.configure(state="normal")
        self.btn_analizar_lista.configure(state="normal")
        self.progress_global.set(1)
        self.progress_actual.set(1)
        estado = "Cancelada" if self.cancelar_solicitado else "Completada"
        color = C["warning"] if self.cancelar_solicitado else C["success"]
        self.lbl_progreso.configure(text=f"Descarga {estado}", text_color=color)

    def _on_limpiar(self):
        self.txt_urls.delete("1.0", "end")
        self.videos_info = []
        self.video_checkboxes = {}
        self._playlist_detectadas = {}
        self.lbl_estado.configure(text="")
        self.resumen_frame.grid_remove()
        self.dl_frame.grid_remove()
        self.log_frame.grid_remove()
        self.playlist_banner.grid_remove()
        # Restaurar tabla
        for w in self.tabla_frame.winfo_children():
            w.destroy()
        self.lbl_tabla_vacia = ctk.CTkLabel(
            self.tabla_frame,
            text="Pega URLs arriba y pulsa Analizar\npara ver los videos disponibles",
            font=ctk.CTkFont(size=12), text_color=C["text_sec"])
        self.lbl_tabla_vacia.grid(row=0, column=0, pady=40)
        self.txt_log.configure(state="normal")
        self.txt_log.delete("1.0", "end")
        self.txt_log.configure(state="disabled")
        self.progress_global.set(0)
        self.progress_actual.set(0)
        self.lbl_progreso.configure(text="")
        self.lbl_seleccion.configure(text="")


# === BOOTSTRAP DIALOG =========================================================

class BootstrapDialog(ctk.CTkToplevel):
    """Ventana de progreso que descarga yt-dlp/ffmpeg antes de abrir la app."""
    def __init__(self, master):
        super().__init__(master)
        self.title("Preparando herramientas")
        self.geometry("480x180")
        self.resizable(False, False)
        self.grab_set()
        self.protocol("WM_DELETE_WINDOW", lambda: None)  # no cerrable

        self.resultado = None

        ctk.CTkLabel(self, text="Descargando herramientas necesarias...",
                     font=ctk.CTkFont(size=14, weight="bold")
                     ).pack(pady=(20, 8))
        self.lbl_msg = ctk.CTkLabel(self, text="Iniciando...",
                                     font=ctk.CTkFont(size=11))
        self.lbl_msg.pack(pady=4)
        self.progress = ctk.CTkProgressBar(self, width=420)
        self.progress.pack(pady=10)
        self.progress.set(0)
        ctk.CTkLabel(self,
                     text="Solo la primera vez. Las proximas aperturas seran instantaneas.",
                     font=ctk.CTkFont(size=9),
                     text_color="gray").pack(pady=(4, 0))

        self.after(100, self._arrancar)

    def _progress_cb(self, msg, pct):
        def _upd():
            self.lbl_msg.configure(text=msg)
            if pct < 0:
                # indeterminado: animacion simple
                val = (self.progress.get() + 0.02) % 1.0
                self.progress.set(val)
            else:
                self.progress.set(pct / 100)
        self.after(0, _upd)

    def _arrancar(self):
        def _worker():
            try:
                from bootstrap import ensure_tools
                self.resultado = ensure_tools(progress_cb=self._progress_cb)
            except Exception as e:
                self.resultado = {"errores": [str(e)], "ytdlp": None, "ffmpeg": None}
            self.after(0, self.destroy)
        threading.Thread(target=_worker, daemon=True).start()


def _preparar_herramientas():
    """Si faltan yt-dlp o ffmpeg en %LOCALAPPDATA% y PATH, las descarga."""
    try:
        from bootstrap import ruta_ytdlp, ruta_ffmpeg, BIN_DIR
    except ImportError:
        return  # bootstrap no disponible, seguir sin bloquear
    if ruta_ytdlp() and ruta_ffmpeg():
        # Ya estan. Anadir BIN_DIR al PATH por si acaso
        os.environ["PATH"] = f"{BIN_DIR}{os.pathsep}{os.environ.get('PATH', '')}"
        return

    # Faltan: mostrar dialogo modal
    ctk.set_appearance_mode("light")
    root = ctk.CTk()
    root.withdraw()
    dlg = BootstrapDialog(root)
    root.wait_window(dlg)
    res = dlg.resultado or {}
    if res.get("errores"):
        messagebox.showwarning(
            "Aviso",
            "No se pudieron descargar todas las herramientas:\n\n"
            + "\n".join(res["errores"])
            + "\n\nLa app abrira igualmente; algunas funciones pueden fallar.")
    root.destroy()


# === MAIN =====================================================================

if __name__ == "__main__":
    try:
        _preparar_herramientas()
        app = App()
        app.mainloop()
    except Exception as e:
        import traceback
        error_msg = traceback.format_exc()
        print(f"\n[ERROR FATAL]\n{error_msg}", file=sys.stderr)
        # Intentar mostrar en messagebox
        try:
            import tkinter as tk
            root = tk.Tk()
            root.withdraw()
            messagebox.showerror("Error fatal", f"La aplicacion no pudo iniciar:\n\n{e}\n\nDetalle:\n{error_msg[:500]}")
            root.destroy()
        except Exception:
            pass
        input("\nPulsa Enter para cerrar...")
        sys.exit(1)
