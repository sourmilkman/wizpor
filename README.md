# wizpor

Free, offline push-to-talk dictation for Windows, a personal Wispr Flow alternative. Hold a key, speak, let go, and tidied-up text is pasted wherever your cursor is. Everything runs on your own PC.

- **Speech-to-text:** Whisper `large-v3-turbo` (faster-whisper) on your NVIDIA GPU
- **Tidy-up:** a small local AI model via [Ollama](https://ollama.com). It removes filler words, fixes punctuation, applies "no wait, I meant…" corrections, and formats lists and code terms.

## Install (one time)

1. Download this repo: **Code → Download ZIP** on GitHub, then unzip it somewhere permanent (e.g. `Documents\wizpor`).
2. Open the `pc` folder and double-click **`setup.bat`**. It installs Python and Ollama if they're missing (if it installs Python, re-run it once afterwards), then downloads the models (~4 GB total).
3. Double-click **`run.bat`**. A mic icon appears in the system tray and a small pill shows *Ready on CUDA*.
4. Optional: right-click the tray icon and tick **Start with Windows**. It then launches silently each time you log in (Ollama starts itself too).

## Use

| Action | What happens |
|---|---|
| **Hold Right Ctrl**, talk, release | Tidied-up text is pasted at the cursor |
| **Double-tap Right Ctrl** | Hands-free mode: talk as long as you like, tap Right Ctrl to finish |
| Hold **Shift** at any point while recording | Raw mode: pastes exactly what Whisper heard, no tidy-up |
| Right Ctrl + another key (e.g. Ctrl+C) | Works as a normal shortcut; nothing is recorded |

The pill at the bottom of the screen shows *Listening* → *Transcribing* → *Pasted*, and the version number.

## Settings

Tray icon → **Open settings** / **Open word list** / **Open history**. Files live in `%APPDATA%\wizpor\`:

- `config.toml`: hotkey, models, tidy-up on/off, timeouts. Restart wizpor after editing.
- `words.txt`: your vocabulary (project names, libraries, jargon), one per line. Used by both Whisper and the tidy-up. Changes apply immediately.
- `history.jsonl`: every dictation (raw + tidied), so nothing is ever lost.
- `wizpor.log`: diagnostics.

Want a smarter tidy-up and have VRAM to spare (8 GB+)? Run `ollama pull qwen2.5:7b` and set `ollama_model = "qwen2.5:7b"`.

## Troubleshooting

- **Pill says "Ready on CPU"**: CUDA failed and it fell back to the slower CPU model. Update your NVIDIA driver, then run `run-debug.bat` and check the log.
- **"Pasted raw (tidy-up unavailable)"**: Ollama isn't running. Start the Ollama app (it normally runs in the tray), or run `ollama pull qwen2.5:3b` if the model is missing.
- **Nothing happens when I hold Right Ctrl**: run `run-debug.bat` to see errors. If you're typing into an app running as administrator, wizpor must also run as administrator.
- **Wrong words**: add them to `words.txt`.

## Development

```
cd pc
pip install -r requirements-dev.txt
pytest -q
```

Design: `docs/superpowers/specs/2026-11-22-pc-dictation-design.md`

---

# wizpor for Android

A floating mic bubble that types what you say into any app. Speech recognition runs entirely on the phone (NVIDIA Parakeet via sherpa-onnx), so it works with no PC and no internet after the one-time model download.

## Install
1. On your phone, open the repo's **Releases** page on GitHub and tap the newest `wizpor-…apk`. Allow your browser to install apps when asked.
2. Open **wizpor** and follow the four steps on screen: microphone → download the speech model (~480 MB, Wi-Fi recommended) → turn on the bubble in Accessibility → allow background running.
   - If the Accessibility switch is greyed out ("Restricted setting"): App info → ⋮ → **Allow restricted settings**, then try again.

## Use
- Tap a text box. The bubble appears. **Tap it**, talk, **tap again**, and the text appears.
- **Long-press while recording** to cancel. **Long-press while idle** to open settings. **Drag** to move it.
- Updates: install the newer APK over the top. Your model and settings are kept.
