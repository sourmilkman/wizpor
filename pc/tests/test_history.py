import json
from wizpor.history import append_history


def test_append(tmp_path):
    p = tmp_path / "sub" / "history.jsonl"
    append_history(p, {"raw": "a"})
    append_history(p, {"raw": "b"})
    lines = p.read_text(encoding="utf-8").splitlines()
    assert len(lines) == 2
    for line in lines:
        assert "ts" in json.loads(line)
