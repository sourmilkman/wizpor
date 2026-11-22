"""Small always-on-top status pill that never steals focus."""
from __future__ import annotations

import sys
from typing import Callable

from . import __version__

W, H = 230, 40
COLORS = {
    "recording": "#ef4444",
    "processing": "#f59e0b",
    "done": "#22c55e",
    "error": "#ef4444",
    "loading": "#60a5fa",
}
BG = "#18181b"
FG = "#fafafa"
MUTED = "#a1a1aa"
TRANSPARENT = "#010203"


class Overlay:
    def __init__(self, root, level_fn: Callable[[], float] = lambda: 0.0):
        import tkinter as tk

        self.root = root
        self.level_fn = level_fn
        self.state = "hidden"
        self._hide_job = None

        win = tk.Toplevel(root)
        win.overrideredirect(True)
        win.attributes("-topmost", True)
        win.configure(bg=TRANSPARENT)
        if sys.platform == "win32":
            win.attributes("-transparentcolor", TRANSPARENT)
        sw, sh = win.winfo_screenwidth(), win.winfo_screenheight()
        win.geometry(f"{W}x{H}+{(sw - W) // 2}+{sh - H - 70}")
        c = tk.Canvas(win, width=W, height=H, bg=TRANSPARENT, highlightthickness=0)
        c.pack()
        r = H // 2
        c.create_oval(0, 0, H, H, fill=BG, outline=BG)
        c.create_oval(W - H, 0, W, H, fill=BG, outline=BG)
        c.create_rectangle(r, 0, W - r, H, fill=BG, outline=BG)
        self.dot = c.create_oval(14, r - 6, 26, r + 6, fill=COLORS["loading"], outline="")
        self.label = c.create_text(36, r, text="", fill=FG, anchor="w", font=("Segoe UI", 10))
        self.ver = c.create_text(W - 16, r, text=f"v{__version__}", fill=MUTED, anchor="e", font=("Segoe UI", 8))
        self.bars = [c.create_rectangle(0, 0, 0, 0, fill=COLORS["recording"], outline="") for _ in range(5)]
        self.canvas, self.win = c, win
        win.withdraw()
        win.update_idletasks()
        self._no_activate()
        self._animate()

    def _no_activate(self):
        if sys.platform != "win32":
            return
        try:
            import ctypes

            GWL_EXSTYLE, WS_EX_NOACTIVATE, WS_EX_TOOLWINDOW = -20, 0x08000000, 0x00000080
            hwnd = ctypes.windll.user32.GetParent(self.win.winfo_id())
            style = ctypes.windll.user32.GetWindowLongW(hwnd, GWL_EXSTYLE)
            ctypes.windll.user32.SetWindowLongW(hwnd, GWL_EXSTYLE, style | WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW)
        except Exception:
            pass

    def show(self, state: str, text: str = "", hide_after_ms: int | None = None) -> None:
        """Thread-safe."""
        self.root.after(0, self._show, state, text, hide_after_ms)

    def _show(self, state, text, hide_after_ms):
        if self._hide_job:
            self.root.after_cancel(self._hide_job)
            self._hide_job = None
        self.state = state
        if state == "hidden":
            self.win.withdraw()
            return
        self.canvas.itemconfigure(self.dot, fill=COLORS.get(state, FG))
        self.canvas.itemconfigure(self.label, text=text)
        self.win.deiconify()
        self.win.attributes("-topmost", True)
        if hide_after_ms:
            self._hide_job = self.root.after(hide_after_ms, self._show, "hidden", "", None)

    def _animate(self):
        c, r = self.canvas, H // 2
        if self.state == "recording":
            lvl = min(1.0, self.level_fn() * 12)
            for i, b in enumerate(self.bars):
                h = 3 + lvl * (14 - abs(i - 2) * 3)
                x = 128 + i * 6
                c.coords(b, x, r - h / 2, x + 3, r + h / 2)
        else:
            for b in self.bars:
                c.coords(b, 0, 0, 0, 0)
        self.root.after(50, self._animate)
