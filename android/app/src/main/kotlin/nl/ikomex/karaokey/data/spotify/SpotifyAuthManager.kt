package nl.ikomex.karaokey.data.spotify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.ikomex.karaokey.BuildConfig
import java.net.URLEncoder
import java.util.UUID

@Serializable
data class OAuthStatePayload(
    val sid: String,
    val host: String
)

data class AuthorizationSession(
    val sessionId: String,
    val authorizeUrl: String,
    val codeVerifier: String
)

class SpotifyAuthManager(
    private val tokenStore: TokenStore,
    private val client: HttpClient = authHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var pendingSession: AuthorizationSession? = null

    private val _authCompleted = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)
    val authCompleted: SharedFlow<Result<Unit>> = _authCompleted.asSharedFlow()

    fun beginAuthorization(stickHost: String): AuthorizationSession {
        val sessionId = UUID.randomUUID().toString()
        val codeVerifier = PkceUtil.generateCodeVerifier()
        val codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier)
        val state = URLEncoder.encode(
            json.encodeToString(
                OAuthStatePayload.serializer(),
                OAuthStatePayload(sessionId, stickHost)
            ),
            Charsets.UTF_8.name()
        )
        val redirectUri = URLEncoder.encode(BuildConfig.SPOTIFY_REDIRECT_URI, Charsets.UTF_8.name())
        val scope = URLEncoder.encode(SCOPES, Charsets.UTF_8.name())
        val authorizeUrl =
            "$ACCOUNTS_BASE/authorize?client_id=${BuildConfig.SPOTIFY_CLIENT_ID}" +
                "&response_type=code&redirect_uri=$redirectUri&scope=$scope" +
                "&code_challenge=$codeChallenge&code_challenge_method=S256&state=$state"

        val session = AuthorizationSession(sessionId, authorizeUrl, codeVerifier)
        pendingSession = session
        return session
    }

    suspend fun completeAuthorization(code: String, stateJson: String) {
        val payload = runCatching {
            json.decodeFromString<OAuthStatePayload>(stateJson)
        }.getOrElse {
            _authCompleted.emit(Result.failure(IllegalArgumentException("Invalid OAuth state")))
            return
        }

        val session = pendingSession
        if (session == null || session.sessionId != payload.sid) {
            _authCompleted.emit(Result.failure(IllegalStateException("No matching login session on this device")))
            return
        }

        try {
            exchangeAuthorizationCode(code, session.codeVerifier)
            pendingSession = null
            _authCompleted.emit(Result.success(Unit))
        } catch (e: Exception) {
            _authCompleted.emit(Result.failure(e))
        }
    }

    suspend fun exchangeAuthorizationCode(code: String, codeVerifier: String): TokenResponse {
        val response = client.post("$ACCOUNTS_BASE/api/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
                        append("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                        append("code_verifier", codeVerifier)
                    }
                )
            )
        }

        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.bodyAsText()
            val spotifyError = runCatching {
                json.decodeFromString<SpotifyErrorResponse>(errorBody)
            }.getOrNull()
            val message = spotifyError?.errorDescription ?: spotifyError?.error ?: errorBody
            error("Spotify login failed: $message")
        }

        val token = response.body<TokenResponse>()
        tokenStore.saveTokens(token.accessToken, token.refreshToken, token.expiresIn)
        return token
    }

    suspend fun refreshAccessToken(): TokenResponse {
        val refreshToken = tokenStore.refreshToken
            ?: error("No refresh token available")
        val response = client.post("$ACCOUNTS_BASE/api/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                        append("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                    }
                )
            )
        }
        val token = response.body<TokenResponse>()
        tokenStore.saveTokens(token.accessToken, token.refreshToken ?: refreshToken, token.expiresIn)
        return token
    }

    suspend fun ensureValidToken() {
        if (!tokenStore.isLoggedIn()) return
        if (System.currentTimeMillis() >= tokenStore.expiresAtMs) {
            refreshAccessToken()
        }
    }

    companion object {
        private const val ACCOUNTS_BASE = "https://accounts.spotify.com"
        const val SCOPES =
            "user-read-playback-state user-modify-playback-state user-read-currently-playing user-read-private"

        val authHttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
