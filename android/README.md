# YouTube Downloader — Android

App Android que replica las funcionalidades del proyecto desktop.

Ver **[PLAN.md](PLAN.md)** para la arquitectura y roadmap completos.

---

## Requisitos de desarrollo

- **Android Studio** Giraffe (2023.3) o superior.
  Descarga: https://developer.android.com/studio
- **JDK 17** (Android Studio lo incluye).
- Un móvil Android o emulador (API 26+).

---

## Primer arranque

1. Abre Android Studio.
2. **File → Open → ** selecciona la carpeta `android/` (esta carpeta).
3. Android Studio descargará Gradle y dependencias (~5-10 min la primera vez).
4. Conecta tu móvil Samsung con depuración USB activada:
   - Ajustes → Acerca del teléfono → Pulsa 7 veces en "Número de compilación".
   - Ajustes → Opciones de desarrollador → **Depuración USB** ON.
5. Pulsa el botón verde ▶ **Run 'app'**.

La app se instala y abre en el móvil.

---

## Probar el share intent

1. Con la app instalada, abre YouTube.
2. En cualquier vídeo, pulsa **Compartir**.
3. En la lista debe aparecer **YouTube Downloader**.
4. Púlsala. La app abre con la URL ya cargada.

---

## Estructura

```
android/
├── app/
│   ├── build.gradle.kts                     ← dependencias del módulo app
│   └── src/main/
│       ├── AndroidManifest.xml              ← permisos + share intent
│       ├── java/com/tradervolume/ytdl/
│       │   └── MainActivity.kt              ← entry point
│       └── res/
│           ├── values/strings.xml
│           └── xml/                         ← configs varios
├── build.gradle.kts                         ← config root
├── settings.gradle.kts                      ← módulos del proyecto
├── gradle.properties
├── PLAN.md                                  ← plan técnico completo
└── README.md                                ← este archivo
```

---

## Generar APK firmado (para distribución)

1. Genera un keystore (solo una vez en tu vida, **guárdalo bien**):

   ```
   keytool -genkey -v -keystore tradervolume-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias tradervolume
   ```

2. Copia el `.jks` a un sitio seguro **fuera del repo**.

3. Crea `~/.gradle/gradle.properties` con:

   ```
   YTDL_KEYSTORE_FILE=C:/rutasegura/tradervolume-release.jks
   YTDL_KEYSTORE_PASSWORD=...
   YTDL_KEY_ALIAS=tradervolume
   YTDL_KEY_PASSWORD=...
   ```

4. En Android Studio: **Build → Generate Signed Bundle / APK → APK →** elige
   el keystore → Build variant **release**.

5. El APK queda en `app/release/app-release.apk`. Súbelo a GitHub Releases o
   a `tradervolume.com/android/`.

---

## Estado actual

Sprint 1 — esqueleto + share intent: **TODO** (archivos generados, pendiente
implementación de la UI).

Ver `PLAN.md` para el roadmap completo.
