import numpy as np

from karaokey_filter import VocalCut


def _tone(freq: float, sr: float, seconds: float = 0.6) -> np.ndarray:
    t = np.arange(int(sr * seconds)) / sr
    return np.sin(2 * np.pi * freq * t).astype(np.float32)


def _rms(x: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(x))))


def test_center_vocal_band_is_reduced() -> None:
    sr = 48000
    chain = VocalCut(sr, strength=0.75)
    tone = _tone(1000, sr)
    frames = np.stack([tone, tone], axis=1)
    out = chain.process(frames)[sr // 5 :]
    assert _rms(out) < 0.35 * _rms(frames)


def test_side_signal_is_kept() -> None:
    sr = 48000
    chain = VocalCut(sr, strength=0.75)
    tone = _tone(1000, sr)
    frames = np.stack([tone, -tone], axis=1)
    out = chain.process(frames)[sr // 5 :]
    assert _rms(out) > 0.8 * _rms(frames)


def test_centered_bass_is_kept() -> None:
    sr = 48000
    chain = VocalCut(sr, strength=0.75)
    tone = _tone(80, sr)
    frames = np.stack([tone, tone], axis=1)
    out = chain.process(frames)[sr // 5 :]
    assert _rms(out) > 0.7 * _rms(frames)
