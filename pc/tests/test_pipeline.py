import numpy as np
from wizpor.cleanup import CleanResult
from wizpor.pipeline import run_pipeline


def deps(transcript="um hello world", cleaned="Hello world."):
    calls = {"transcribe": 0, "clean": 0, "paste": [], "history": []}

    def transcribe(audio):
        calls["transcribe"] += 1
        return transcript

    def clean(t):
        calls["clean"] += 1
        return CleanResult(cleaned, False)

    kw = dict(
        transcribe=transcribe,
        clean=clean,
        paste=lambda t: calls["paste"].append(t),
        history=lambda e: calls["history"].append(e),
        min_seconds=0.3,
    )
    return calls, kw


AUDIO = np.zeros(16000, dtype=np.float32)


def test_happy_path():
    calls, kw = deps()
    r = run_pipeline(AUDIO, 16000, raw_mode=False, **kw)
    assert r.raw == "um hello world" and r.final == "Hello world." and r.pasted
    assert calls["paste"] == ["Hello world."]
    assert len(calls["history"]) == 1
    assert set(r.timings) == {"transcribe_s", "clean_s", "total_s"}


def test_too_short():
    calls, kw = deps()
    r = run_pipeline(np.zeros(1600, dtype=np.float32), 16000, raw_mode=False, **kw)
    assert not r.pasted and calls["transcribe"] == 0 and calls["paste"] == []


def test_empty_transcript():
    calls, kw = deps(transcript="  ")
    r = run_pipeline(AUDIO, 16000, raw_mode=False, **kw)
    assert not r.pasted and calls["paste"] == [] and calls["clean"] == 0
    assert calls["history"] == []


def test_raw_mode_skips_clean():
    calls, kw = deps()
    r = run_pipeline(AUDIO, 16000, raw_mode=True, **kw)
    assert calls["clean"] == 0 and calls["paste"] == ["um hello world"]
