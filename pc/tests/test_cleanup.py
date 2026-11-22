import requests
from wizpor.cleanup import CleanResult, build_messages, clean_text
from wizpor.config import Config


class FakeResp:
    def __init__(self, content):
        self._content = content

    def raise_for_status(self):
        pass

    def json(self):
        return {"message": {"role": "assistant", "content": self._content}}


def poster(content=None, exc=None):
    calls = []

    def post(url, json=None, timeout=None):
        calls.append((url, json, timeout))
        if exc:
            raise exc
        return FakeResp(content)

    post.calls = calls
    return post


RAW = "um so add a button that uh says submit no wait save"


def test_normal():
    p = poster("Add a button that says Save.\n")
    r = clean_text(RAW, [], Config(), post=p)
    assert r == CleanResult("Add a button that says Save.", False)
    url, body, timeout = p.calls[0]
    assert url == "http://localhost:11434/api/chat"
    assert body["stream"] is False
    assert body["options"]["temperature"] == 0
    assert body["model"] == "qwen2.5:3b"
    assert timeout == 3.0


def test_connection_error_falls_back():
    r = clean_text(RAW, [], Config(), post=poster(exc=requests.ConnectionError("down")))
    assert r.text == RAW and r.used_fallback and r.error


def test_timeout_falls_back():
    r = clean_text(RAW, [], Config(), post=poster(exc=requests.Timeout("slow")))
    assert r.text == RAW and r.used_fallback


def test_strips_chatty_preamble():
    r = clean_text(RAW, [], Config(), post=poster("Sure! Here's the cleaned text:\nAdd a Save button."))
    assert r.text == "Add a Save button."
    r = clean_text(RAW, [], Config(), post=poster('"Add a Save button."'))
    assert r.text == "Add a Save button."
    r = clean_text(RAW, [], Config(), post=poster("<transcript>Add a Save button.</transcript>"))
    assert r.text == "Add a Save button."


def test_overlong_rejected():
    r = clean_text("make it blue", [], Config(), post=poster("x " * 200))
    assert r.text == "make it blue" and r.used_fallback


def test_empty_reply_rejected():
    r = clean_text(RAW, [], Config(), post=poster("   "))
    assert r.text == RAW and r.used_fallback


def test_words_in_prompt():
    msgs = build_messages("hi", ["Supabase", "useEffect"])
    assert "Supabase" in msgs[0]["content"] and "useEffect" in msgs[0]["content"]
    assert "<transcript>hi</transcript>" in msgs[-1]["content"]


def test_empty_input_no_call():
    p = poster("x")
    assert clean_text("  ", [], Config(), post=p) == CleanResult("", False)
    assert p.calls == []


def test_disabled_no_call():
    p = poster("x")
    cfg = Config(cleanup_enabled=False)
    assert clean_text(RAW, [], cfg, post=p) == CleanResult(RAW, False)
    assert p.calls == []
