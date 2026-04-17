package com.tradervolume.ytdl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

/**
 * Muestra la miniatura de un vídeo de YouTube a partir del videoId.
 * Usa la URL pública de YouTube (no requiere login ni API key).
 */
@Composable
fun YtThumb(
    videoId: String?,
    modifier: Modifier = Modifier,
    fallbackLetter: String = "•",
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp
) {
    val ctx = LocalContext.current
    val url = videoId?.takeIf { it.isNotBlank() && it != "unknown" }
        ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                fallbackLetter,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}
