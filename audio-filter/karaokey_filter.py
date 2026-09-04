#!/usr/bin/env python3
"""Real-time vocal-cut filter for computer -> Bluetooth karaoke.

Spotify (or any app) plays into the selected input. Filtered audio goes
to the Bluetooth speaker. This does not decode Spotify files; it processes
the live playback stream after Spotify has already decoded it.
"""

from __future__ import annotations

import argparse
import sys

import numpy as np
import sounddevice as sd

VOCAL_HP_HZ = 180.0
VOCAL_LP_HZ = 8000.0
VOCAL_NOTCH_HZ = 2200.0
SIDE_BOOST = 10 ** (2.5 / 20)


class Biquad:
    def __init__(self, b0: float, b1: float, b2: float, a1: float, a2: float) -> None:
        self.b0 = b0
        self.b1 = b1
        self.b2 = b2
        self.a1 = a1
        self.a2 = a2
        self.x1 = 0.0
        self.x2 = 0.0
        self.y1 = 0.0
        self.y2 = 0.0

    def process(self, x: np.ndarray) -> np.ndarray:
        y = np.empty_like(x)
        x1, x2, y1, y2 = self.x1, self.x2, self.y1, self.y2
        b0, b1, b2, a1, a2 = self.b0, self.b1, self.b2, self.a1, self.a2
        for i, sample in enumerate(x):
            out = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2, x1 = x1, sample
            y2, y1 = y1, out
            y[i] = out
        self.x1, self.x2, self.y1, self.y2 = x1, x2, y1, y2
        return y

    @staticmethod
    def _rbj(kind: str, sr: float, freq: float, q: float, gain_db: float = 0.0) -> "Biquad":
        w0 = 2.0 * np.pi * freq / sr
        cos_w0 = np.cos(w0)
        sin_w0 = np.sin(w0)
        alpha = sin_w0 / (2.0 * q)
        if kind == "highpass":
            b0 = (1.0 + cos_w0) / 2.0
            b1 = -(1.0 + cos_w0)
            b2 = (1.0 + cos_w0) / 2.0
            a0 = 1.0 + alpha
            a1 = -2.0 * cos_w0
            a2 = 1.0 - alpha
        elif kind == "lowpass":
            b0 = (1.0 - cos_w0) / 2.0
            b1 = 1.0 - cos_w0
            b2 = (1.0 - cos_w0) / 2.0
            a0 = 1.0 + alpha
            a1 = -2.0 * cos_w0
            a2 = 1.0 - alpha
        elif kind == "peaking":
            a = 10 ** (gain_db / 40.0)
            b0 = 1.0 + alpha * a
            b1 = -2.0 * cos_w0
            b2 = 1.0 - alpha * a
            a0 = 1.0 + alpha / a
            a1 = -2.0 * cos_w0
            a2 = 1.0 - alpha / a
        else:
            raise ValueError(kind)
        return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

    @classmethod
    def highpass(cls, sr: float, freq: float, q: float = 0.707) -> "Biquad":
        return cls._rbj("highpass", sr, freq, q)

    @classmethod
    def lowpass(cls, sr: float, freq: float, q: float = 0.707) -> "Biquad":
        return cls._rbj("lowpass", sr, freq, q)

    @classmethod
    def peaking(cls, sr: float, freq: float, gain_db: float, q: float) -> "Biquad":
        return cls._rbj("peaking", sr, freq, q, gain_db)


STRENGTHS = {
    "mild": 0.55,
    "medium": 0.75,
    "strong": 0.92,
}


class VocalCut:
    def __init__(self, sample_rate: float, strength: float) -> None:
        self.keep_mid = 1.0 - strength
        self.low_l = Biquad.lowpass(sample_rate, VOCAL_HP_HZ)
        self.vox_hp = Biquad.highpass(sample_rate, VOCAL_HP_HZ)
        self.vox_lp = Biquad.lowpass(sample_rate, VOCAL_LP_HZ)
        self.vox_notch = Biquad.peaking(sample_rate, VOCAL_NOTCH_HZ, -8.0, 1.1)
        self.air = Biquad.highpass(sample_rate, VOCAL_LP_HZ)
        self.side_hp = Biquad.highpass(sample_rate, 80.0)

    def process(self, frames: np.ndarray) -> np.ndarray:
        left = frames[:, 0]
        right = frames[:, 1]
        mid = 0.5 * (left + right)
        side = 0.5 * (left - right)

        low = self.low_l.process(mid)
        vox = self.vox_notch.process(self.vox_lp.process(self.vox_hp.process(mid)))
        air = self.air.process(mid)
        mid_out = low + (vox * self.keep_mid) + air
        side_out = self.side_hp.process(side) * SIDE_BOOST

        out = np.empty_like(frames)
        out[:, 0] = np.clip(mid_out + side_out, -1.0, 1.0)
        out[:, 1] = np.clip(mid_out - side_out, -1.0, 1.0)
        return out


def list_devices() -> None:
    print(sd.query_devices())


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Karaokey real-time vocal-cut filter")
    parser.add_argument("--list-devices", action="store_true")
    parser.add_argument("--input", type=int, default=None, help="Input device index (Stereo Mix / monitor)")
    parser.add_argument("--output", type=int, default=None, help="Output device index (Bluetooth speaker)")
    parser.add_argument("--strength", choices=STRENGTHS, default="medium")
    parser.add_argument("--samplerate", type=int, default=48000)
    parser.add_argument("--blocksize", type=int, default=1024)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.list_devices:
        list_devices()
        return 0

    filter_chain = VocalCut(args.samplerate, STRENGTHS[args.strength])

    def callback(indata, outdata, frames, time, status) -> None:  # type: ignore[no-untyped-def]
        if status:
            print(status, file=sys.stderr)
        outdata[:] = filter_chain.process(indata)

    print(
        f"Filtering {args.strength} at {args.samplerate} Hz. "
        "Play Spotify to the input device. Ctrl+C to stop."
    )
    with sd.Stream(
        device=(args.input, args.output),
        samplerate=args.samplerate,
        blocksize=args.blocksize,
        channels=2,
        dtype="float32",
        callback=callback,
    ):
        try:
            while True:
                sd.sleep(1000)
        except KeyboardInterrupt:
            print("Stopped.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
