#!/usr/bin/env python3
"""PostToolUse(Skill) hook: `dino-review` 스킬을 실행한 HEAD를 기록한다.

규칙 원본: .claude/skills/dino-review/references/report.md#marker
`review_report.py marker`는 이 기록이 있는 HEAD에만 리뷰 마커를 만든다. 결과 JSON을
손으로 써서 마커만 남기는(스킬 절차를 건너뛰는) 실수를 막기 위한 것이며, 의도적인 우회까지
막지는 않는다. 기록은 리뷰한 작업 트리의 `.claude/reviews/invoked/<HEAD SHA>`(gitignore)에 둔다.

리뷰한 작업 트리는 같은 저장소(git common dir가 같음)의 작업 트리 중 다음 순서로 고른다.
1. 스킬 인자(`args`)에 앞뒤를 띄워 적은 첫 절대 경로 — 세션 위치와 다른 워크트리를 리뷰할 때
2. 세션의 현재 위치(`cwd`)
3. 프로젝트 디렉터리(`CLAUDE_PROJECT_DIR`)
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
        record(json.load(sys.stdin))
    except Exception:  # noqa: BLE001 — 기록 실패가 도구 실행을 막지 않게 한다.
        pass
    return 0


def record(payload: dict) -> None:
    skill = ((payload.get("tool_input") or {}).get("skill") or "").split(":")[-1]
    if payload.get("tool_name") != "Skill" or skill != SKILL:
        return
    root = review_root(payload)
    if root is None:
        return
    head = git(root, "rev-parse", "HEAD")
    if head is None:
        return
    marker = root / ".claude" / "reviews" / "invoked" / head
    marker.parent.mkdir(parents=True, exist_ok=True)
    marker.write_text(datetime.now(timezone.utc).isoformat() + "\n", encoding="utf-8")


def review_root(payload: dict) -> Path | None:
    """리뷰한 작업 트리의 최상위. 프로젝트와 같은 저장소의 작업 트리만 고른다."""
    project = Path(os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or ".")
    common = git_common_dir(project)
    if common is None:
        return None
    args = (payload.get("tool_input") or {}).get("args") or ""
    candidates = [*paths_in(str(args)), payload.get("cwd"), project]
    for candidate in candidates:
        if not candidate:
            continue
        top = git(Path(candidate), "rev-parse", "--show-toplevel")
        if top is not None and git_common_dir(Path(top)) == common:
            return Path(top)
    return None


def paths_in(text: str) -> list[Path]:
    """인자 문자열에서 존재하는 절대 경로(디렉터리)를 순서대로 고른다."""
    paths = []
    for token in text.split():
        path = Path(os.path.expanduser(token.strip("\"'`,.:;()[]（）「」")))
        if path.is_absolute() and path.is_dir():
            paths.append(path)
    return paths


def git_common_dir(path: Path) -> Path | None:
    common = git(path, "rev-parse", "--path-format=absolute", "--git-common-dir")
    return Path(common).resolve() if common is not None else None


def git(cwd: Path, *args: str) -> str | None:
    try:
        result = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True)
    except OSError:
        return None
    return result.stdout.strip() if result.returncode == 0 else None


if __name__ == "__main__":
    sys.exit(main())
