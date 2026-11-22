# wizpor Android: design

## Intent
Voice typing on the user's Galaxy S21 Ultra, mainly for prompting AI tools while at work, when the home PC is off. It must be free to build and use, and run fully on the phone.

## Goals
- A voice keyboard (Android InputMethodService) usable in any app. Hold the mic button to talk, or tap it for hands-free mode and tap again to stop. Text is inserted at the cursor.
- On-device speech-to-text with NVIDIA Parakeet TDT 0.6B v2 (int8) via sherpa-onnx (Apache-2.0). About 1–2 s for a 15 s prompt; punctuation and capitals come from the model.
- Instant rule-based tidy-up: filler removal, "scratch that", custom word list.
- Version shown in both the keyboard and the setup screen.

## Non-goals (this version)
- On-phone AI tidy-up (phase 2, only if fast enough), PC relay mode, and word-list sync.
- A full QWERTY keyboard. The user flips back to the Samsung keyboard with the switch button.

## Anti-goals
- Network use while dictating. The only network call is the one-time model download.
- Losing the downloaded model on update. APKs are signed with one stable key so updates install over the top.

## Approaches considered
- **Parakeet via sherpa-onnx (chosen):** best accuracy-to-speed ratio on a phone CPU, with prebuilt Android native libraries.
- whisper.cpp: needs a native build and is slower at similar accuracy.
- Android's built-in recogniser: weak on technical terms, and Gboard already offers it.

## Design
- **Build:** a GitHub Actions workflow downloads the sherpa-onnx v2.0.3 Android native libraries (arm64-v8a only), runs unit tests, builds a release APK signed with a committed keystore, and publishes it as a GitHub Release so the phone can download it.
- **Setup screen (MainActivity):** version; steps with status (1. grant mic permission, 2. download the speech model, about 480 MB, Wi-Fi recommended, 3. enable the keyboard, 4. switch to it); a test text box; a word list editor; a filler-removal toggle.
- **Model:** downloaded with Android DownloadManager (shows a notification and survives the app closing), then extracted (tar.bz2 via commons-compress) to `filesDir/models/parakeet-v2/`.
- **Keyboard UI:** a large mic button, a status line (Listening / Transcribing / Ready / errors), the version, and globe (switch keyboard), space, backspace (repeats when held) and enter buttons.
- **Audio:** AudioRecord at 16 kHz mono, recording only while active.
- **Recogniser:** loaded once in the background when the keyboard opens and kept in a process-wide singleton, with 4 threads.
- **Tidy-up (pure Kotlin, unit-tested):** remove standalone um/uh/er/erm/ah/hmm (plus the commas around them); "scratch that" deletes the preceding sentence; custom words replace case-insensitive 1–3-word sequences whose letters match (e.g. "use effect" → useEffect); tidy spacing and re-capitalise sentence starts.
- **Insertion:** `commitText`, adding a leading space when the text before the cursor doesn't end in whitespace.
