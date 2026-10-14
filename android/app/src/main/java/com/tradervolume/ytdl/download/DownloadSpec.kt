package com.tradervolume.ytdl.download

import androidx.work.Data
import androidx.work.workDataOf

/**
 * Calidad de vídeo. YouTube solo da vídeo+audio juntos en 360p; 720p y 1080p llegan como
 * vídeo H.264 y audio AAC separados y se unen en el móvil con MediaMuxer (sin recodificar).
 */
enum class VideoQuality(val label: String, val maxHeight: Int) {
    P360("360p", 360), P720("720p", 720), P1080("1080p", 1080);

    val needsMux get() = maxHeight > 360
}

/** M4A = AAC (compatible con todo); OPUS = WebM/Opus (algo más de calidad, menos compatible). */
enum class AudioFormat(val label: String) { M4A("M4A"), OPUS("Opus") }

/** Qué partes bajar de un vídeo. En playlist cada fila puede sobrescribir las globales. */
data class Parts(val video: Boolean, val audio: Boolean, val subs: Boolean) {
    val any get() = video || audio || subs
    fun label() = listOfNotNull("V".takeIf { video }, "A".takeIf { audio }, "S".takeIf { subs })
        .joinToString("+").ifEmpty { "—" }
}

data class DownloadSpec(
    val video: Boolean = true,
    val audio: Boolean = false,
    val subs: Boolean = false,
    val quality: VideoQuality = VideoQuality.P720,
    val audioFormat: AudioFormat = AudioFormat.M4A,
    val subLangs: Set<String> = setOf("es"),
    val subsFormat: SubsFormat = SubsFormat.SRT
) {
    val parts get() = Parts(video, audio, subs)

    fun withParts(p: Parts) = copy(video = p.video, audio = p.audio, subs = p.subs)

    /** Hay algo que bajar y, si se piden subtítulos, al menos un idioma. */
    val isValid get() = parts.any && (!subs || subLangs.isNotEmpty())

    /** Resumen legible: "Vídeo 720p + subtítulos ES (SRT)". */
    fun summary(): String = listOfNotNull(
        "vídeo ${quality.label}".takeIf { video },
        "audio ${audioFormat.label}".takeIf { audio },
        "subtítulos ${subLangs.sortedBy { SubsLang.isOriginal(it) }.joinToString("+") { SubsLang.label(it) }} (${subsFormat.label})"
            .takeIf { subs && subLangs.isNotEmpty() }
    ).joinToString(" + ").replaceFirstChar { it.uppercase() }.ifEmpty { "Nada seleccionado" }

    fun toData(url: String, index: Int?, title: String?): Data = workDataOf(
        KEY_URL to url,
        KEY_INDEX to (index ?: -1),
        KEY_TITLE to title,
        KEY_VIDEO to video,
        KEY_AUDIO to audio,
        KEY_SUBS to subs,
        KEY_QUALITY to quality.name,
        KEY_AUDIO_FORMAT to audioFormat.name,
        KEY_SUB_LANGS to subLangs.joinToString(","),
        KEY_SUBS_FORMAT to subsFormat.name
    )

    companion object {
        const val KEY_URL = "url"
        const val KEY_INDEX = "index"
        const val KEY_TITLE = "title"
        const val KEY_VIDEO = "video"
        const val KEY_AUDIO = "audio"
        const val KEY_SUBS = "subs"
        const val KEY_QUALITY = "quality"
        const val KEY_AUDIO_FORMAT = "audio_format"
        const val KEY_SUB_LANGS = "sub_langs"
        const val KEY_SUBS_FORMAT = "subs_format"

        fun fromData(d: Data) = DownloadSpec(
            video = d.getBoolean(KEY_VIDEO, false),
            audio = d.getBoolean(KEY_AUDIO, false),
            subs = d.getBoolean(KEY_SUBS, false),
            quality = enumOr(d.getString(KEY_QUALITY), VideoQuality.P720),
            audioFormat = enumOr(d.getString(KEY_AUDIO_FORMAT), AudioFormat.M4A),
            subLangs = d.getString(KEY_SUB_LANGS).orEmpty()
                .split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet(),
            subsFormat = enumOr(d.getString(KEY_SUBS_FORMAT), SubsFormat.SRT)
        )

        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            runCatching { enumValueOf<E>(name!!) }.getOrDefault(default)
    }
}
