# wizpor PC: design

## Intent
A free, fully local Wispr Flow-style dictation app for Windows. The user mainly wants to speak prompts and vibe-coding instructions instead of typing them. Speed and accuracy on technical words matter most. Tidying up the text matters, rated 7/10.

## Goals
- Hold a hotkey anywhere, speak, release, and cleaned-up text is pasted at the cursor in any app.
- Everything runs locally on an NVIDIA GPU. No paid APIs and no accounts.
- Under about 1.5 s from releasing the key to pasted text for a 15-second prompt.
- Technical vocabulary survives intact (`useEffect`, `.tsx`, `npm run dev`, camelCase identifiers).

## Non-goals (for v1)
- Phone app. That's a separate, later project: an Android keyboard, then a "use the PC when it's reachable" mode.
- Voice commands that control the PC ("open Chrome").
- Per-app tone or style profiles, and cloud sync.

## Anti-goals (count as failures even if they technically work)
- The tidy-up changes meaning, answers the prompt instead of cleaning it, or adds content. A vibe-coding prompt must stay the user's words.
- The user's clipboard gets lost.
- Text ends up pasted into the wrong window or typed out slowly character by character.

## Constraints
- Windows 10/11 with an NVIDIA GPU. VRAM is unknown, so default models must fit in about 6 GB combined.
- Free, open-source components only.
- The build version is shown in the UI (tray tooltip, tray menu, and the overlay).

## Approaches considered
- **Python tray app + faster-whisper + Ollama (chosen).** Fastest to build, uses the best local speech-recognition stack (CUDA via CTranslate2), and the tidy-up model is easy to swap.
- C#/.NET native app + whisper.cpp + llama.cpp. Slicker install and a smaller footprint, but a much slower build and harder to change. Not worth it for personal use.
- Using an existing tool (Handy, Buzz, etc.). No tidy-up tuned for coding and limited customisation, which is why the user wants their own.

## Design

**Flow:** hotkey down → record mic (16 kHz mono) → hotkey up → Whisper transcribe → tidy-up model cleans the text → paste at cursor.

**Components** (each a small module that can be tested on its own):
1. **Hotkey listener.** Default is *hold Right Ctrl* (push-to-talk). Double-tapping it toggles hands-free mode, which stops on the next tap. Configurable.
2. **Recorder.** Captures audio with `sounddevice` while the key is held. Discards recordings under 0.3 s.
3. **Transcriber.** faster-whisper, model `large-v3-turbo` on CUDA (float16), loaded once at startup and kept warm. Uses an `initial_prompt` built from the user's word list to bias recognition toward their vocabulary. Falls back to CPU with `small.en` if CUDA isn't available.
4. **Tidy-up.** Calls the local Ollama HTTP API with a strict system prompt: remove filler words, fix punctuation and capitalisation, apply spoken self-corrections ("no wait, make that…"), format spoken lists as lists, keep technical terms and the original meaning, never answer or add anything, and output only the cleaned text. Default model is a small instruct model of about 3–4B parameters (exact model is the builder's call, picked for speed and instruction-following). Uses `temperature=0` and a timeout of about 3 s. On a timeout or error it pastes the raw transcript instead, so it never blocks. The word list is included here too.
5. **Raw mode.** Holding Shift together with the hotkey skips the tidy-up for that dictation.
6. **Paster.** Saves the clipboard, puts the text on it, sends Ctrl+V, then restores the old clipboard after a short delay.
7. **Overlay.** A small always-on-top pill near the bottom of the screen that doesn't take focus. It shows recording (with a live audio level), processing, and done or error, and briefly shows the version.
8. **Tray icon.** Menu with the version, an "Open config" item, an "Open history" item, a "Start with Windows" toggle, and Quit.
9. **Config.** `config.toml` in `%APPDATA%\wizpor`: hotkey, models, tidy-up on/off, Ollama URL, timeout, and the path to the word list. `words.txt` holds custom vocabulary, one term per line.
10. **History.** Appends raw text, cleaned text, timings and a timestamp to `history.jsonl`, so a lost dictation can always be recovered.

**Setup:** a `setup.bat` creates a venv, installs requirements, and pulls the Ollama model. A `run.bat` starts the app with `pythonw`, so no console window appears. A README covers installing Python, CUDA libraries and Ollama.

**Testing:** unit tests for the tidy-up prompt and response handling (raw fallback on timeout), config loading, history writing, and the hotkey state machine (hold vs double-tap toggle). Hardware-facing parts (mic, paste, GPU) sit behind thin interfaces and are mocked in tests. The user verifies them by hand on Windows.

## Left to the builder
- Exact tidy-up model, and fine-tuning the tidy-up prompt (include a few few-shot examples drawn from coding prompts).
- Overlay UI library (tkinter is fine).
- Exact hotkey and clipboard libraries.
- Version scheme. Single source in `wizpor/__init__.py`, starting at `0.1.0`.
