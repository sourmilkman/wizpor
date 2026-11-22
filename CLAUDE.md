# wizpor: personal Wispr Flow clone

## User context (from 2026-11-22)
- Phone: Android, Samsung Galaxy S21 Ultra
- PC: Windows
- Purpose: fast, efficient voice typing on phone + PC, mainly for prompting and vibe coding
- Must be free: no paid APIs/services, run locally
- AI cleanup step (remove filler, punctuation, self-corrections): importance 7/10
- Show build version in the app UI

## Status
- PC app v0.1.1 WORKING on user PC (confirmed 2026-11-22). Start-with-Windows = registry Run key → pythonw launch.pyw
- PC has NVIDIA GPU
- Android (android/): user uses phone at work while PC is OFF → fully on-device. User chose FLOATING BUBBLE only (accessibility service overlay), keyboard later if needed.
  - STT: Parakeet TDT 0.6B v2 int8 via sherpa-onnx v2.0.3 (Kotlin API vendored; native .so fetched in CI). Model downloaded in-app (~480 MB).
  - Tidy-up: rule-based Cleanup.kt (fillers, "scratch that", word list). On-phone AI tidy = phase 2, only if fast.
  - Mic: MicService FGS (type microphone) started from MainActivity while visible; bubble shows error if Android silences mic.
  - Build: GitHub Actions (.github/workflows/android.yml) → GitHub Release APK; fixed committed keystore so updates install over the top.
  - Status: v0.1.0 build1 WORKING on user phone over Claude app (confirmed 2026-11-22). Background mic via FGS works.
