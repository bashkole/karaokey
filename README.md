# Karaokey

Fire Stick karaoke companion for Spotify Premium. Guests add any song from their phone browser; the TV shows synced lyrics while Spotify plays audio on the stick.

## Prerequisites

- Amazon Fire Stick on the same Wi-Fi as guest phones
- Spotify Premium account
- Spotify app installed on the Fire Stick
- Android SDK (API 34) for building the APK
- Spotify Developer app (Authorization Code + PKCE; Device Authorization is not available for custom apps)

## Spotify Dashboard setup

1. Create an app at https://developer.spotify.com/dashboard
2. Add redirect URI: `https://karaokey.ikomex.nl/callback`
3. Copy Client ID and Client Secret into `android/local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
SPOTIFY_REDIRECT_URI=https://karaokey.ikomex.nl/callback
```

4. Add your Spotify account under **Users and Access** (Development Mode)

Note: Spotify blocks Device Authorization for standard developer apps. Karaokey uses **Authorization Code + PKCE** with your redirect URI.

## Build

Requires Android SDK API 34. On this server:

```bash
cd android
./gradlew assembleDebug
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk` (~15 MB)

## Web hosting and SSL

The site is served at `https://karaokey.ikomex.nl/` once DNS and SSL are active.

### 1. Add public DNS (required)

`ikomex.nl` uses IONOS nameservers (`ui-dns.*`). Add this record in the IONOS DNS panel:

| Type | Host | Value |
|------|------|-------|
| A | karaokey | 217.154.113.94 |

Verify propagation:

```bash
dig +short karaokey.ikomex.nl @8.8.8.8
```

### 2. Enable SSL

After DNS resolves publicly, run on the server:

```bash
./scripts/enable-karaokey-ssl.sh
```

This requests a Let's Encrypt certificate and switches nginx to HTTPS.

### 3. Download APK on Fire Stick

Open in **Downloader**:

`https://karaokey.ikomex.nl/karaokey.apk`

Or visit the homepage and tap **Download APK for Fire Stick**.

## Install on Fire Stick

1. Enable **Developer Options** and **ADB debugging** on the Fire Stick
2. Connect: `adb connect <fire-stick-ip>:5555`
3. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Party night

1. Open **Spotify** on the Fire Stick once (keeps Connect available)
2. Launch **Karaokey** → **Connect Spotify** → scan the QR code with your phone (same Wi-Fi)
3. Log in to Spotify; after the browser hits `https://karaokey.ikomex.nl/callback`, return to the TV
4. Guests scan the party QR on TV (or open `http://<stick-ip>:8080/`)

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
