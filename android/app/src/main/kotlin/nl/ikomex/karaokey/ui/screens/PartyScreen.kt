package nl.ikomex.karaokey.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text as MaterialText
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import nl.ikomex.karaokey.data.queue.QueueItemEntity
import nl.ikomex.karaokey.playback.PlaybackUiState
import nl.ikomex.karaokey.ui.components.QrCodeImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PartyScreen(
    playbackState: PlaybackUiState,
    queue: List<QueueItemEntity>,
    queueLocked: Boolean,
    guestUrl: String,
    onSkip: () -> Unit,
    onTogglePause: () -> Unit,
    onToggleQueueLock: () -> Unit
) {
    val track = playbackState.currentTrack
    val lyrics = playbackState.lyrics
    val currentLine = lyrics?.lines?.getOrNull(playbackState.currentLineIndex)
    val previousLine = lyrics?.lines?.getOrNull(playbackState.currentLineIndex - 1)
    val nextLine = lyrics?.lines?.getOrNull(playbackState.currentLineIndex + 1)
    val upNext = queue.filter { it.status == "PENDING" }.take(5)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!track?.albumArtUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track?.albumArtUrl,
                            contentDescription = "Album art",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Column {
                        Text(
                            text = track?.trackName ?: "Waiting for songs",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track?.artistName ?: playbackState.statusMessage.orEmpty(),
                            fontSize = 20.sp,
                            color = Color(0xFFBBBBBB),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        playbackState.playbackDeviceName?.let { deviceName ->
                            Text(
                                text = "Audio on $deviceName",
                                fontSize = 14.sp,
                                color = Color(0xFF888888),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Queue: ${queue.count { it.status == "PENDING" || it.status == "PLAYING" }}",
                        fontSize = 18.sp,
                        color = Color(0xFF888888)
                    )
                    if (queueLocked) {
                        Text(
                            text = "Queue locked",
                            fontSize = 14.sp,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    currentLine != null -> {
                        Text(
                            text = previousLine?.text.orEmpty(),
                            fontSize = 24.sp,
                            color = Color(0xFF666666),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = currentLine.text,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1DB954),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = nextLine?.text.orEmpty(),
                            fontSize = 24.sp,
                            color = Color(0xFF666666),
                            textAlign = TextAlign.Center
                        )
                    }

                    !lyrics?.plainText.isNullOrBlank() -> {
                        Text(
                            text = lyrics?.plainText.orEmpty(),
                            fontSize = 28.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }

                    else -> {
                        Text(
                            text = "Lyrics unavailable — enjoy the music!",
                            fontSize = 28.sp,
                            color = Color(0xFF888888),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Column {
                ProgressBar(
                    progressMs = playbackState.progressMs,
                    durationMs = playbackState.durationMs
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    track?.addedBy?.let {
                        Text(
                            text = "Added by $it",
                            fontSize = 16.sp,
                            color = Color(0xFF888888)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onToggleQueueLock) {
                        Text(if (queueLocked) "Unlock queue" else "Lock queue")
                    }
                    Button(onClick = onTogglePause) {
                        Text(if (playbackState.isPlaying) "Pause" else "Resume")
                    }
                    Button(onClick = onSkip) {
                        Text("Skip")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Add songs",
                fontSize = 20.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            QrCodeImage(
                content = guestUrl,
                modifier = Modifier.size(160.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = guestUrl,
                fontSize = 12.sp,
                color = Color(0xFF888888),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Up next",
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (upNext.isEmpty()) {
                Text(
                    text = "No songs queued",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(upNext, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A1A1A))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = item.trackName,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.artistName} · ${item.addedBy}",
                                fontSize = 12.sp,
                                color = Color(0xFF888888),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progressMs: Long, durationMs: Long) {
    val fraction = if (durationMs > 0) {
        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF333333))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .background(Color(0xFF1DB954))
                .align(Alignment.CenterStart)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MaterialText(formatTime(progressMs), fontSize = 14.sp, color = Color(0xFF888888))
        MaterialText(formatTime(durationMs), fontSize = 14.sp, color = Color(0xFF888888))
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
