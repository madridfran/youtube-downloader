# YouTube Downloader v7.0

**Descarga vídeos, audio y subtítulos de YouTube con una interfaz sencilla.**
`www.tradervolume.com`

---

## Uso en 10 segundos

1. **Doble clic** en `YouTube_Downloader_v7.bat`
2. La primera vez se descargan solas las herramientas (yt-dlp, ffmpeg). Espera.
3. Se abre la ventana de la aplicación.
4. Pega una URL de YouTube (vídeo o playlist) → **Analizar**.
5. Marca los vídeos que quieras y pulsa **DESCARGAR**.

Listo. Los archivos se guardan en `C:\Users\TU_USUARIO\Downloads\subtitulosyoutube\` (puedes cambiar la carpeta desde la app).

---

## Requisitos

- **Windows 10 / 11** (x64 o ARM64).
- **Python 3.10 o superior** instalado y con "Add to PATH" activado.
  Descarga: https://www.python.org/downloads/

El resto de dependencias se instalan solas al abrir la app por primera vez.

---

## Opciones de la app

### ¿Qué descargar?
- **Solo subtítulos** — archivo `.txt` plano o `.srt` con tiempos.
- **Solo vídeo (MP4)** — elige calidad (480p a 4K).
- **Solo audio (MP3)** — extrae el audio del vídeo.
- **Subtítulos + Vídeo** — los dos a la vez.

### Idioma de subtítulos
- `ES` / `EN` / `ES+EN`.
- **Incluir auto-generados**: si un vídeo no tiene subtítulos oficiales, baja los que genera YouTube automáticamente.

### Formato de subtítulo
- **TXT (plano)**: texto limpio, sin marcas de tiempo. Ideal para leer o usar en IA.
- **SRT (tiempos)**: formato estándar de subtítulos.

### Carpetas de salida
- Cada sección (Subtítulos / Vídeos+Audio) tiene su propia carpeta.
- Botón **Cambiar**: elegir otra ruta.
- Botón **Abrir**: abrir la carpeta en el explorador.
- Botón **Borrar anteriores** (rojo): borra archivos previos de esa carpeta. Pide confirmación.

### Nombres de archivo

Los archivos se nombran así para que sea fácil identificarlos:

```
01 - Título del vídeo [abc123xyz].es.srt
02 - Otro título [def456uvw].es.txt
```

El número al principio mantiene el orden de la playlist. El código entre corchetes es el ID único del vídeo en YouTube.

---

## Autenticación con cookies (vídeos privados, miembros, etc.)

### Qué son

Muchos vídeos de YouTube requieren que estés logueado (listas de miembros,
vídeos privados, contenido por edad, etc.). La app puede usar las **cookies de
tu navegador** para autenticarse con tu cuenta.

### Cómo usarlo

1. En la sección "Autenticación YouTube" elige tu navegador (Chrome, Edge, Firefox, Brave).
2. **Solo la primera vez**: cierra el navegador completamente antes de analizar/descargar.
   La app extrae las cookies y las guarda cacheadas.
3. **Después**: ya puedes mantener el navegador abierto. La app usará las cookies guardadas.

### Cuándo hay que recapturar

Las cookies caducan con el tiempo (o cuando cambias la contraseña, cierras sesión, etc.).

Cuando ocurra, la app detectará el fallo automáticamente y mostrará un aviso:

> **"Las cookies de CHROME han caducado. Cierra Chrome y pulsa Sí."**

Pasos:

1. Cierra completamente el navegador indicado.
2. Pulsa "Sí" en el diálogo.
3. La app recaptura y actualiza las cookies.
4. Puedes volver a abrir el navegador y seguir descargando.

### Reset manual

Si quieres forzar la recaptura sin esperar a que caduquen, borra la carpeta:

```
%LOCALAPPDATA%\YouTubeDownloader\cookies\
```

La próxima descarga volverá a extraerlas (requiere el navegador cerrado).

---

## Portable

Puedes **copiar la carpeta del proyecto a cualquier sitio**:
- Un disco duro externo.
- Un USB.
- Otra carpeta con otro nombre (`mi_descargador`, `youtube_tools`, etc.).

Solo asegúrate de **mover la carpeta completa** (`.bat` + carpeta `engine/`).
El `.bat` detecta su propia ubicación y encuentra los scripts automáticamente.

---

## Estructura del proyecto

```
Proyecto_Descargayoutube/
├── YouTube_Downloader_v7.bat    ← El que abres con doble clic
├── README.md                    ← Este archivo
├── android/                     ← Versión Android (en desarrollo). Ver android/PLAN.md
└── engine/                      ← No tocar. Todos los scripts.
    ├── v7.0_descargador_youtube_gui.py
    ├── bootstrap.py             ← Descarga/actualiza yt-dlp y ffmpeg
    ├── cookies_cache.py         ← Cache y recaptura de cookies
    ├── licensing.py             ← Módulo de licencias (uso futuro)
    ├── build.spec               ← Configuración para generar .exe
    ├── installer.iss            ← Configuración del instalador Windows
    ├── build_exe_x64.bat        ← Para generar el .exe (x64)
    ├── build_exe_arm64.bat      ← Para generar el .exe (ARM64)
    └── requirements.txt
```

---

## Problemas comunes

| Síntoma | Solución |
|---|---|
| "Python no está en PATH" | Reinstala Python marcando *"Add Python to PATH"*. |
| La app no puede extraer cookies | Cierra completamente el navegador (revisa bandeja del sistema). |
| Diálogo "Cookies caducadas" | Cierra el navegador indicado y pulsa "Sí". |
| Error 429 | YouTube te limitó temporalmente. Espera 10-15 minutos. |
| yt-dlp da errores raros | La app auto-actualiza cada 24 h. Si urgente, borra `%LOCALAPPDATA%\YouTubeDownloader\bin\` y reabre. |
| El .bat se cierra sin mostrar error | Abre `cmd` manualmente, navega a la carpeta y ejecuta el .bat desde ahí para ver el mensaje. |

---

## Para desarrolladores

### Generar ejecutable Windows

Desde la carpeta `engine/`:

**x64**:
```
build_exe_x64.bat
```

**ARM64** (en una máquina Windows ARM64, no cross-compile):
```
build_exe_arm64.bat
```

Resultado:
- `engine/dist/YouTubeDownloader/YouTubeDownloader.exe` (portable)
- `engine/dist_installer/YouTubeDownloader-7.0-<arch>-setup.exe` (instalador, requiere Inno Setup: https://jrsoftware.org/isdl.php)

### Datos de usuario

La app guarda en `%LOCALAPPDATA%\YouTubeDownloader\`:

| Carpeta/archivo | Qué contiene |
|---|---|
| `bin/` | yt-dlp.exe, ffmpeg.exe (descargados automáticamente) |
| `cookies/` | Archivos de cookies por navegador (cookies_chrome.txt, cookies_edge.txt…) |
| `bootstrap_state.json` | Cache de comprobación de versiones |
| `license.json` | Clave de licencia (cuando se implemente) |

Para resetear completamente, borra toda la carpeta `%LOCALAPPDATA%\YouTubeDownloader\`.

---

© www.tradervolume.com
