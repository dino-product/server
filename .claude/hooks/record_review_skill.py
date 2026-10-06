#!/usr/bin/env python3
"""PostToolUse(Skill) hook: `dino-review` 스킬을 실행한 HEAD를 기록한다.

규칙 원본: .claude/skills/dino-review/references/report.md#marker
`review_report.py marker`는 이 기록이 있는 HEAD에만 리뷰 마커를 만든다. 결과 JSON을
손으로 써서 마커만 남기는(스킬 절차를 건너뛰는) 실수를 막기 위한 것이며, 의도적인 우회까지
막지는 않는다. 기록은 `.claude/reviews/invoked/<HEAD SHA>`(gitignore)에 둔다.
어떤 경우에도 도구 실행을 막지 않는다(항상 종료 코드 0).
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

SKILL = "dino-review"


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        return 0
    skill = ((payload.get("tool_input") or {}).get("skill") or "").split(":")[-1]
    if payload.get("tool_name") != "Skill" or skill != SKILL:
        return 0
    root = Path(os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or ".")
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True)
    if head.returncode != 0:
        return 0
    record = root / ".claude" / "reviews" / "invoked" / head.stdout.strip()
    record.parent.mkdir(parents=True, exist_ok=True)
    record.write_text(datetime.now(timezone.utc).isoformat() + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
