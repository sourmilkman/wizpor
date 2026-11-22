import pytest
from wizpor.paster import paste_text


def make(clip="old", fail=False):
    log = []
    state = {"clip": clip}

    def get_clip():
        return state["clip"]

    def set_clip(v):
        log.append(("set", v))
        state["clip"] = v

    def send_paste():
        log.append(("paste", state["clip"]))
        if fail:
            raise RuntimeError("boom")

    return log, state, dict(get_clip=get_clip, set_clip=set_clip, send_paste=send_paste, sleep=lambda s: None)


def test_order_and_restore():
    log, state, kw = make()
    paste_text("hello", **kw)
    assert log == [("set", "hello"), ("paste", "hello"), ("set", "old")]
    assert state["clip"] == "old"


def test_restore_on_error():
    log, state, kw = make(fail=True)
    with pytest.raises(RuntimeError):
        paste_text("hello", **kw)
    assert state["clip"] == "old"


def test_empty_noop():
    log, state, kw = make()
    paste_text("", **kw)
    assert log == []
