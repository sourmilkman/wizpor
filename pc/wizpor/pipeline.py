"""Audio -> transcript -> tidy-up -> paste -> history."""
from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Callable

from .cleanup import CleanResult


@dataclass
class PipelineResult:
    raw: str
    final: str
    pasted: bool
    used_fallback: bool = False
    timings: dict = field(default_factory=dict)


def run_pipeline(
    audio,
    samplerate: int,
    *,
    raw_mode: bool,
    transcribe: Callable[[object], str],
    clean: Callable[[str], CleanResult],
    paste: Callable[[str], None],
    history: Callable[[dict], None],
    min_seconds: float,
) -> PipelineResult:
    t0 = time.perf_counter()
    if audio is None or len(audio) / samplerate < min_seconds:
        return PipelineResult("", "", False)
    raw = (transcribe(audio) or "").strip()
    t1 = time.perf_counter()
    if not raw:
        return PipelineResult("", "", False, timings={"transcribe_s": t1 - t0})
    if raw_mode:
        res = CleanResult(raw, False)
    else:
        res = clean(raw)
    t2 = time.perf_counter()
    final = res.text or raw
    paste(final)
    t3 = time.perf_counter()
    timings = {
        "transcribe_s": round(t1 - t0, 3),
        "clean_s": round(t2 - t1, 3),
        "total_s": round(t3 - t0, 3),
    }
    history({
        "raw": raw,
        "final": final,
        "raw_mode": raw_mode,
        "used_fallback": res.used_fallback,
        "error": res.error,
        "audio_s": round(len(audio) / samplerate, 2),
        **timings,
    })
    return PipelineResult(raw, final, True, res.used_fallback, timings)
