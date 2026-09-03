package nl.ikomex.karaokey.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.ikomex.karaokey.BuildConfig
import nl.ikomex.karaokey.data.session.PartySettings
import nl.ikomex.karaokey.data.spotify.SpotifyAuthManager
import nl.ikomex.karaokey.data.spotify.TokenStore
import nl.ikomex.karaokey.data.queue.QueueRepository
import nl.ikomex.karaokey.playback.PlaybackController
import nl.ikomex.karaokey.server.KaraokeyServer
import nl.ikomex.karaokey.util.NetworkUtils

data class LoginUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val authorizeUrl: String? = null,
    val error: String? = null
)

class KaraokeyViewModel(
    application: Application,
    private val tokenStore: TokenStore,
    private val authManager: SpotifyAuthManager,
    private val queueRepository: QueueRepository,
    private val partySettings: PartySettings,
    private val playbackController: PlaybackController,
    private val karaokeyServer: KaraokeyServer
) : AndroidViewModel(application) {

    private val _loginState = MutableStateFlow(LoginUiState(isLoggedIn = tokenStore.isLoggedIn()))
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _listeningPort = MutableStateFlow(BuildConfig.GUEST_SERVER_PORT)

    val guestUrl: StateFlow<String> = _listeningPort
        .map { port -> NetworkUtils.guestUrl(port, getApplication()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkUtils.guestUrl(BuildConfig.GUEST_SERVER_PORT, application))

    val playbackState = playbackController.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        playbackController.state.value
    )

    val queueState = queueRepository.observeActiveQueue().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val queueLocked = partySettings.queueLocked.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    init {
        viewModelScope.launch {
            authManager.authCompleted.collect { result ->
                result.onSuccess {
                    _loginState.value = LoginUiState(isLoggedIn = true)
                    startPartyServices()
                }.onFailure { error ->
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Login failed"
                    )
                }
            }
        }
    }

    fun startPartyServices() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val port = karaokeyServer.startServer()
                    _listeningPort.value = port
                    playbackController.start()
                }
            } catch (e: Exception) {
                _loginState.value = _loginState.value.copy(
                    error = e.message ?: "Could not start guest server"
                )
            }
        }
    }

    fun beginSpotifyLogin() {
        viewModelScope.launch {
            _loginState.value = _loginState.value.copy(isLoading = true, error = null, authorizeUrl = null)
            try {
                val port = withContext(Dispatchers.IO) {
                    karaokeyServer.startServer()
                }
                _listeningPort.value = port
                val stickHost = NetworkUtils.stickHostAddress(port, getApplication())
                val session = authManager.beginAuthorization(stickHost)
                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    authorizeUrl = session.authorizeUrl
                )
            } catch (e: Exception) {
                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Could not start Spotify login"
                )
            }
        }
    }

    fun resumeIfLoggedIn() {
        if (tokenStore.isLoggedIn()) {
            _loginState.value = LoginUiState(isLoggedIn = true)
            startPartyServices()
        }
    }

    fun skipCurrent() {
        viewModelScope.launch {
            playbackController.skipCurrent()
        }
    }

    fun togglePause() {
        viewModelScope.launch {
            playbackController.togglePause()
        }
    }

    fun toggleQueueLock() {
        partySettings.toggleQueueLock()
    }

    fun logout() {
        tokenStore.clear()
        playbackController.stop()
        viewModelScope.launch(Dispatchers.IO) {
            karaokeyServer.stopServer()
        }
        _loginState.value = LoginUiState(isLoggedIn = false)
    }
}
