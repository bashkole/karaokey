package nl.ikomex.karaokey.data.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LrcLibTrack(
    val id: Long? = null,
    @SerialName("trackName") val trackName: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null,
    @SerialName("plainLyrics") val plainLyrics: String? = null
)

class LyricsRepository(
    private val client: HttpClient = lyricsHttpClient
) {
    suspend fun getLyrics(trackName: String, artistName: String): LyricsContent {
        return try {
            val results: List<LrcLibTrack> = client.get("$BASE/search") {
                parameter("track_name", trackName)
                parameter("artist_name", artistName)
            }.body()

            val match = results.firstOrNull()
                ?: return LyricsContent(lines = emptyList(), plainText = null, synced = false)

            val synced = match.syncedLyrics
            if (!synced.isNullOrBlank()) {
                LrcParser.parse(synced)
            } else if (!match.plainLyrics.isNullOrBlank()) {
                LyricsContent(
                    lines = emptyList(),
                    plainText = match.plainLyrics,
                    synced = false
                )
            } else {
                LyricsContent(lines = emptyList(), plainText = null, synced = false)
            }
        } catch (_: Exception) {
            LyricsContent(lines = emptyList(), plainText = null, synced = false)
        }
    }

    companion object {
        private const val BASE = "https://lrclib.net/api"

        private val json = Json { ignoreUnknownKeys = true }

        private val lyricsHttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }
}
