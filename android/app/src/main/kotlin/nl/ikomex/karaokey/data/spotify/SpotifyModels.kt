package nl.ikomex.karaokey.data.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceAuthResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("interval") val interval: Int = 5
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null
)

@Serializable
data class SpotifyErrorResponse(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class SearchResponse(
    val tracks: TrackSearchResult? = null
)

@Serializable
data class TrackSearchResult(
    val items: List<SpotifyTrack> = emptyList()
)

@Serializable
data class SpotifyTrack(
    val id: String,
    val name: String,
    val uri: String,
    val duration_ms: Long,
    val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum? = null
)

@Serializable
data class SpotifyArtist(
    val name: String
)

@Serializable
data class SpotifyAlbum(
    val name: String,
    val images: List<SpotifyImage> = emptyList()
)

@Serializable
data class SpotifyImage(
    val url: String,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class DevicesResponse(
    val devices: List<SpotifyDevice> = emptyList()
)

@Serializable
data class SpotifyDevice(
    val id: String? = null,
    val name: String,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("is_restricted") val isRestricted: Boolean = false,
    val type: String? = null
)

@Serializable
data class TransferPlaybackRequest(
    @SerialName("device_ids") val deviceIds: List<String>,
    val play: Boolean = false
)

@Serializable
data class PlayRequest(
    val uris: List<String>? = null,
    @SerialName("position_ms") val positionMs: Long? = null
)

@Serializable
data class CurrentlyPlayingResponse(
    val item: SpotifyTrack? = null,
    @SerialName("progress_ms") val progressMs: Long? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false
)

@Serializable
data class SpotifyUserProfile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val product: String? = null
)
