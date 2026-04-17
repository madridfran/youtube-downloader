# YouTube Downloader Android — Plan técnico

Versión Android del proyecto desktop. Mismas funcionalidades clave, adaptadas
a las limitaciones y posibilidades del móvil.

---

## Objetivo

Que el usuario del Samsung (u otro Android) pueda:

1. Estar viendo un vídeo de YouTube.
2. Pulsar **Compartir** → **YouTubeDownloader**.
3. Ver un diálogo sencillo y elegir qué descargar.
4. El archivo aparece en `Downloads/YouTubeDownloader/` sin más pasos.

---

## Stack elegido

- **Lenguaje**: Kotlin
- **UI**: Jetpack Compose
- **Mínimo SDK**: 26 (Android 8.0) — cubre 95%+ de dispositivos actuales
- **Target SDK**: 34 (Android 14)
- **Extracción YouTube**: [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — librería Java oficial de NewPipe, mantenida, sin Python
- **Descargas**: `DownloadManager` de Android + `OkHttp` para casos que no cubra
- **Audio → MP3**: [FFmpegKit](https://github.com/arthenica/ffmpeg-kit) (~20 MB, librería oficial Android)
- **Cookies**: WebView embebido + `CookieManager` nativo
- **Build**: Gradle Kotlin DSL

### Por qué NO:
- **yt-dlp directo**: requiere Python embebido (Chaquopy) + binario — APK de 80 MB+ y actualizaciones pesadas.
- **Kivy/BeeWare**: UI no nativa, share intent limitado, depurar es doloroso.
- **Flutter**: válido, pero para Android-only no compensa la complejidad extra.

---

## Limitaciones de Android vs Desktop

| Aspecto | Desktop | Android | Impacto |
|---|---|---|---|
| Cookies desde navegador instalado | Sí (Chrome, Edge, FF) | **No** (sandboxing) | Usamos WebView propio para login |
| Calidad vídeo | Selector | Fija: "best mp4" disponible | UI más simple |
| Ruta de salida | Cualquiera | `Downloads/YouTubeDownloader/` (Scoped Storage) | OK |
| Actualizar yt-dlp | GitHub binario | NewPipeExtractor se actualiza con la app | Menos automatizable |
| Empaquetar FFmpeg | bootstrap | Incluido en APK vía FFmpegKit | APK ~40 MB |
| Distribución | Instalador .exe | **APK directo** (Play Store prohíbe YT downloaders) | Sideload obligatorio |

---

## Funcionalidades MVP

### Sprint 1: Esqueleto + Share Intent
- [ ] Proyecto Android Studio arranca.
- [ ] Share intent registrado (el botón Compartir de YouTube muestra nuestra app).
- [ ] Al recibir URL, mostrar pantalla con URL detectada.

### Sprint 2: Extracción + Descarga básica
- [ ] Integrar NewPipeExtractor.
- [ ] Detectar si es vídeo único o playlist.
- [ ] Diálogo con opciones: Vídeo / MP3 / Subs / Subs+Vídeo.
- [ ] Descarga a `Downloads/YouTubeDownloader/` con notificación de progreso.
- [ ] Nombres de archivo `{índice} - {Título} [{id}].{ext}` (igual que desktop).

### Sprint 3: Subtítulos
- [ ] Elección ES / EN / ES+EN.
- [ ] Formato TXT (plano) / SRT (tiempos).
- [ ] Conversión SRT→TXT (reusar lógica desktop).

### Sprint 4: Playlist
- [ ] Pantalla con lista de vídeos (thumbnails, título, duración).
- [ ] Checkboxes + "seleccionar todos".
- [ ] Descarga en cola con progreso global.

### Sprint 5: Cookies
- [ ] Pantalla "Iniciar sesión en YouTube" con WebView.
- [ ] Extraer cookies con `CookieManager.getInstance().getCookie(...)`.
- [ ] Guardar cifradas con `EncryptedSharedPreferences`.
- [ ] Pasar cookies a NewPipeExtractor en cada petición.
- [ ] Detección de caducidad → diálogo "Sesión expirada, vuelve a loguearte".

### Sprint 6: Licencias (opcional, futuro)
- [ ] Replicar API de `licensing.py` en Kotlin.
- [ ] Misma función `verifyLicense()` con stub que devuelve "free".

### Sprint 7: Pulido
- [ ] Icono app.
- [ ] Dark mode.
- [ ] Configuración (idioma subs por defecto, formato, etc.).
- [ ] Botón "Borrar descargas anteriores".
- [ ] Auto-update de la app (check a `tradervolume.com/yt-dl-android/version.json`).

---

## Arquitectura de código

```
app/src/main/java/com/tradervolume/ytdl/
├── MainActivity.kt               ← entry point + share intent
├── ui/
│   ├── ShareDialogScreen.kt      ← diálogo tras share (vídeo/mp3/subs)
│   ├── PlaylistScreen.kt         ← selector de playlist
│   ├── SettingsScreen.kt         ← idioma, formato, cookies
│   └── theme/                    ← Material3 theme
├── domain/
│   ├── VideoInfo.kt              ← data class
│   ├── DownloadOption.kt         ← sealed class: Video, Audio, Subs, SubsVideo
│   └── UrlParser.kt              ← detecta vídeo/playlist de una URL
├── data/
│   ├── extractor/
│   │   └── NewPipeRepository.kt  ← wrap NewPipeExtractor
│   ├── downloader/
│   │   ├── DownloadService.kt    ← foreground service + notification
│   │   └── SubtitleConverter.kt  ← SRT→TXT
│   └── cookies/
│       ├── CookieRepository.kt   ← encrypted storage
│       └── LoginActivity.kt      ← WebView login
└── licensing/
    └── LicenseManager.kt         ← stub, futuro
```

---

## Intent filter — así aparecemos en el botón Compartir

En `AndroidManifest.xml`:

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

Filtramos dentro: si el texto recibido es una URL de youtube.com / youtu.be,
procesamos. Si no, mostramos mensaje "Esto no es un enlace de YouTube".

---

## Distribución

### Sin Play Store

1. **Firma única**: generar un keystore (`tradervolume-release.keystore`) y
   guardarlo fuera del repo. Lo mismo para todas las releases futuras.
2. **GitHub Releases**: subir APK firmado cada versión.
3. **Página en tradervolume.com**: botón de descarga + QR + instrucciones
   paso a paso (activar "Orígenes desconocidos").
4. **Auto-update opcional**:
   - `version.json` en tu servidor con `{"latest": "1.2.0", "url": "..."}`.
   - App consulta al arrancar, avisa al usuario si hay actualización,
     descarga APK y dispara `ACTION_VIEW` para que Android lo instale.

### F-Droid (opcional, a futuro)

- Repositorio libre, bien valorado por usuarios técnicos.
- Requiere código open source, build reproducible.
- Tiempo de revisión: semanas-meses.

---

## Tiempo estimado

| Fase | Tiempo (1 dev) |
|---|---|
| Sprints 1-2 (MVP vídeo/MP3) | 1-2 semanas |
| Sprint 3 (subtítulos) | 3-5 días |
| Sprint 4 (playlist) | 3-5 días |
| Sprint 5 (cookies) | 1 semana |
| Sprint 6-7 (licencias, pulido) | 1 semana |
| **Total MVP completo** | **4-6 semanas** |

---

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| NewPipeExtractor rompe con cambios de YouTube | Dependencia regularmente actualizada por su comunidad. Plan B: binario yt-dlp vía Chaquopy. |
| Usuarios no saben activar sideload | Vídeo corto en `tradervolume.com/android` con los 4 clics necesarios. |
| Samsung/Xiaomi/Huawei tienen "protección mejorada" que bloquea APK externos | Documentar el workaround por fabricante en la web. |
| Rate limiting de YouTube | Implementar sleep entre descargas (como en desktop). |
| Cookies no persisten tras reinstalar | Normal, relogin manual. Exponer "Exportar/Importar sesión" como ajuste avanzado. |

---

## Siguientes pasos

1. Abrir la carpeta `android/` en **Android Studio** (Giraffe o superior).
2. Dejar que Gradle sincronice (primera vez tarda ~5 min).
3. Conectar móvil con depuración USB activada y ejecutar en él.
4. Implementar Sprint 1 (share intent).

Ver `README.md` para instrucciones de build paso a paso.
