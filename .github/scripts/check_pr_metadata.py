#!/usr/bin/env python3
"""PR 제목·라벨·본문 양식의 기계적 규약 검사.

규칙 원본: docs/conventions/workflow/pull-requests/writing.md, labels.md,
.github/pull_request_template.md. 크기(약 500줄)·커밋 완결성처럼 맥락 판단이 필요한
항목은 검사하지 않고 리뷰에 맡긴다.

입력: GITHUB_EVENT_PATH의 pull_request 이벤트 JSON (또는 --event 파일).
출력: GitHub annotation(::error::) + 실패 시 종료 코드 1.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

TYPES = (
    "feat", "fix", "docs", "refactor", "perf", "test", "build",
    "ci", "chore", "revert", "investigation",
)
TITLE_RE = re.compile(
    r"^(?P<type>" + "|".join(TYPES) + r")(\((?P<scope>[a-z0-9][a-z0-9._/-]*)\))?(?P<bang>!)?: (?P<subject>\S.*)$"
)
REQUIRED_SECTIONS = ["## 변경 목적과 결과", "## 관련 이슈", "## 주요 변경", "## 검증"]
OPTIONAL_SECTIONS = ["## 영향과 리뷰 포인트"]
ISSUE_LINK_RE = re.compile(r"\b(Closes|Refs)\s+#\d+\b|(^|\n)\s*없음\s*($|\n)")


def annotate(level: str, message: str) -> None:
    print(f"::{level}::{message}")


def check_title(title: str, labels: set[str], errors: list[str]) -> None:
    m = TITLE_RE.match(title)
    if not m:
        errors.append(
            f"제목 `{title}` 이 `type(scope): 결과` 형식이 아닙니다. 유형은 {', '.join(TYPES)} 중 하나입니다."
        )
        return
    pr_type = m.group("type")
    type_labels = {l for l in labels if l.startswith("type:")}
    if not type_labels:
        errors.append(
            f"`type:{pr_type}` 라벨이 없습니다. 저장소에 라벨이 없으면 docs/conventions/workflow/labels.md#저장소-적용 의 명령을 먼저 실행합니다."
        )
    elif type_labels != {f"type:{pr_type}"}:
        errors.append(
            f"제목 유형 `{pr_type}` 과 라벨 {sorted(type_labels)} 이 일치하지 않습니다. `type:*` 라벨은 제목 유형과 같은 것 하나만 둡니다."
        )
    breaking = "compatibility:breaking" in labels
    if m.group("bang") and not breaking:
        errors.append("제목의 `!` 는 `compatibility:breaking` 라벨과 함께 사용합니다.")
    if breaking and not m.group("bang"):
        errors.append("`compatibility:breaking` 라벨이 있으면 제목을 `type(scope)!:` 형식으로 표시합니다.")


def split_sections(body: str) -> dict[str, str]:
    sections: dict[str, str] = {}
    current = None
    for line in body.splitlines():
        if line.startswith("## "):
            current = line.strip()
            sections[current] = ""
        elif current:
            sections[current] += line + "\n"
    return sections


def check_body(body: str, errors: list[str]) -> None:
    if "<!--" in body:
        errors.append("본문에 양식 안내 주석(`<!-- -->`)이 남아 있습니다. 제출 전에 삭제합니다.")
    sections = split_sections(body)
    order = [h for h in sections if h in REQUIRED_SECTIONS + OPTIONAL_SECTIONS]
    missing = [h for h in REQUIRED_SECTIONS if h not in sections]
    if missing:
        errors.append(f"필수 절이 없습니다: {', '.join(missing)}")
    expected = [h for h in REQUIRED_SECTIONS + OPTIONAL_SECTIONS if h in sections]
    if order != expected:
        errors.append(f"절 순서가 양식과 다릅니다. 현재 {order}, 기대 {expected}")
    for h in REQUIRED_SECTIONS:
        if h in sections and not sections[h].strip():
            errors.append(f"`{h}` 절이 비어 있습니다.")
    issues = sections.get("## 관련 이슈", "")
    if issues.strip() and not ISSUE_LINK_RE.search(issues):
        errors.append("`## 관련 이슈` 는 `Closes #번호`, `Refs #번호` 또는 `없음` 으로 적습니다.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", default=os.environ.get("GITHUB_EVENT_PATH"))
    args = parser.parse_args()
    if not args.event:
        print("GITHUB_EVENT_PATH 또는 --event 가 필요합니다.", file=sys.stderr)
        return 2
    event = json.loads(Path(args.event).read_text(encoding="utf-8"))
    pr = event["pull_request"]
    title = pr["title"].strip()
    body = pr.get("body") or ""
    labels = {l["name"] for l in pr.get("labels", [])}
    draft = bool(pr.get("draft"))

    errors: list[str] = []
    check_title(title, labels, errors)
    if draft:
        annotate("notice", "Draft PR이라 본문 양식 검사는 건너뜁니다. 제목·라벨만 확인했습니다.")
    else:
        check_body(body, errors)

    for e in errors:
        annotate("error", e)
    if errors:
        print(f"PR 규약 검사 실패: {len(errors)}건. 기준: docs/conventions/workflow/pull-requests/writing.md")
        return 1
    annotate("notice", "PR 제목·라벨·본문 양식이 규약과 일치합니다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
