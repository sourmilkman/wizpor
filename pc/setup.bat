@echo off
setlocal
cd /d "%~dp0"
echo.
echo ===== wizpor setup =====
echo.

rem --- Python 3.11+ ---
set "PY=python"
where py >nul 2>nul && set "PY=py -3"
%PY% -c "import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)" >nul 2>nul
if errorlevel 1 (
  echo Python 3.11+ not found. Installing Python 3.12 with winget...
  winget install -e --id Python.Python.3.12 --accept-source-agreements --accept-package-agreements
  echo.
  echo Python installed. Close this window and double-click setup.bat again.
  pause
  exit /b 1
)

rem --- Virtual environment + packages ---
if not exist ".venv\Scripts\python.exe" (
  echo [1/4] Creating virtual environment...
  %PY% -m venv .venv || goto :fail
)
echo.
echo [2/4] Installing packages (about 1.5 GB, first time only)...
".venv\Scripts\python.exe" -m pip install --upgrade pip -q
".venv\Scripts\python.exe" -m pip install -r requirements.txt || goto :fail

rem --- Ollama (AI tidy-up) ---
set "OLLAMA=ollama"
where ollama >nul 2>nul
if errorlevel 1 (
  if exist "%LOCALAPPDATA%\Programs\Ollama\ollama.exe" (
    set "OLLAMA=%LOCALAPPDATA%\Programs\Ollama\ollama.exe"
  ) else (
    echo Ollama not found. Installing with winget...
    winget install -e --id Ollama.Ollama --accept-source-agreements --accept-package-agreements
    set "OLLAMA=%LOCALAPPDATA%\Programs\Ollama\ollama.exe"
  )
)
echo.
echo [3/4] Downloading AI tidy-up model (about 2 GB)...
"%OLLAMA%" pull qwen2.5:3b
if errorlevel 1 (
  echo.
  echo WARNING: could not pull the tidy-up model. Start the Ollama app, then run:
  echo     ollama pull qwen2.5:3b
  echo wizpor still works without it, you just get raw text.
  echo.
)

rem --- Whisper speech model ---
echo.
echo [4/4] Downloading speech model (about 1.6 GB, progress bar below)...
".venv\Scripts\python.exe" download_models.py large-v3-turbo || goto :fail

echo.
echo ===== Setup complete =====
echo Double-click run.bat to start wizpor. Hold Right Ctrl and talk.
echo Tip: right-click the tray icon and tick "Start with Windows".
echo.
pause
exit /b 0

:fail
echo.
echo Setup failed. Scroll up for the error.
pause
exit /b 1
