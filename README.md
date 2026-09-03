# Karaokey

Fire Stick karaoke companion for Spotify Premium. Guests add any song from their phone browser; the TV shows synced lyrics while Spotify plays audio on the stick.

## Prerequisites

- Amazon Fire Stick on the same Wi-Fi as guest phones
- Spotify Premium account
- Spotify app installed on the Fire Stick
- Android SDK (API 34) for building the APK
- Spotify Developer app with Device Authorization enabled

## Spotify Dashboard setup

1. Create an app at https://developer.spotify.com/dashboard
2. Add redirect URI: `http://karokey.ikomex.nl/callback` (optional for future phone login)
3. Copy Client ID and Client Secret into `android/local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
SPOTIFY_REDIRECT_URI=http://karokey.ikomex.nl/callback
```

4. Keep the app in Development Mode for personal sideload use

## Build

Requires Android SDK API 34. On this server:

```bash
cd android
./gradlew assembleDebug
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk` (~15 MB)

## Install on Fire Stick

1. Enable **Developer Options** and **ADB debugging** on the Fire Stick
2. Connect: `adb connect <fire-stick-ip>:5555`
3. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Party night

1. Open **Spotify** on the Fire Stick once (keeps Connect available)
2. Launch **Karaokey** and connect Spotify using the on-screen code
3. Guests scan the QR code on TV (or open the shown `http://<stick-ip>:8080/` URL)
4. Guests search and add songs; lyrics appear on TV when each song plays

## Architecture

- Android TV app (Kotlin + Compose for TV)
- Embedded Ktor server on port 8080 for guest phones
- Room SQLite for party queue
- Spotify Web API for search and playback control
- LRCLIB for synced lyrics

## Limitations

- No vocal removal (Spotify does not expose raw audio to third-party apps)
- Host Premium account required
- Guest phones must be on the same local network as the Fire Stick
