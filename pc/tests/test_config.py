from wizpor.config import Config, load_config, load_words


def test_default_config_created_and_loaded(tmp_path):
    p = tmp_path / "config.toml"
    cfg = load_config(p)
    assert p.exists()
    assert cfg == Config()
    assert load_config(p) == Config()


def test_override_read(tmp_path):
    p = tmp_path / "config.toml"
    p.write_text('ollama_model = "llama3.2:3b"\nunknown_key = 1\n', encoding="utf-8")
    cfg = load_config(p)
    assert cfg.ollama_model == "llama3.2:3b"
    assert cfg.hotkey == "ctrl_r"


def test_malformed_falls_back(tmp_path):
    p = tmp_path / "config.toml"
    p.write_text("this is = = not toml", encoding="utf-8")
    assert load_config(p) == Config()


def test_words(tmp_path):
    p = tmp_path / "words.txt"
    words = load_words(p)
    assert p.exists() and isinstance(words, list)
    p.write_text("# comment\n\nuseEffect\n  Tailwind  \n", encoding="utf-8")
    assert load_words(p) == ["useEffect", "Tailwind"]
