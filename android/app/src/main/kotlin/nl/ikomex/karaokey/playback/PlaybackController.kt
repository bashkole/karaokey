package nl.ikomex.karaokey.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.ikomex.karaokey.data.lyrics.LrcParser
import nl.ikomex.karaokey.data.lyrics.LyricsContent
import nl.ikomex.karaokey.data.lyrics.LyricsRepository
import nl.ikomex.karaokey.data.queue.QueueItemEntity
import nl.ikomex.karaokey.data.queue.QueueRepository
import nl.ikomex.karaokey.data.spotify.SpotifyApi

data class PlaybackUiState(
    val currentTrack: QueueItemEntity? = null,
    val lyrics: LyricsContent? = null,
    val currentLineIndex: Int = -1,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val statusMessage: String? = null
)

class PlaybackController(
    private val spotifyApi: SpotifyApi,
    private val queueRepository: QueueRepository,
    private val lyricsRepository: LyricsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var advanceJob: Job? = null
    private var lastProgressMs: Long = 0
    private var stalledPolls: Int = 0

    fun start() {
        if (advanceJob?.isActive == true) return
        advanceJob = scope.launch {
            spotifyApi.ensureActiveDevice()
            while (isActive) {
                try {
                    val playing = queueRepository.getCurrentlyPlaying()
                    if (playing == null) {
                        val next = queueRepository.getNextPending()
                        if (next != null) {
                            playQueueItem(next)
                        } else {
                            _state.value = _state.value.copy(
                                currentTrack = null,
                                lyrics = null,
                                statusMessage = "Scan the QR code to add songs"
                            )
                        }
                    }
                } catch (e: Exception) {
                    _state.value = _state.value.copy(statusMessage = e.message)
                }
                delay(2000)
            }
        }
        startPolling()
    }

    fun stop() {
        pollJob?.cancel()
        advanceJob?.cancel()
    }

    suspend fun onTrackAdded() {
        if (queueRepository.getCurrentlyPlaying() == null) {
            queueRepository.getNextPending()?.let { playQueueItem(it) }
        }
    }

    suspend fun skipCurrent() {
        val current = queueRepository.getCurrentlyPlaying() ?: return
        queueRepository.markSkipped(current.id)
        queueRepository.getNextPending()?.let { playQueueItem(it) } ?: run {
            _state.value = PlaybackUiState(statusMessage = "Queue empty — add more songs!")
        }
    }

    suspend fun togglePause() {
        val playing = _state.value.isPlaying
        if (playing) {
            spotifyApi.pause()
        } else {
            spotifyApi.resume()
        }
        _state.value = _state.value.copy(isPlaying = !playing)
    }

    private suspend fun playQueueItem(item: QueueItemEntity) {
        queueRepository.markPlaying(item)
        spotifyApi.ensureActiveDevice()
        spotifyApi.playTrack(item.spotifyUri)
        val lyrics = lyricsRepository.getLyrics(item.trackName, item.artistName)
        lastProgressMs = 0
        stalledPolls = 0
        _state.value = PlaybackUiState(
            currentTrack = item,
            lyrics = lyrics,
            durationMs = item.durationMs,
            isPlaying = true,
            statusMessage = null
        )
    }

    private suspend fun advanceToNext(currentItem: QueueItemEntity) {
        queueRepository.markPlayed(currentItem.id)
        queueRepository.getNextPending()?.let { playQueueItem(it) } ?: run {
            _state.value = _state.value.copy(
                currentTrack = null,
                lyrics = null,
                isPlaying = false,
                progressMs = 0,
                statusMessage = "Scan the QR code to add songs"
            )
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                try {
                    val currentItem = queueRepository.getCurrentlyPlaying()
                    if (currentItem != null) {
                        val spotifyState = spotifyApi.getCurrentlyPlaying()
                        val spotifyUri = spotifyState?.item?.uri
                        val progress = spotifyState?.progressMs ?: _state.value.progressMs
                        val isPlaying = spotifyState?.isPlaying ?: _state.value.isPlaying
                        val durationMs = currentItem.durationMs.takeIf { it > 0 }
                            ?: _state.value.durationMs

                        if (progress == lastProgressMs && isPlaying) {
                            stalledPolls++
                        } else {
                            stalledPolls = 0
                            lastProgressMs = progress
                        }

                        val lyrics = _state.value.lyrics
                        val lineIndex = if (lyrics?.synced == true) {
                            LrcParser.lineIndexAt(lyrics.lines, progress)
                        } else {
                            -1
                        }

                        _state.value = _state.value.copy(
                            progressMs = progress,
                            durationMs = durationMs,
                            isPlaying = isPlaying,
                            currentLineIndex = lineIndex
                        )

                        val trackChanged = spotifyUri != null &&
                            spotifyUri != currentItem.spotifyUri &&
                            progress < 5000

                        val reachedEnd = durationMs > 0 &&
                            progress >= durationMs - 1500

                        val playbackStoppedAtEnd = !isPlaying &&
                            durationMs > 0 &&
                            progress >= durationMs - 5000

                        val noPlaybackInfo = spotifyState == null && stalledPolls >= 3

                        if (trackChanged || reachedEnd || playbackStoppedAtEnd || noPlaybackInfo) {
                            advanceToNext(currentItem)
                        }
                    }
                } catch (_: Exception) {
                    // Keep polling through transient API errors.
                }
                delay(1000)
            }
        }
    }
}
