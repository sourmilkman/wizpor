"""Push-to-talk state machine: hold to talk, double-tap for hands-free."""
from __future__ import annotations

from enum import Enum


class Action(Enum):
    NONE = "none"
    START = "start"
    STOP = "stop"
    CANCEL = "cancel"


class HotkeyMachine:
    def __init__(self, tap_threshold_s: float = 0.3, double_tap_s: float = 0.4):
        self.tap_threshold_s = tap_threshold_s
        self.double_tap_s = double_tap_s
        self._state = "idle"  # idle | hold | toggle | stopping
        self._down_t = 0.0
        self._last_tap_end: float | None = None

    @property
    def recording(self) -> bool:
        return self._state in ("hold", "toggle")

    def down(self, t: float) -> Action:
        if self._state == "idle":
            self._state = "hold"
            self._down_t = t
            return Action.START
        if self._state == "toggle":
            self._state = "stopping"
            return Action.STOP
        return Action.NONE  # auto-repeat while held

    def interrupt(self) -> Action:
        """Another key was pressed. While holding, treat it as a normal shortcut."""
        if self._state == "hold":
            self._state = "stopping"
            self._last_tap_end = None
            return Action.CANCEL
        return Action.NONE

    def up(self, t: float) -> Action:
        if self._state == "stopping":
            self._state = "idle"
            return Action.NONE
        if self._state != "hold":
            return Action.NONE
        if t - self._down_t >= self.tap_threshold_s:
            self._state = "idle"
            self._last_tap_end = None
            return Action.STOP
        if self._last_tap_end is not None and self._down_t - self._last_tap_end <= self.double_tap_s:
            self._state = "toggle"
            self._last_tap_end = None
            return Action.NONE
        self._state = "idle"
        self._last_tap_end = t
        return Action.CANCEL
