"""Download the Whisper model with a visible progress bar (resumable)."""
import sys

from faster_whisper.utils import _MODELS
from huggingface_hub import snapshot_download

PATTERNS = ["config.json", "preprocessor_config.json", "model.bin", "tokenizer.json", "vocabulary.*"]

for name in sys.argv[1:] or ["large-v3-turbo"]:
    repo = _MODELS.get(name, name)
    print(f"  {name} ({repo})", flush=True)
    path = snapshot_download(repo, allow_patterns=PATTERNS)
    print(f"  done: {path}", flush=True)
