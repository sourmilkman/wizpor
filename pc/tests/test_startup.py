from wizpor.tray import startup_command, startup_enabled


def test_startup_command_uses_pythonw_and_launcher():
    cmd = startup_command(r"C:\wizpor\pc\.venv\Scripts\python.exe")
    assert "pythonw.exe" in cmd and "launch.pyw" in cmd
    assert cmd.startswith('"') and cmd.count('"') == 4


def test_startup_enabled_false_off_windows():
    assert startup_enabled() is False
