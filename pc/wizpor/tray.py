"""System tray icon and menu."""
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path
from typing import Callable

from . import __version__


RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
RUN_NAME = "wizpor"


def _legacy_startup_file() -> Path:
    appdata = os.environ.get("APPDATA", str(Path.home()))
    return Path(appdata) / "Microsoft/Windows/Start Menu/Programs/Startup/wizpor.bat"


def startup_command(executable: str | None = None) -> str:
    """Silent launch command: pythonw + launch.pyw (no console window)."""
    exe = executable or sys.executable
    if exe.lower().endswith("python.exe"):
        exe = exe[: -len("python.exe")] + "pythonw.exe"
    launcher = Path(__file__).resolve().parent.parent / "launch.pyw"
    return f'"{exe}" "{launcher}"'


def startup_enabled() -> bool:
    try:
        import winreg

        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
            winreg.QueryValueEx(k, RUN_NAME)
        return True
    except (ImportError, OSError):
        return False


def set_startup(enabled: bool) -> None:
    import winreg

    _legacy_startup_file().unlink(missing_ok=True)  # replace the old console-flashing .bat
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as k:
        if enabled:
            winreg.SetValueEx(k, RUN_NAME, 0, winreg.REG_SZ, startup_command())
        else:
            try:
                winreg.DeleteValue(k, RUN_NAME)
            except FileNotFoundError:
                pass


def open_path(p: Path) -> None:
    if sys.platform == "win32":
        os.startfile(str(p))  # type: ignore[attr-defined]
    else:
        subprocess.Popen(["xdg-open", str(p)])


def _icon_image():
    from PIL import Image, ImageDraw

    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((2, 2, 62, 62), fill=(24, 24, 27, 255))
    d.rounded_rectangle((24, 12, 40, 38), radius=8, fill=(250, 250, 250, 255))
    d.arc((17, 22, 47, 46), start=0, end=180, fill=(250, 250, 250, 255), width=3)
    d.line((32, 46, 32, 53), fill=(250, 250, 250, 255), width=3)
    return img


def make_tray(paths: dict[str, Path], on_quit: Callable[[], None]):
    import pystray
    from pystray import MenuItem as Item

    def toggle_startup(icon, item):
        set_startup(not startup_enabled())

    menu = pystray.Menu(
        Item(f"wizpor v{__version__}", None, enabled=False),
        pystray.Menu.SEPARATOR,
        Item("Open settings", lambda: open_path(paths["config"])),
        Item("Open word list", lambda: open_path(paths["words"])),
        Item("Open history", lambda: open_path(paths["history"])),
        Item("Open log", lambda: open_path(paths["log"])),
        Item("Start with Windows", toggle_startup, checked=lambda item: startup_enabled()),
        pystray.Menu.SEPARATOR,
        Item("Quit", lambda icon, item: on_quit()),
    )
    return pystray.Icon("wizpor", _icon_image(), f"wizpor v{__version__}", menu)
