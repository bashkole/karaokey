# Karaokey

Fire Stick karaoke for any Spotify song.

The host connects one Spotify Premium account. Guests on the same Wi-Fi scan a QR code and add tracks from their phone browser — no Spotify login required. The TV shows large synced lyrics while Spotify plays on a computer (a vocal filter and Bluetooth speakers are optional).

This repository is meant to be cloned and built with **your** Spotify Developer app. Karaokey does not ship with working API keys.

## What you need

- Amazon Fire Stick on the same Wi-Fi as guest phones
- A [Spotify Premium](https://www.spotify.com/premium/) account
- A free [Spotify Developer](https://developer.spotify.com/dashboard) app (Client ID and Client Secret)
- The [Spotify desktop app](https://www.spotify.com/download/) open on the computer that will play audio
- Android SDK (API 34) and JDK 17 to build the APK

Guests never need Spotify. Only the host account does.

## 1. Create a Spotify Developer app

1. Open the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) and log in with the Premium account you will use at the party.
2. Click **Create app**.
3. Give it a name and description (for example `Karaokey`).
4. Add a redirect URI. Pick one:

   | Option | Redirect URI | When to use it |
   | --- | --- | --- |
   | Public relay (easiest) | `https://karaokey.ikomex.nl/callback` | You do not want to host anything |
   | Your GitHub Pages site | `https://<your-username>.github.io/karaokey/callback/` | You enabled Pages on this repo (see below) |

   The URI must match **exactly**, including `https` and the trailing slash if you use one.
5. Save the app, then open **Settings** and copy **Client ID** and **Client Secret**.
6. Under **Users and Access**, add the Spotify account that will log in on the TV. New apps start in Development Mode and only listed users can authorize.

Karaokey uses **Authorization Code + PKCE**. Device Authorization is not available for custom Spotify apps.

## 2. Add your credentials

Clone this repository, then copy the example properties file:

```bash
git clone https://github.com/<your-username>/karaokey.git
cd karaokey
cp android/local.properties.example android/local.properties
```

Edit `android/local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
SPOTIFY_REDIRECT_URI=https://karaokey.ikomex.nl/callback
```

- `sdk.dir` is your Android SDK path (Android Studio shows this under Settings → Languages & Frameworks → Android SDK).
- `SPOTIFY_REDIRECT_URI` must be the same URI you registered in the Spotify Dashboard.

These values are compiled into the APK. They are not read from `.env` at runtime. Do not commit `android/local.properties`.

## 3. Build the APK

```bash
cd android
./gradlew assembleDebug
```

The APK is written to:

`android/app/build/outputs/apk/debug/app-debug.apk`

## 4. Install on the Fire Stick

**Option A — Downloader app**

Copy the APK to any HTTPS URL you control, or use a USB stick / shared folder. On the Fire Stick, open **Downloader** and enter that URL.

**Option B — ADB**

1. On the Fire Stick: Settings → My Fire TV → Developer Options → enable **ADB debugging** and **Apps from Unknown Sources**.
2. From your computer:

```bash
adb connect <fire-stick-ip>:5555
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Launch **Karaokey** from Apps.

## 5. Start a party

1. Open **Spotify on the computer** and leave it running. That computer is the audio device.
2. Optional: apply the [vocal filter](audio-filter/README.md) on that computer and pair Bluetooth speakers there.
3. Open **Karaokey** on the Fire Stick → **Connect Spotify**.
4. Scan the QR code with your phone (same Wi-Fi). Log in with the Premium account you added under Users and Access.
5. After the browser shows that Spotify is connected, return to the TV.
6. Guests scan the party QR on the TV, or open `http://<stick-ip>:8765/` in a phone browser.

Fire OS cannot play Spotify audio in the background while Karaokey is on screen. Keep Spotify on the computer and mute the TV if needed.

## GitHub Pages intro and callback

This repo includes a public intro site in `docs/`. After you push to GitHub:

1. Open the repository → **Settings** → **Pages**.
2. Set **Source** to **Deploy from a branch**.
3. Branch: `main` (or `master`), folder: `/docs`.
4. Save. The site will be at `https://<your-username>.github.io/karaokey/`.
5. If you want to use that site as the Spotify redirect, add `https://<your-username>.github.io/karaokey/callback/` in the Spotify Dashboard and in `android/local.properties`, then rebuild the APK.

The callback page only forwards the login code to your Fire Stick on the local network. It does not store tokens.

## Optional vocal filter

Karaokey never sees the Spotify audio stream, so it cannot remove vocals on the TV. To duck centered lead vocals, run a filter on the computer that plays Spotify. See [audio-filter/README.md](audio-filter/README.md).

## Limitations

- Spotify Premium is required for the host account
- Guest phones must be on the same Wi-Fi as the Fire Stick
- Spotify Development Mode allows a limited number of users per app
- No vocal removal inside the TV app
- Lyrics come from [LRCLIB](https://lrclib.net/) and are missing for some tracks

## Architecture

- Android TV app (Kotlin + Jetpack Compose for TV)
- Embedded local web server on port 8765 for guest phones
- Room SQLite for the party queue
- Spotify Web API for search and playback control
- LRCLIB for synced lyrics
