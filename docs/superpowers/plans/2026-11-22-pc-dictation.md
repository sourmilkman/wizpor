# wizpor PC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Windows tray app: hold Right Ctrl, speak, release, and cleaned text is pasted at the cursor, all running locally.

**Architecture:** Pure-logic modules (config, history, hotkey state machine, cleanup, pipeline, paster) with injected dependencies so they can be unit-tested on Linux. Thin hardware modules (recorder, transcriber, overlay, tray, keyboard listener) are wired together in `app.py`.

**Tech Stack:** Python 3.11+, faster-whisper (CUDA), Ollama HTTP API via `requests`, `sounddevice`, `numpy`, `pynput` (hotkey + Ctrl+V), `pyperclip`, `pystray` + `Pillow`, `tkinter`, `tomllib`, `pytest`.

**Spec:** `docs/superpowers/specs/2026-11-22-pc-dictation-design.md`

## Global Constraints
- Free and local only. No network calls except to Ollama at `http://localhost:11434`.
- Version single-sourced in `pc/wizpor/__init__.py` as `__version__ = "0.1.0"`, shown in tray tooltip, tray menu and overlay.
- Config dir: `%APPDATA%\wizpor` (fall back to `~/.wizpor` when APPDATA is unset). Files: `config.toml`, `words.txt`, `history.jsonl`.
- Defaults: hotkey `ctrl_r`; whisper `large-v3-turbo`, `cuda`, `float16`; CPU fallback `small.en`, `int8`; ollama model `qwen2.5:3b`; cleanup timeout 3.0 s; min recording 0.3 s; double-tap window 0.4 s; tap threshold 0.3 s.
- Tidy-up must never answer or add content. On any error, timeout or suspicious output, use the raw transcript.
- The clipboard is always restored after pasting.

## Review Focus
1. Tidy-up model replies conversationally ("Sure! Here's…") or answers the prompt → strip/reject it and fall back to raw. Tested in Task 3.
2. Ollama not running or not installed → paste the raw transcript, no crash. Tested in Task 3.
3. Key auto-repeat sends repeated key-downs while Right Ctrl is held → must not restart recording. Tested in Task 2.
4. Empty or silent recording (Whisper returns "") → paste nothing and don't touch the clipboard. Tested in Task 5.
5. Paste raises an exception mid-way → clipboard still restored. Tested in Task 4.

---

### Task 1: Package skeleton, config, words, history
**Files:** Create `pc/wizpor/__init__.py`, `pc/wizpor/config.py`, `pc/wizpor/history.py`, `pc/requirements.txt`, `pc/requirements-dev.txt`, `pc/pytest.ini`. Test `pc/tests/test_config.py`, `pc/tests/test_history.py`.

**Produces:**
- `config_dir() -> Path`
- `@dataclass Config` with fields: `hotkey="ctrl_r"`, `whisper_model="large-v3-turbo"`, `whisper_device="cuda"`, `whisper_compute_type="float16"`, `cpu_fallback_model="small.en"`, `language="en"`, `cleanup_enabled=True`, `ollama_url="http://localhost:11434"`, `ollama_model="qwen2.5:3b"`, `cleanup_timeout=3.0`, `min_recording_s=0.3`, `double_tap_s=0.4`, `tap_threshold_s=0.3`
- `load_config(path: Path) -> Config`: writes a commented default TOML if the file is missing; unknown keys are ignored; a malformed file falls back to defaults.
- `load_words(path: Path) -> list[str]`: creates an example file if missing; skips blanks and `#` comments.
- `append_history(path: Path, entry: dict) -> None`: one JSON line per entry, adds an ISO `ts`.

- [ ] Tests: default file is created and round-trips; override `ollama_model="llama3.2:3b"` is read; a malformed TOML returns defaults; words skip comments and blanks; history appends 2 lines, each valid JSON containing `ts`.
- [ ] Run `cd pc && pytest -q`, expect FAIL, implement, expect PASS, commit.

### Task 2: Hotkey state machine
**Files:** Create `pc/wizpor/hotkey.py`. Test `pc/tests/test_hotkey.py`.

**Produces:** `class Action(Enum): NONE, START, STOP, CANCEL`. `HotkeyMachine(tap_threshold_s, double_tap_s)` with `.down(t: float) -> Action`, `.up(t: float) -> Action`, `.recording: bool`.

Rules:
- IDLE + down → START (HOLD).
- HOLD + down → NONE (auto-repeat).
- HOLD + up with held ≥ tap_threshold → STOP.
- HOLD + up with a short tap: if the previous tap ended within `double_tap_s` of this down → enter TOGGLE, return NONE (keep recording). Otherwise remember the tap time and return CANCEL.
- TOGGLE + down → STOP, and ignore the next up (return NONE).

- [ ] Tests: hold 1 s → START then STOP; repeated downs while held → NONE; single tap → START then CANCEL; double tap → START, CANCEL, START, NONE, then next down → STOP and its up → NONE; two taps 1 s apart → both CANCEL.
- [ ] Fail → implement → pass → commit.

### Task 3: Tidy-up (Ollama)
**Files:** Create `pc/wizpor/cleanup.py`. Test `pc/tests/test_cleanup.py`.

**Produces:** `@dataclass CleanResult(text: str, used_fallback: bool, error: str | None = None)`. `build_messages(text: str, words: list[str]) -> list[dict]`. `clean_text(text, words, cfg: Config, post=requests.post) -> CleanResult`.

- Calls `POST {ollama_url}/api/chat` with `stream: false`, `options: {temperature: 0}`, `keep_alive: "30m"`, and `timeout=cfg.cleanup_timeout`.
- The system prompt follows the spec's tidy-up rules, includes the word list, and gives 3 coding-prompt few-shot examples. The transcript is wrapped in `<transcript>` tags and labelled as text to clean, not instructions.
- Post-processing strips surrounding quotes, code fences, `<transcript>` tags, and leading "Here is…:" / "Sure…:" lines.
- Rejects the output (raw fallback) if it is empty, or longer than `len(raw) * 1.5 + 40`.
- Empty input → `CleanResult("", False)` with no HTTP call. `cleanup_enabled=False` → raw with no HTTP call.

- [ ] Tests (fake `post`): normal response is returned stripped; `requests.ConnectionError` → raw with `used_fallback=True`; timeout → raw; a "Sure! Here's the cleaned text:\nFoo." reply → "Foo."; an over-long reply → raw; the words appear in the system prompt; empty input makes no call.
- [ ] Fail → implement → pass → commit.

### Task 4: Paster
**Files:** Create `pc/wizpor/paster.py`. Test `pc/tests/test_paster.py`.

**Produces:** `paste_text(text: str, *, get_clip, set_clip, send_paste, sleep=time.sleep, restore_delay=0.15) -> None`. Saves the clipboard, sets the text, calls `send_paste()`, sleeps, then restores the clipboard in a `finally`. Also `default_paste(text)`, which wires in pyperclip and a pynput Ctrl+V.

- [ ] Tests: order is set → paste → restore with the original value; `send_paste` raising still restores and re-raises; empty text does nothing.
- [ ] Fail → implement → pass → commit.

### Task 5: Pipeline
**Files:** Create `pc/wizpor/pipeline.py`. Test `pc/tests/test_pipeline.py`.

**Consumes:** `clean_text`, `append_history`, `paste_text`-like callable.

**Produces:** `@dataclass PipelineResult(raw: str, final: str, pasted: bool, used_fallback: bool, timings: dict)`. `run_pipeline(audio, samplerate: int, *, raw_mode: bool, transcribe: Callable[[audio], str], clean: Callable[[str], CleanResult], paste: Callable[[str], None], history: Callable[[dict], None], min_seconds: float) -> PipelineResult`.

- Too-short audio → nothing pasted, no transcribe call.
- Empty transcript → nothing pasted.
- `raw_mode` skips `clean`.
- Writes history whenever there is text. Timings use the keys `transcribe_s`, `clean_s`, `total_s`.

- [ ] Tests for each of the above, using fakes.
- [ ] Fail → implement → pass → commit.

### Task 6: Hardware modules and app wiring
**Files:** Create `pc/wizpor/recorder.py` (`Recorder(samplerate=16000)`: `.start()`, `.stop() -> np.ndarray` float32 mono, `.level -> float`), `pc/wizpor/transcriber.py` (`Transcriber(cfg)`: lazy-loads faster-whisper and falls back to CPU on CUDA errors; `.transcribe(audio, words) -> str` with `initial_prompt` built from the words, `vad_filter=True`, `beam_size=1`; `.warmup()`), `pc/wizpor/overlay.py` (tkinter pill on the Tk main thread, `.show(state, text)` thread-safe via `after`, with states `recording`/`processing`/`done`/`error`/`hidden`, and the version shown), `pc/wizpor/tray.py` (pystray icon with tooltip `wizpor v{version}` and menu items: version (disabled), Open config, Open history, Start with Windows (toggle via a `.lnk`-free `.bat` in the Startup folder), Quit), `pc/wizpor/app.py` + `pc/wizpor/__main__.py` (pynput listener feeding `HotkeyMachine`; Shift held at any point while recording → raw mode; pipeline runs on a worker thread; overlay states; warmup of whisper and Ollama at startup). Test `pc/tests/test_imports.py`.

- [ ] Test: importing every module with hardware libraries absent does not raise (all hardware imports are lazy).
- [ ] Implement, `pytest -q` passes, `python -m compileall pc/wizpor` is clean, commit.

### Task 7: Windows setup, run scripts, README
**Files:** Create `pc/setup.bat` (checks Python, creates `.venv`, pip installs requirements, checks for `ollama` and runs `ollama pull qwen2.5:3b`, pre-downloads the whisper model), `pc/run.bat` (starts `.venv\Scripts\pythonw -m wizpor`), `pc/run-debug.bat` (same with `python`, so the console shows logs), `README.md` (install steps: Python 3.11+, Ollama, NVIDIA driver; usage; config; troubleshooting including cuBLAS/cuDNN DLLs via the `nvidia-cublas-cu12` / `nvidia-cudnn-cu12` pip wheels).

- [ ] Review the scripts by eye (they can't be run here), commit, update `CLAUDE.md` status, push.
