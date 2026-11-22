from wizpor.hotkey import Action, HotkeyMachine


def m():
    return HotkeyMachine(tap_threshold_s=0.3, double_tap_s=0.4)


def test_hold():
    h = m()
    assert h.down(0.0) == Action.START
    assert h.recording
    assert h.up(1.0) == Action.STOP
    assert not h.recording


def test_autorepeat_ignored():
    h = m()
    assert h.down(0.0) == Action.START
    assert h.down(0.05) == Action.NONE
    assert h.down(0.10) == Action.NONE
    assert h.up(1.0) == Action.STOP


def test_single_tap_cancels():
    h = m()
    assert h.down(0.0) == Action.START
    assert h.up(0.1) == Action.CANCEL
    assert not h.recording


def test_double_tap_toggle():
    h = m()
    assert h.down(0.0) == Action.START
    assert h.up(0.1) == Action.CANCEL
    assert h.down(0.3) == Action.START
    assert h.up(0.4) == Action.NONE
    assert h.recording
    assert h.down(5.0) == Action.STOP
    assert h.up(5.1) == Action.NONE
    assert not h.recording
    # back to normal hold afterwards
    assert h.down(6.0) == Action.START
    assert h.up(7.0) == Action.STOP


def test_slow_taps_both_cancel():
    h = m()
    h.down(0.0)
    assert h.up(0.1) == Action.CANCEL
    h.down(1.1)
    assert h.up(1.2) == Action.CANCEL


def test_other_key_while_holding_cancels():
    # e.g. Right Ctrl + C used as a normal shortcut
    h = m()
    h.down(0.0)
    assert h.interrupt() == Action.CANCEL
    assert not h.recording
    assert h.up(1.0) == Action.NONE


def test_other_key_in_toggle_mode_ignored():
    h = m()
    h.down(0.0); h.up(0.1); h.down(0.3); h.up(0.4)
    assert h.interrupt() == Action.NONE
    assert h.recording
