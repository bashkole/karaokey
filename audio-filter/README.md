# Karaokey computer vocal filter

Apply this on the computer that plays Spotify. Pair the Bluetooth speaker to that computer. The speaker then plays the already-filtered mix. Karaokey on the TV only shows lyrics.

This is a band-limited center cut, not AI stem separation. It ducks lead vocals that are panned center (most pop/rock). It also ducks centered instruments in the same band. Bass and kick are kept. Wide backing vocals and reverb will still be audible.

## Windows (recommended)

Equalizer APO often does **not** attach directly to Bluetooth devices. Route through VoiceMeeter so the filter always sits in front of the speaker.

1. Install [VoiceMeeter Banana](https://vb-audio.com/Voicemeeter/banana.htm) and restart.
2. Install [Equalizer APO](https://sourceforge.net/projects/equalizerapo/). In its Configurator, install on **VoiceMeeter Input** (Voicemeeter VAIO).
3. Copy `windows/karaokey-vocal-cut.txt` into `C:\Program Files\EqualizerAPO\config\`.
4. Edit `C:\Program Files\EqualizerAPO\config\config.txt` so it contains:

```
Include: karaokey-vocal-cut.txt
```

5. In VoiceMeeter, set hardware out **A1** to your Bluetooth speaker.
6. In Windows sound settings, set the output device to **VoiceMeeter Input**.
7. Open Spotify on the computer and play a song. You should hear less lead vocal on the Bluetooth speaker.

Strengths:

- `karaokey-vocal-cut-mild.txt` if the mix is too thin
- `karaokey-vocal-cut.txt` default
- `karaokey-vocal-cut-strong.txt` if the singer is still too loud

To bypass the filter, comment out the `Include:` line in `config.txt`.

If APO *does* offer your Bluetooth device in the Configurator, you can install on that device and skip VoiceMeeter.

## Linux

### Easy Effects (simplest)

1. Install Easy Effects (needs PipeWire).
2. Open **Output**, add **Voice Suppressor** if your build has it. If not, add **Stereo Tools**.
3. Voice Suppressor starting points:
   - Start: `180`
   - End: `8000`
   - Inverted mode: **off**
   - Raise Correlation until the lead vocal drops without killing the band
4. Or Stereo Tools: Middle level about `-12 dB`, Side level about `+2.5 dB`, then add a low shelf at `80 Hz` / `+4 dB` so the kick stays.
5. In the Players list, enable **Spotify**.
6. Set the Easy Effects output device to the Bluetooth speaker.

### Real-time script

On the computer (not this server):

```bash
uv pip install sounddevice numpy
uv run python karaokey_filter.py --list-devices
uv run python karaokey_filter.py --input <monitor_or_stereo_mix> --output <bluetooth> --strength medium
```

Point Spotify at the input device (a monitor / loopback / Stereo Mix). Point `--output` at the Bluetooth speaker.

## What to expect

| Mix type | Result |
| --- | --- |
| Centered pop vocal | Clear drop, usable karaoke |
| Wide / doubled vocal | Partial drop, some voice left |
| Centered bass + vocal | Voice drops, bass stays |
| Mono track | Almost nothing left — skip the filter |
| Live / heavily reverbed vocal | Voice residue remains |

This cannot match an official karaoke track. It is the best real-time option that still lets Spotify play any song to a Bluetooth speaker.
