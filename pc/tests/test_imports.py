import importlib

import pytest


@pytest.mark.parametrize("mod", [
    "wizpor", "wizpor.config", "wizpor.history", "wizpor.hotkey", "wizpor.cleanup",
    "wizpor.paster", "wizpor.pipeline", "wizpor.recorder", "wizpor.transcriber",
    "wizpor.overlay", "wizpor.tray", "wizpor.app",
])
def test_imports_without_hardware_libs(mod):
    importlib.import_module(mod)
