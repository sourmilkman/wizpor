"""Paste text at the cursor via the clipboard, then restore the clipboard."""
from __future__ import annotations

import time
from typing import Callable


def paste_text(
    text: str,
    *,
    get_clip: Callable[[], str],
    set_clip: Callable[[str], None],
    send_paste: Callable[[], None],
    sleep: Callable[[float], None] = time.sleep,
    restore_delay: float = 0.15,
) -> None:
    if not text:
        return
    try:
        old = get_clip()
    except Exception:
        old = None
    set_clip(text)
    try:
        sleep(0.03)  # let the clipboard settle before pasting
        send_paste()
        sleep(restore_delay)  # target app reads the clipboard asynchronously
    finally:
        if old is not None:
            set_clip(old)


def default_paste(text: str) -> None:
    import pyperclip
    from pynput.keyboard import Controller, Key

    kb = Controller()

    def send_paste():
        # Release modifiers that may still be held from the hotkey.
        for k in (Key.ctrl_r, Key.alt_r, Key.shift, Key.shift_r):
            try:
                kb.release(k)
            except Exception:
                pass
        with kb.pressed(Key.ctrl):
            kb.press("v")
            kb.release("v")

    paste_text(text, get_clip=pyperclip.paste, set_clip=pyperclip.copy, send_paste=send_paste)
