package com.tradervolume.ytdl.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Une un vídeo sin audio (MP4/H.264) y un audio (M4A/AAC) en un MP4, copiando las muestras
 * tal cual (sin recodificar). Las pistas DASH de YouTube son MP4 fragmentado, que
 * MediaExtractor lee sin problema.
 */
object Mp4Muxer {

    fun mux(
        video: File,
        audio: File,
        out: File,
        onProgress: (Int) -> Unit = {},
        checkCancelled: () -> Unit = {}
    ) {
        val ve = MediaExtractor().apply { setDataSource(video.path) }
        val ae = MediaExtractor().apply { setDataSource(audio.path) }
        var muxer: MediaMuxer? = null
        try {
            val vTrack = firstTrack(ve, "video/") ?: error("El fichero de vídeo no tiene pista de vídeo")
            val aTrack = firstTrack(ae, "audio/") ?: error("El fichero de audio no tiene pista de audio")
            ve.selectTrack(vTrack)
            ae.selectTrack(aTrack)
            val vFormat = ve.getTrackFormat(vTrack)
            val aFormat = ae.getTrackFormat(aTrack)
            val durationUs = maxOf(vFormat.longOr(MediaFormat.KEY_DURATION), aFormat.longOr(MediaFormat.KEY_DURATION))

            val m = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
            val vOut = m.addTrack(vFormat)
            val aOut = m.addTrack(aFormat)
            m.start()

            val bufSize = maxOf(4 shl 20, vFormat.intOr(MediaFormat.KEY_MAX_INPUT_SIZE), aFormat.intOr(MediaFormat.KEY_MAX_INPUT_SIZE))
            val buf = ByteBuffer.allocateDirect(bufSize)
            val info = MediaCodec.BufferInfo()
            var vDone = false
            var aDone = false
            var lastPct = -1
            var n = 0
            // Intercala por marca de tiempo para que el MP4 resultante se reproduzca fluido.
            while (!vDone || !aDone) {
                val useVideo = !vDone && (aDone || ve.sampleTime <= ae.sampleTime)
                val ex = if (useVideo) ve else ae
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) {
                    if (useVideo) vDone = true else aDone = true
                    continue
                }
                val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, ex.sampleTime, flags)
                m.writeSampleData(if (useVideo) vOut else aOut, buf, info)
                if (useVideo && durationUs > 0) {
                    val pct = (ex.sampleTime * 100 / durationUs).toInt().coerceIn(0, 100)
                    if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                }
                ex.advance()
                if (++n % 200 == 0) checkCancelled()
            }
            m.stop()
        } finally {
            runCatching { muxer?.release() }
            ve.release()
            ae.release()
        }
    }

    private fun firstTrack(ex: MediaExtractor, mimePrefix: String): Int? =
        (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true
        }

    private fun MediaFormat.longOr(key: String) = if (containsKey(key)) getLong(key) else 0L
    private fun MediaFormat.intOr(key: String) = if (containsKey(key)) getInteger(key) else 0
}
