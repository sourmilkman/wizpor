"""Config and custom-word loading."""
from __future__ import annotations

import logging
import os
import tomllib
from dataclasses import dataclass, fields
from pathlib import Path

log = logging.getLogger(__name__)


def config_dir() -> Path:
    base = os.environ.get("APPDATA")
    d = Path(base) / "wizpor" if base else Path.home() / ".wizpor"
    d.mkdir(parents=True, exist_ok=True)
    return d


@dataclass
class Config:
    hotkey: str = "ctrl_r"
    whisper_model: str = "large-v3-turbo"
    whisper_device: str = "cuda"
    whisper_compute_type: str = "float16"
    cpu_fallback_model: str = "small.en"
    language: str = "en"
    cleanup_enabled: bool = True
    ollama_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5:3b"
    cleanup_timeout: float = 3.0
    min_recording_s: float = 0.3
    double_tap_s: float = 0.4
    tap_threshold_s: float = 0.3


DEFAULT_TOML = """# wizpor settings. Delete this file to reset. Restart wizpor after editing.

# Push-to-talk key (pynput Key name): ctrl_r, alt_r, shift_r, f9, ...
hotkey = "ctrl_r"

# Speech-to-text (faster-whisper)
whisper_model = "large-v3-turbo"
whisper_device = "cuda"          # "cuda" or "cpu"
whisper_compute_type = "float16"
cpu_fallback_model = "small.en"  # used if CUDA fails
language = "en"

# AI tidy-up via Ollama (set cleanup_enabled = false for raw text always)
cleanup_enabled = true
ollama_url = "http://localhost:11434"
ollama_model = "qwen2.5:3b"
cleanup_timeout = 3.0            # seconds; raw text is pasted if exceeded

# Timing (seconds)
min_recording_s = 0.3
double_tap_s = 0.4
tap_threshold_s = 0.3
"""

DEFAULT_WORDS = """# Custom vocabulary, one term per line. Helps Whisper and the tidy-up
# spell your project names, libraries and jargon correctly.
Claude
TypeScript
Tailwind
useEffect
"""


def load_config(path: Path) -> Config:
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(DEFAULT_TOML, encoding="utf-8")
    try:
        data = tomllib.loads(path.read_text(encoding="utf-8"))
    except (tomllib.TOMLDecodeError, OSError) as e:
        log.warning("Bad config %s (%s); using defaults", path, e)
        return Config()
    known = {f.name: f.type for f in fields(Config)}
    kwargs = {}
    defaults = Config()
    for k, v in data.items():
        if k not in known:
            log.warning("Unknown config key ignored: %s", k)
            continue
        default = getattr(defaults, k)
        if isinstance(default, float) and isinstance(v, int) and not isinstance(v, bool):
            v = float(v)
        if type(v) is not type(default):
            log.warning("Config key %s has wrong type; using default", k)
            continue
        kwargs[k] = v
    return Config(**kwargs)


def load_words(path: Path) -> list[str]:
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(DEFAULT_WORDS, encoding="utf-8")
    words = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            words.append(line)
    return words
