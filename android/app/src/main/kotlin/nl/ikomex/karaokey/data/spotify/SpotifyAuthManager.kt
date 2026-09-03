package nl.ikomex.karaokey.data.spotify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import nl.ikomex.karaokey.BuildConfig

class SpotifyAuthManager(
    private val tokenStore: TokenStore,
    private val client: HttpClient = authHttpClient
) {
    suspend fun requestDeviceCode(): DeviceAuthResponse {
        val response = client.post("$ACCOUNTS_BASE/api/oauth2/device/authorize") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                        append("scope", SCOPES)
                    }
                )
            )
        }
        return response.body()
    }

    suspend fun pollDeviceAuthorization(
        deviceCode: String,
        intervalSeconds: Int
    ): TokenResponse {
        var waitSeconds = intervalSeconds.coerceAtLeast(5)
        while (true) {
            delay(waitSeconds * 1000L)
            val response = client.post("$ACCOUNTS_BASE/api/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                            append("device_code", deviceCode)
                            append("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                        }
                    )
                )
            }

            if (response.status == HttpStatusCode.OK) {
                val token = response.body<TokenResponse>()
                tokenStore.saveTokens(token.accessToken, token.refreshToken, token.expiresIn)
                return token
            }

            val errorBody = response.bodyAsText()
            when {
                errorBody.contains("authorization_pending") -> Unit
                errorBody.contains("slow_down") -> waitSeconds += 5
                errorBody.contains("expired_token") -> error("Spotify login expired. Try again.")
                errorBody.contains("access_denied") -> error("Spotify login denied.")
                else -> error("Spotify login failed: $errorBody")
            }
        }
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

        private val json = Json { ignoreUnknownKeys = true }

        val authHttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }
}
