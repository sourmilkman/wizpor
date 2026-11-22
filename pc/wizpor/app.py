"""Wires hotkey, recorder, transcriber, tidy-up, paster, overlay and tray together."""
from __future__ import annotations

import logging
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from functools import partial

from . import __version__
from .cleanup import clean_text
from .config import config_dir, load_config, load_words
from .history import append_history
from .hotkey import Action, HotkeyMachine
from .pipeline import run_pipeline

log = logging.getLogger("wizpor")
SAMPLERATE = 16000


def _single_instance() -> bool:
    if sys.platform != "win32":
        return True
    import ctypes

    ctypes.windll.kernel32.CreateMutexW(None, False, "wizpor_single_instance")
    return ctypes.windll.kernel32.GetLastError() != 183  # ERROR_ALREADY_EXISTS


def _resolve_key(name: str):
    from pynput.keyboard import Key, KeyCode

    if hasattr(Key, name):
        return getattr(Key, name)
    if len(name) == 1:
        return KeyCode.from_char(name)
    raise ValueError(f"Unknown hotkey: {name!r}")


class App:
    def __init__(self):
        import tkinter as tk

        from .overlay import Overlay
        from .paster import default_paste
        from .recorder import Recorder
        from .transcriber import Transcriber
        from .tray import make_tray

        d = config_dir()
        self.paths = {
            "config": d / "config.toml",
            "words": d / "words.txt",
            "history": d / "history.jsonl",
            "log": d / "wizpor.log",
        }
        self.cfg = load_config(self.paths["config"])
        load_words(self.paths["words"])  # create the example file on first run
        self.hotkey = _resolve_key(self.cfg.hotkey)
        self.machine = HotkeyMachine(self.cfg.tap_threshold_s, self.cfg.double_tap_s)
        self.recorder = Recorder(SAMPLERATE)
        self.transcriber = Transcriber(self.cfg)
        self.paste = default_paste
        self.worker = ThreadPoolExecutor(max_workers=1)
        self.raw_mode = False
        self.ready = False

        self.root = tk.Tk()
        self.root.withdraw()
        self.overlay = Overlay(self.root, level_fn=lambda: self.recorder.level)
        self.tray = make_tray(self.paths, on_quit=lambda: self.root.after(0, self.quit))

    # ---- startup ----
    def _warmup(self):
        self.overlay.show("loading", "Loading speech model…")
        try:
            self.transcriber.warmup()
        except Exception as e:
            log.exception("Whisper failed to load")
            self.overlay.show("error", f"Speech model failed: {e}"[:60], 8000)
            return
        self.ready = True
        msg, state, ms = f"Ready on {self.transcriber.device.upper()} · hold Right Ctrl", "done", 2500
        if self.cfg.cleanup_enabled:
            import requests

            try:
                r = requests.post(
                    f"{self.cfg.ollama_url.rstrip('/')}/api/generate",
                    json={"model": self.cfg.ollama_model, "keep_alive": "30m"},
                    timeout=120,
                )
                r.raise_for_status()
            except Exception as e:
                log.warning("Ollama warmup failed: %s", e)
                msg, state, ms = "Ready · tidy-up unavailable (is Ollama running?)", "error", 5000
        self.overlay.show(state, msg, ms)

    # ---- keyboard ----
    def _on_press(self, key):
        from pynput.keyboard import Key

        if key == self.hotkey:
            self._handle(self.machine.down(time.monotonic()))
        elif key in (Key.shift, Key.shift_l, Key.shift_r):
            if self.machine.recording:
                self.raw_mode = True
        else:
            self._handle(self.machine.interrupt())

    def _on_release(self, key):
        if key == self.hotkey:
            act = self.machine.up(time.monotonic())
            if act == Action.NONE and self.machine.recording:
                self.overlay.show("recording", "Hands-free · tap Right Ctrl to stop")
            self._handle(act)

    def _handle(self, act: Action):
        if act == Action.START:
            if not self.ready:
                self.overlay.show("loading", "Still loading, one moment…", 1500)
            self.raw_mode = False
            self.recorder.start()
            self.overlay.show("recording", "Listening…")
        elif act == Action.CANCEL:
            self.recorder.stop()
            self.overlay.show("hidden")
        elif act == Action.STOP:
            audio = self.recorder.stop()
            raw = self.raw_mode
            self.overlay.show("processing", "Transcribing…" if raw else "Transcribing & tidying…")
            self.worker.submit(self._process, audio, raw)

    # ---- processing ----
    def _process(self, audio, raw_mode: bool):
        try:
            words = load_words(self.paths["words"])  # re-read so edits apply immediately
            res = run_pipeline(
                audio,
                SAMPLERATE,
                raw_mode=raw_mode,
                transcribe=lambda a: self.transcriber.transcribe(a, words),
                clean=lambda t: clean_text(t, words, self.cfg),
                paste=self.paste,
                history=partial(append_history, self.paths["history"]),
                min_seconds=self.cfg.min_recording_s,
            )
        except Exception as e:
            log.exception("Dictation failed")
            self.overlay.show("error", f"Error: {e}"[:60], 5000)
            return
        if not res.pasted:
            self.overlay.show("done", "No speech heard", 1200)
        elif res.used_fallback:
            self.overlay.show("error", "Pasted raw (tidy-up unavailable)", 3000)
        else:
            self.overlay.show("done", f"Pasted · {res.timings['total_s']:.1f}s", 1200)
        log.info("Dictation: %s", res.timings)

    # ---- lifecycle ----
    def run(self):
        from pynput import keyboard

        log.info("wizpor v%s starting", __version__)
        self.recorder.open()
        self.listener = keyboard.Listener(on_press=self._on_press, on_release=self._on_release)
        self.listener.start()
        self.tray.run_detached()
        threading.Thread(target=self._warmup, daemon=True).start()
        self.root.mainloop()

    def quit(self):
        log.info("Quitting")
        for fn in (self.listener.stop, self.tray.stop, self.recorder.close):
            try:
                fn()
            except Exception:
                pass
        self.worker.shutdown(wait=False, cancel_futures=True)
        self.root.quit()


def main():
    import os

    # pythonw has no console: give libraries (tqdm etc.) somewhere harmless to write.
    if sys.stdout is None:
        sys.stdout = open(os.devnull, "w")
    if sys.stderr is None:
        sys.stderr = open(os.devnull, "w")
    d = config_dir()
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        handlers=[logging.FileHandler(d / "wizpor.log", encoding="utf-8"), logging.StreamHandler()],
    )
    if not _single_instance():
        log.info("wizpor is already running")
        return
    App().run()
