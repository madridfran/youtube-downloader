package com.tradervolume.ytdl.extractor

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Idempotent NewPipe initialization. Must be called before any StreamInfo/PlaylistInfo request.
 */
object NewPipeInitializer {
    @Volatile private var initialized = false

    fun ensureInitialized(downloader: OkHttpDownloader, localization: Localization = Localization("es", "ES")) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(downloader, localization)
            initialized = true
        }
    }
}
