package nl.ikomex.karaokey.server

import android.content.res.AssetManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.ikomex.karaokey.data.queue.QueueRepository
import nl.ikomex.karaokey.data.session.PartySettings
import nl.ikomex.karaokey.data.spotify.SpotifyApi
import nl.ikomex.karaokey.data.spotify.SpotifyAuthManager
import nl.ikomex.karaokey.playback.PlaybackController
import java.io.IOException
import java.util.Collections

@Serializable
data class AddQueueRequest(
    val spotifyUri: String,
    val trackName: String,
    val artistName: String,
    val albumArtUrl: String? = null,
    val durationMs: Long = 0,
    val addedBy: String = "Guest"
)

@Serializable
data class QueueItemDto(
    val id: Long,
    val spotifyUri: String,
    val trackName: String,
    val artistName: String,
    val addedBy: String,
    val status: String
)

@Serializable
data class SearchResultDto(
    val uri: String,
    val name: String,
    val artist: String,
    val albumArtUrl: String?,
    val durationMs: Long
)

@Serializable
data class PartyStatusDto(
    val queueLocked: Boolean,
    val queueSize: Int
)

@Serializable
data class OAuthCompleteRequest(
    val code: String,
    val state: String
)

class KaraokeyServer(
    private val preferredPort: Int,
    private val spotifyApi: SpotifyApi,
    private val spotifyAuthManager: SpotifyAuthManager,
    private val queueRepository: QueueRepository,
    private val partySettings: PartySettings,
    private val playbackController: PlaybackController,
    private val assets: AssetManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeServer: GuestHttpServer? = null

    var listeningPort: Int = preferredPort
        private set

    init {
        scope.launch {
            queueRepository.observeActiveQueue().collectLatest {
                activeServer?.broadcastQueue()
                activeServer?.broadcastStatus()
            }
        }
        scope.launch {
            partySettings.queueLocked.collectLatest {
                activeServer?.broadcastStatus()
            }
        }
    }

    fun startServer(): Int {
        synchronized(this) {
            activeServer?.let { server ->
                if (server.isAlive) {
                    return server.listeningPort
                }
                runCatching { server.stop() }
                activeServer = null
            }

            val portsToTry = listOf(preferredPort, 8765, 8081, 8888, 9090).distinct()
            var lastError: IOException? = null

            for (port in portsToTry) {
                val server = GuestHttpServer(port)
                try {
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    activeServer = server
                    listeningPort = server.listeningPort
                    return listeningPort
                } catch (error: IOException) {
                    lastError = error
                    runCatching { server.stop() }
                }
            }

            throw IllegalStateException(
                "Failed to start guest server on ports ${portsToTry.joinToString()}: ${lastError?.message ?: "unknown error"}",
                lastError
            )
        }
    }

    fun stopServer() {
        synchronized(this) {
            activeServer?.stop()
            activeServer = null
        }
    }

    private inner class GuestHttpServer(port: Int) : NanoWSD(port) {
        private val json = Json { ignoreUnknownKeys = true }
        private val searchLimiter = RateLimiter(maxRequests = 10, windowMs = 60_000)
        private val webSockets = Collections.synchronizedSet(mutableSetOf<WebSocket>())

        override fun serve(session: IHTTPSession): Response {
            return try {
                handleRequest(session)
            } catch (e: Exception) {
                jsonResponse(500, """{"error":${json.encodeToString(e.message ?: "error")}}""")
            }
        }

        private fun handleRequest(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            if (uri == "/ws") {
                return super.serve(session)
            }

            if (uri == "/" || uri.isBlank()) {
                return assetResponse("guest/index.html", "text/html")
            }
            if (uri == "/style.css") {
                return assetResponse("guest/style.css", "text/css")
            }
            if (uri == "/app.js") {
                return assetResponse("guest/app.js", "application/javascript")
            }

            if (uri == "/api/status" && method == Method.GET) {
                return jsonResponse(json.encodeToString(PartyStatusDto.serializer(), buildStatus()))
            }

            if (uri == "/api/queue" && method == Method.GET) {
                return jsonResponse(json.encodeToString(ListSerializer(QueueItemDto.serializer()), buildQueueDto()))
            }

            if (uri == "/api/oauth/complete" && method == Method.POST) {
                val body = readBody(session)
                val request = json.decodeFromString<OAuthCompleteRequest>(body)
                scope.launch {
                    spotifyAuthManager.completeAuthorization(request.code, request.state)
                }
                return corsResponse(jsonResponse("""{"status":"ok"}"""))
            }

            if (uri == "/api/oauth/complete" && method == Method.OPTIONS) {
                return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""))
            }

            if (uri.startsWith("/api/search") && method == Method.GET) {
                val query = HttpRequestUtils.queryParam(session, "q")
                if (query.length < 2) {
                    return jsonResponse(json.encodeToString(ListSerializer(SearchResultDto.serializer()), emptyList()))
                }
                val clientIp = session.headers["remote-addr"] ?: session.remoteIpAddress ?: "unknown"
                if (!searchLimiter.allow(clientIp)) {
                    return jsonResponse(429, """{"error":"Rate limit exceeded"}""")
                }
                return try {
                    val tracks = runBlocking { spotifyApi.searchTracks(query) }
                    val results = tracks.map {
                        SearchResultDto(
                            uri = it.uri,
                            name = it.name,
                            artist = it.artists.joinToString { artist -> artist.name },
                            albumArtUrl = it.album?.images?.firstOrNull()?.url,
                            durationMs = it.duration_ms
                        )
                    }
                    jsonResponse(json.encodeToString(ListSerializer(SearchResultDto.serializer()), results))
                } catch (e: Exception) {
                    jsonResponse(502, """{"error":${json.encodeToString(e.message ?: "Spotify search failed")}}""")
                }
            }

            if (uri == "/api/queue" && method == Method.POST) {
                if (partySettings.queueLocked.value) {
                    return jsonResponse(403, """{"error":"Queue is locked by the host"}""")
                }
                return try {
                    val body = readBody(session)
                    val request = json.decodeFromString<AddQueueRequest>(body)
                    require(request.spotifyUri.startsWith("spotify:track:")) { "Invalid track URI" }
                    val item = runBlocking {
                        queueRepository.addTrack(
                            spotifyUri = request.spotifyUri,
                            trackName = request.trackName,
                            artistName = request.artistName,
                            albumArtUrl = request.albumArtUrl,
                            durationMs = request.durationMs,
                            addedBy = request.addedBy
                        ).also {
                            playbackController.onTrackAdded()
                        }
                    }
                    jsonResponse(
                        json.encodeToString(
                            QueueItemDto.serializer(),
                            QueueItemDto(
                                id = item.id,
                                spotifyUri = item.spotifyUri,
                                trackName = item.trackName,
                                artistName = item.artistName,
                                addedBy = item.addedBy,
                                status = item.status
                            )
                        )
                    )
                } catch (e: Exception) {
                    jsonResponse(500, """{"error":${json.encodeToString(e.message ?: "Could not add song")}}""")
                }
            }

            if (uri.startsWith("/api/queue/") && method == Method.DELETE) {
                val id = uri.removePrefix("/api/queue/").toLongOrNull()
                    ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad id")
                runBlocking { queueRepository.remove(id) }
                return newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, "")
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }

        override fun openWebSocket(handshake: IHTTPSession): WebSocket {
            return object : WebSocket(handshake) {
                override fun onOpen() {
                    webSockets += this
                    send(buildQueueMessage())
                    send(buildStatusMessage())
                }

                override fun onClose(
                    code: WebSocketFrame.CloseCode?,
                    reason: String?,
                    initiatedByRemote: Boolean
                ) {
                    webSockets -= this
                }

                override fun onMessage(message: WebSocketFrame?) = Unit

                override fun onPong(pong: WebSocketFrame?) = Unit

                override fun onException(exception: IOException?) = Unit
            }
        }

        fun broadcastQueue() {
            val message = buildQueueMessage()
            webSockets.toList().forEach { socket ->
                runCatching { socket.send(message) }
            }
        }

        fun broadcastStatus() {
            val message = buildStatusMessage()
            webSockets.toList().forEach { socket ->
                runCatching { socket.send(message) }
            }
        }

        private fun buildQueueDto(): List<QueueItemDto> =
            runBlocking {
                queueRepository.getAll().map {
                    QueueItemDto(
                        id = it.id,
                        spotifyUri = it.spotifyUri,
                        trackName = it.trackName,
                        artistName = it.artistName,
                        addedBy = it.addedBy,
                        status = it.status
                    )
                }
            }

        private fun buildStatus(): PartyStatusDto {
            val queue = buildQueueDto()
            return PartyStatusDto(
                queueLocked = partySettings.queueLocked.value,
                queueSize = queue.count { it.status == "PENDING" || it.status == "PLAYING" }
            )
        }

        private fun buildQueueMessage(): String {
            val payload = json.encodeToString(ListSerializer(QueueItemDto.serializer()), buildQueueDto())
            return """{"type":"queue","data":$payload}"""
        }

        private fun buildStatusMessage(): String {
            val payload = json.encodeToString(PartyStatusDto.serializer(), buildStatus())
            return """{"type":"status","data":$payload}"""
        }

        private fun assetResponse(path: String, mime: String): Response {
            val text = assets.open(path).bufferedReader().use { it.readText() }
            return newFixedLengthResponse(Response.Status.OK, mime, text)
        }

        private fun jsonResponse(body: String): Response =
            newFixedLengthResponse(Response.Status.OK, "application/json", body)

        private fun jsonResponse(statusCode: Int, body: String): Response {
            val status = when (statusCode) {
                403 -> Response.Status.FORBIDDEN
                429 -> Response.Status.TOO_MANY_REQUESTS
                else -> Response.Status.INTERNAL_ERROR
            }
            return newFixedLengthResponse(status, "application/json", body)
        }

        private fun corsResponse(response: Response): Response {
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Access-Control-Allow-Methods", "POST, OPTIONS")
            response.addHeader("Access-Control-Allow-Headers", "Content-Type")
            return response
        }

        private fun readBody(session: IHTTPSession): String {
            val files = java.util.concurrent.ConcurrentHashMap<String, String>()
            session.parseBody(files)
            return files["postData"].orEmpty()
        }
    }
}
