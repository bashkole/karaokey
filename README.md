# Karaokey

Your living room. The entire Spotify catalog. Friends yelling the wrong lyrics on purpose.

Karaokey turns a Fire Stick into a karaoke night. You connect one Spotify Premium account. Everyone else just scans a QR code and throws songs on the queue from their phone — no app, no login, no “wait, whose playlist is this?” The TV prints the lyrics big enough to see from the couch. Audio plays from Spotify on a computer. Optional vocal filter if you want the singer to have a fighting chance.

**Grab it. Build it. Ruin a ballad tonight.**

Clone this repo, drop in your own Spotify Developer keys, sideload the APK, and try a song. That is the whole product. If something is awkward, that is useful — open an issue or fix it and send a pull request.

This project does not ship with working API keys. You bring a free Spotify Developer app and a Premium account. Five minutes of dashboard clicking, then you are in.

## Try it this weekend

1. Clone the repo
2. Create a Spotify Developer app and paste the keys
3. Build the APK and put it on your Fire Stick
4. Play one song you know too well
5. Hand a phone to someone who will immediately queue something unhinged

```bash
git clone https://github.com/<your-username>/karaokey.git
cd karaokey
cp android/local.properties.example android/local.properties
```

Edit `android/local.properties`, then:

```bash
cd android
./gradlew assembleDebug
```

APK lands at `android/app/build/outputs/apk/debug/app-debug.apk`. Sideload it (steps below), open Karaokey, hit **Connect Spotify**, and scan the QR code.

## What you need

- A Fire Stick on the same Wi-Fi as the phones
- [Spotify Premium](https://www.spotify.com/premium/) for the host
- A free [Spotify Developer](https://developer.spotify.com/dashboard) app (Client ID + Client Secret)
- The [Spotify desktop app](https://www.spotify.com/download/) open on the computer that will play sound
- Android SDK (API 34) and JDK 17 to build

Guests need a browser and the Wi-Fi password. That is it.

## 1. Make a Spotify Developer app

This is the boring bit. Do it once.

1. Open the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) and log in with the Premium account you will use at the party.
2. Click **Create app**. Name it Karaokey, or something you will not be embarrassed by later.
3. Add a redirect URI. Pick one:

   | Option | Redirect URI | When to use it |
   | --- | --- | --- |
   | Public relay (easiest) | `https://karaokey.ikomex.nl/callback` | You do not want to host anything |
   | Your GitHub Pages site | `https://<your-username>.github.io/karaokey/callback/` | You enabled Pages on this repo (see below) |

   Copy it exactly. Spotify will reject a missing slash with zero charm.
4. Save, open **Settings**, copy **Client ID** and **Client Secret**.
5. Under **Users and Access**, add the Spotify account that will log in on the TV. New apps start in Development Mode — only listed people can authorize.

Karaokey uses **Authorization Code + PKCE**. Device Authorization is not available for custom Spotify apps.

## 2. Drop in your keys

```properties
sdk.dir=/path/to/Android/Sdk
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
SPOTIFY_REDIRECT_URI=https://karaokey.ikomex.nl/callback
```

- `sdk.dir` is your Android SDK path (Android Studio: Settings → Languages & Frameworks → Android SDK).
- `SPOTIFY_REDIRECT_URI` must match the Dashboard URI.

These values are baked into the APK at build time. Do not commit `android/local.properties`. Your future self will thank you.

## 3. Build the APK

```bash
cd android
./gradlew assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`

## 4. Get it onto the Fire Stick

**Downloader app:** put the APK on any HTTPS URL you control (or a USB stick). On the Stick, open **Downloader** and paste the URL.

**ADB:**

1. Fire Stick: Settings → My Fire TV → Developer Options → enable **ADB debugging** and **Apps from Unknown Sources**.
2. From your computer:

```bash
adb connect <fire-stick-ip>:5555
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Find **Karaokey** in Apps and launch it.

## 5. Start the night

1. Open **Spotify on the computer** and leave it running. That machine is the PA.
2. Optional: turn on the [vocal filter](audio-filter/README.md) and pair Bluetooth speakers there.
3. Open **Karaokey** on the Fire Stick → **Connect Spotify**.
4. Scan the QR with your phone (same Wi-Fi). Log in with the Premium account you listed under Users and Access.
5. When the browser says you are connected, look back at the TV.
6. Guests scan the party QR, or open `http://<stick-ip>:8765/` and start adding songs.

Fire OS cannot play Spotify in the background while Karaokey is on screen. Keep Spotify on the computer. Mute the TV if it starts competing.

Then queue the song you always pretend you do not want to sing.

## GitHub Pages intro and callback

`docs/` is a public intro site. After you push:

1. Repository → **Settings** → **Pages**
2. Source: **Deploy from a branch**
3. Branch `main` (or `master`), folder `/docs`
4. Site: `https://<your-username>.github.io/karaokey/`
5. Want that as your Spotify redirect? Add `https://<your-username>.github.io/karaokey/callback/` in the Dashboard and in `android/local.properties`, then rebuild.

The callback page only forwards the login code to your Fire Stick. It does not store tokens.

## Optional vocal filter

Karaokey never sees the Spotify audio stream, so it cannot strip vocals on the TV. To duck centered lead vocals, run a filter on the computer that plays Spotify. See [audio-filter/README.md](audio-filter/README.md).

## Known edges

- Host needs Spotify Premium
- Phones must be on the same Wi-Fi as the Stick
- Spotify Development Mode caps how many people can authorize your app
- No vocal removal inside the TV app
- Lyrics come from [LRCLIB](https://lrclib.net/). Some tracks just do not have them. Sing anyway.

## Under the hood

- Android TV app (Kotlin + Jetpack Compose for TV)
- Local web server on port 8765 for guest phones
- Room SQLite for the party queue
- Spotify Web API for search and playback
- LRCLIB for synced lyrics

Download it. Break it with a real party. Tell us what happened.
