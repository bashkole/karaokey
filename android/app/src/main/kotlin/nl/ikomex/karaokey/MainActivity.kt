package nl.ikomex.karaokey

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.ikomex.karaokey.ui.KaraokeyViewModel
import nl.ikomex.karaokey.ui.screens.LoginScreen
import nl.ikomex.karaokey.ui.screens.PartyScreen

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: KaraokeyViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KaraokeyApplication
        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return KaraokeyViewModel(
                        tokenStore = app.tokenStore,
                        authManager = app.spotifyAuthManager,
                        queueRepository = app.queueRepository,
                        partySettings = app.partySettings,
                        playbackController = app.playbackController,
                        karaokeyServer = app.karaokeyServer
                    ) as T
                }
            }
        )[KaraokeyViewModel::class.java]

        viewModel.resumeIfLoggedIn()
        handleOAuthIntent(intent)

        setContent {
            val loginState by viewModel.loginState.collectAsState()
            val playbackState by viewModel.playbackState.collectAsState()
            val queue by viewModel.queueState.collectAsState()
            val queueLocked by viewModel.queueLocked.collectAsState()

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (loginState.isLoggedIn) {
                    PartyScreen(
                        playbackState = playbackState,
                        queue = queue,
                        queueLocked = queueLocked,
                        guestUrl = viewModel.guestUrl,
                        onSkip = viewModel::skipCurrent,
                        onTogglePause = viewModel::togglePause,
                        onToggleQueueLock = viewModel::toggleQueueLock
                    )
                } else {
                    LoginScreen(
                        state = loginState,
                        onConnect = viewModel::beginSpotifyLogin
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != "karaokey" || data.host != "callback") return
        val code = data.getQueryParameter("code") ?: return
        val state = data.getQueryParameter("state") ?: return
        lifecycleScope.launch {
            (application as KaraokeyApplication).spotifyAuthManager.completeAuthorization(code, state)
        }
    }
}
