package nl.ikomex.karaokey

import android.app.Application
import nl.ikomex.karaokey.data.lyrics.LyricsRepository
import nl.ikomex.karaokey.data.queue.AppDatabase
import nl.ikomex.karaokey.data.queue.QueueRepository
import nl.ikomex.karaokey.data.spotify.SpotifyApi
import nl.ikomex.karaokey.data.spotify.SpotifyAuthManager
import nl.ikomex.karaokey.data.spotify.TokenStore
import nl.ikomex.karaokey.data.session.PartySettings
import nl.ikomex.karaokey.playback.PlaybackController
import nl.ikomex.karaokey.server.KaraokeyServer

class KaraokeyApplication : Application() {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var spotifyAuthManager: SpotifyAuthManager
        private set
    lateinit var spotifyApi: SpotifyApi
        private set
    lateinit var queueRepository: QueueRepository
        private set
    lateinit var lyricsRepository: LyricsRepository
        private set
    lateinit var partySettings: PartySettings
        private set
    lateinit var playbackController: PlaybackController
        private set
    lateinit var karaokeyServer: KaraokeyServer
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        spotifyAuthManager = SpotifyAuthManager(tokenStore)
        spotifyApi = SpotifyApi(tokenStore, spotifyAuthManager)
        val database = AppDatabase.create(this)
        queueRepository = QueueRepository(database.queueDao())
        partySettings = PartySettings()
        lyricsRepository = LyricsRepository()
        playbackController = PlaybackController(
            spotifyApi = spotifyApi,
            queueRepository = queueRepository,
            lyricsRepository = lyricsRepository
        )
        karaokeyServer = KaraokeyServer(
            port = BuildConfig.GUEST_SERVER_PORT,
            spotifyApi = spotifyApi,
            queueRepository = queueRepository,
            partySettings = partySettings,
            playbackController = playbackController,
            assets = assets
        )
    }
}
