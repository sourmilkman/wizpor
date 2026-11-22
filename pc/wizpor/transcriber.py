"""Speech-to-text with faster-whisper, CUDA first, CPU fallback."""
from __future__ import annotations

import logging
import os
import sys
import threading
from pathlib import Path

from .config import Config

log = logging.getLogger(__name__)


def _add_nvidia_dll_dirs() -> None:
    """On Windows, make pip-installed cuBLAS/cuDNN DLLs findable by CTranslate2."""
    if sys.platform != "win32":
        return
    for base in sys.path:
        nv = Path(base) / "nvidia"
        if not nv.is_dir():
            continue
        for bin_dir in nv.glob("*/bin"):
            try:
                os.add_dll_directory(str(bin_dir))
                os.environ["PATH"] = str(bin_dir) + os.pathsep + os.environ.get("PATH", "")
            except OSError:
                pass


class Transcriber:
    def __init__(self, cfg: Config):
        self.cfg = cfg
        self._model = None
        self._lock = threading.Lock()
        self.device = None

    def _load(self, device: str):
        from faster_whisper import WhisperModel

        if device == "cuda":
            _add_nvidia_dll_dirs()
            model = WhisperModel(self.cfg.whisper_model, device="cuda", compute_type=self.cfg.whisper_compute_type)
        else:
            model = WhisperModel(self.cfg.cpu_fallback_model, device="cpu", compute_type="int8")
        self._model, self.device = model, device
        log.info("Whisper loaded on %s", device)

    def _ensure(self):
        if self._model is not None:
            return
        if self.cfg.whisper_device == "cuda":
            try:
                self._load("cuda")
                return
            except Exception as e:
                log.warning("CUDA load failed (%s); falling back to CPU", e)
        self._load("cpu")

    def _run(self, audio, words: list[str]) -> str:
        prompt = ("Vocabulary: " + ", ".join(words) + ".") if words else None
        segments, _info = self._model.transcribe(
            audio,
            language=self.cfg.language or None,
            initial_prompt=prompt,
            vad_filter=True,
            beam_size=1,
            condition_on_previous_text=False,
        )
        return " ".join(s.text.strip() for s in segments).strip()

    def transcribe(self, audio, words: list[str]) -> str:
        with self._lock:
            self._ensure()
            try:
                return self._run(audio, words)
            except Exception as e:
                if self.device != "cuda":
                    raise
                # CUDA DLL problems often only surface on first use.
                log.warning("CUDA transcription failed (%s); switching to CPU", e)
                self._model = None
                self._load("cpu")
                return self._run(audio, words)

    def warmup(self) -> None:
        import numpy as np

        self.transcribe(np.zeros(16000, dtype=np.float32), [])
