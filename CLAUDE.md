# wizpor: personal Wispr Flow clone

## User context (from 2026-11-22)
- Phone: Android, Samsung Galaxy S21 Ultra
- PC: Windows
- Purpose: fast, efficient voice typing on phone + PC, mainly for prompting and vibe coding
- Must be free: no paid APIs/services, run locally
- AI cleanup step (remove filler, punctuation, self-corrections): importance 7/10
- Show build version in the app UI

## Status
- PC app v0.1.0 BUILT in pc/ (40 tests pass). Not yet run on real Windows hardware; awaiting user test (setup.bat → run.bat)
- Plan: PC first (Python + faster-whisper + Ollama), then Android keyboard (on-device Whisper), later "use PC when reachable" mode
- PC has NVIDIA GPU
