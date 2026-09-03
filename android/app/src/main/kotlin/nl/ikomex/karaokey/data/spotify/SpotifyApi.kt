package nl.ikomex.karaokey.data.spotify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class SpotifyApi(
    private val tokenStore: TokenStore,
    private val authManager: SpotifyAuthManager,
    private val client: HttpClient = apiHttpClient
) {
    suspend fun getProfile(): SpotifyUserProfile = authorizedGet("/me")

    suspend fun searchTracks(query: String, limit: Int = 20): List<SpotifyTrack> {
        val response: SearchResponse = authorizedGet("/search") {
            parameter("q", query)
            parameter("type", "track")
            parameter("limit", limit)
        }
        return response.tracks?.items.orEmpty()
    }

    suspend fun getDevices(): List<SpotifyDevice> {
        val response: DevicesResponse = authorizedGet("/me/player/devices")
        return response.devices
    }

    suspend fun transferPlayback(deviceId: String, play: Boolean = true) {
        authorizedPut<Unit>("/me/player") {
            setBody(TransferPlaybackRequest(deviceIds = listOf(deviceId), play = play))
        }
        tokenStore.deviceId = deviceId
    }

    suspend fun playTrack(uri: String, deviceId: String? = tokenStore.deviceId) {
        authorizedPut<Unit>("/me/player/play") {
            deviceId?.let { parameter("device_id", it) }
            setBody(PlayRequest(uris = listOf(uri)))
        }
    }

    suspend fun pause(deviceId: String? = tokenStore.deviceId) {
        authorizedPut<Unit>("/me/player/pause") {
            deviceId?.let { parameter("device_id", it) }
        }
    }

    suspend fun resume(deviceId: String? = tokenStore.deviceId) {
        authorizedPut<Unit>("/me/player/play") {
            deviceId?.let { parameter("device_id", it) }
        }
    }

    suspend fun skipToNext(deviceId: String? = tokenStore.deviceId) {
        authorizedPost<Unit>("/me/player/next") {
            deviceId?.let { parameter("device_id", it) }
        }
    }

    suspend fun getCurrentlyPlaying(): CurrentlyPlayingResponse? {
        authManager.ensureValidToken()
        val response = client.get("$API_BASE/me/player/currently-playing") {
            header("Authorization", "Bearer ${tokenStore.accessToken ?: return null}")
        }
        if (response.status == HttpStatusCode.NoContent) {
            return null
        }
        return if (response.status.value in 200..299) response.body() else null
    }

    suspend fun ensureActiveDevice(): SpotifyDevice? {
        val devices = getDevices()
        val savedId = tokenStore.deviceId
        val saved = devices.firstOrNull { it.id == savedId && !it.isRestricted }
        if (saved != null) {
            if (!saved.isActive) {
                transferPlayback(saved.id!!, play = false)
            }
            return saved
        }

        val fireTvDevice = devices.firstOrNull {
            !it.isRestricted &&
                (it.name.contains("Fire", ignoreCase = true) ||
                    it.type.equals("TV", ignoreCase = true) ||
                    it.type.equals("Cast", ignoreCase = true))
        } ?: devices.firstOrNull { !it.isRestricted && it.isActive }
            ?: devices.firstOrNull { !it.isRestricted }

        fireTvDevice?.id?.let { transferPlayback(it, play = false) }
        return fireTvDevice
    }

    private suspend fun authHeader(): String {
        authManager.ensureValidToken()
        return "Bearer ${tokenStore.accessToken ?: error("Not logged in to Spotify")}"
    }

    private suspend inline fun <reified T> authorizedGet(
        path: String,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}
    ): T {
        val response = client.get("$API_BASE$path") {
            header("Authorization", authHeader())
            block()
        }
        if (response.status == HttpStatusCode.NoContent) {
            error("Empty response for $path")
        }
        if (response.status.value !in 200..299) {
            val errorBody = response.bodyAsText()
            error("Spotify API error (${response.status.value}): $errorBody")
        }
        return response.body()
    }

    private suspend inline fun <reified T> authorizedPut(
        path: String,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}
    ): T? {
        val response = client.put("$API_BASE$path") {
            header("Authorization", authHeader())
            contentType(ContentType.Application.Json)
            block()
        }
        return if (response.status == HttpStatusCode.NoContent) null else response.body()
    }

    private suspend inline fun <reified T> authorizedPost(
        path: String,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}
    ): T? {
        val response = client.post("$API_BASE$path") {
            header("Authorization", authHeader())
            block()
        }
        return if (response.status == HttpStatusCode.NoContent) null else response.body()
    }

    companion object {
        private const val API_BASE = "https://api.spotify.com/v1"

        private val json = Json { ignoreUnknownKeys = true }

        private val apiHttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }
}
