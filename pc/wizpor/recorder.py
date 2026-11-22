"""Microphone capture. The stream stays open so the first word is never clipped."""
from __future__ import annotations

import threading


class Recorder:
    def __init__(self, samplerate: int = 16000):
        self.samplerate = samplerate
        self._chunks: list = []
        self._recording = False
        self._lock = threading.Lock()
        self._stream = None
        self.level = 0.0

    def open(self) -> None:
        import sounddevice as sd

        self._stream = sd.InputStream(
            samplerate=self.samplerate, channels=1, dtype="float32", callback=self._cb, blocksize=0
        )
        self._stream.start()

    def _cb(self, indata, frames, time_info, status):
        import numpy as np

        mono = indata[:, 0]
        self.level = float(np.sqrt(np.mean(mono * mono))) if len(mono) else 0.0
        if self._recording:
            with self._lock:
                self._chunks.append(mono.copy())

    def start(self) -> None:
        with self._lock:
            self._chunks = []
        self._recording = True

    def stop(self):
        import numpy as np

        self._recording = False
        with self._lock:
            chunks, self._chunks = self._chunks, []
        if not chunks:
            return np.zeros(0, dtype=np.float32)
        return np.concatenate(chunks).astype(np.float32)

    def close(self) -> None:
        if self._stream is not None:
            self._stream.stop()
            self._stream.close()
