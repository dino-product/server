#!/usr/bin/env python3
"""PR 제목·라벨·본문 양식의 기계적 규약 검사.

규칙 원본: .claude/skills/dino-pr/references/writing.md, kinds.md,
.claude/skills/dino-issue/references/labels.md, .github/pull_request_template.md.
PR 종류·제목 type 조합, 노션 링크, 종류별 mermaid 다이어그램의 존재, 로컬 리뷰 마커의 head 일치까지만
판정한다. 다이어그램과 코드의 일치·PR 단위·커밋 완결성처럼 맥락 판단이 필요한 항목은 로컬 리뷰
(.claude/skills/dino-review/references/procedure.md)에 맡긴다.

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
REQUIRED_SECTIONS = [
    "## PR 종류", "## 기능 명세", "## 관련 이슈", "## 핵심 다이어그램", "## 구현 내용", "## 기술적 선택", "## 검증",
]
OPTIONAL_SECTIONS = ["## 리뷰 포인트"]
ISSUE_LINK_RE = re.compile(r"\b(Closes|Refs)\s+#\d+\b|(^|\n)\s*없음\s*($|\n)")
NOTION_RE = re.compile(r"https://(www\.|app\.)?notion\.(so|site|com)/\S+|https://\S+\.notion\.site/\S+")
MERMAID_RE = re.compile(r"```mermaid\s*\n\s*(?:%%[^\n]*\n\s*)*(\w+)")
PLACEHOLDER_RE = re.compile(r"\b(TODO|TBD|FIXME|[A-Z]+_PENDING)\b")
REVIEW_MARKER_RE = re.compile(r"<!--\s*dino-review\s+head=(?P<head>[0-9a-f]{40})\b[^>]*-->")
REVIEW_DOC = ".claude/skills/dino-review/references/report.md#marker"
NONE_RE = re.compile(r"(^|\n)\s*없음\s*($|\n)")
ANY_DIAGRAM = {"sequenceDiagram", "flowchart", "graph", "classDiagram", "erDiagram", "stateDiagram"}
# 종류 -> (허용 제목 type, 노션 링크 필수, 허용 다이어그램 또는 None=없음 허용)
KINDS = {
    "유즈케이스": ({"feat"}, True, {"sequenceDiagram"}),
    "모듈 연동": ({"feat"}, True, {"sequenceDiagram"}),
    "호환성 변경": ({"feat", "refactor", "fix"}, False, ANY_DIAGRAM),
    "구조 변경": ({"refactor", "perf"}, False, {"flowchart", "graph", "classDiagram"}),
    "결함 수정": ({"fix"}, False, {"sequenceDiagram"}),
    "유지보수": ({"build", "ci", "docs", "test", "chore", "revert", "investigation"}, False, None),
}
KINDS_DOC = ".claude/skills/dino-pr/references/kinds.md#kinds"


def annotate(level: str, message: str) -> None:
    print(f"::{level}::{message}")


def check_title(title: str, labels: set[str], errors: list[str]) -> str | None:
    m = TITLE_RE.match(title)
    if not m:
        errors.append(
            f"제목 `{title}` 이 `type(scope): 결과` 형식이 아닙니다. 유형은 {', '.join(TYPES)} 중 하나입니다."
        )
        return None
    pr_type = m.group("type")
    type_labels = {l for l in labels if l.startswith("type:")}
    if not type_labels:
        errors.append(
            f"`type:{pr_type}` 라벨이 없습니다. 저장소에 라벨이 없으면 .claude/skills/dino-issue/references/labels.md#저장소-적용 의 명령을 먼저 실행합니다."
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
    return pr_type


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


def check_kind(sections: dict[str, str], pr_type: str | None, errors: list[str]) -> None:
    first = next((l.strip().strip("-*` ") for l in sections.get("## PR 종류", "").splitlines() if l.strip()), "")
    if first not in KINDS:
        errors.append(f"`## PR 종류` 의 첫 줄은 {', '.join(KINDS)} 중 하나입니다(현재 `{first}`). 기준: {KINDS_DOC}")
        return
    kind = first
    types, needs_spec, diagrams = KINDS[kind]
    if pr_type and pr_type not in types:
        errors.append(f"PR 종류 `{kind}` 의 제목 type은 {', '.join(sorted(types))} 중 하나입니다(현재 `{pr_type}`).")
    spec = sections.get("## 기능 명세", "")
    if needs_spec and not NOTION_RE.search(spec):
        errors.append(f"PR 종류 `{kind}` 는 `## 기능 명세` 에 노션 기능명세 링크가 필요합니다.")
    diagram = sections.get("## 핵심 다이어그램", "")
    found = MERMAID_RE.findall(diagram)
    if diagrams is None:
        if diagram.strip() and not found and not NONE_RE.search(diagram):
            errors.append("`## 핵심 다이어그램` 은 mermaid 블록 또는 `없음` 으로 적습니다.")
        return
    if not found:
        errors.append(f"PR 종류 `{kind}` 는 `## 핵심 다이어그램` 에 mermaid 블록({', '.join(sorted(diagrams))})이 필요합니다.")
    elif not any(d in diagrams for d in found):
        errors.append(f"PR 종류 `{kind}` 의 핵심 다이어그램은 {', '.join(sorted(diagrams))} 중 하나를 포함합니다(현재 {found}).")


def check_review_marker(sections: dict[str, str], head: str | None, errors: list[str]) -> None:
    found = REVIEW_MARKER_RE.findall(sections.get("## 검증", ""))
    if not found:
        errors.append(f"`## 검증` 에 로컬 리뷰 마커가 없습니다. PR 전에 dino-review 로 리뷰하고 마커를 남깁니다. 기준: {REVIEW_DOC}")
    elif len(found) > 1:
        errors.append("`## 검증` 에 리뷰 마커가 여러 개입니다. 마지막 리뷰의 마커 하나만 둡니다.")
    elif head and found[0] != head:
        errors.append(
            f"리뷰 마커의 head `{found[0][:7]}` 가 PR head `{head[:7]}` 와 다릅니다. 리뷰 뒤에 커밋이 바뀌었으니 다시 리뷰하고 마커를 갱신합니다."
        )


def check_body(body: str, errors: list[str], pr_type: str | None = None, head: str | None = None) -> None:
    if "<!--" in REVIEW_MARKER_RE.sub("", body):
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
    for h, text in sections.items():
        found = PLACEHOLDER_RE.findall(re.sub(r"```.*?```", "", text, flags=re.S))
        if found:
            errors.append(f"`{h}` 절에 자리표시자 {sorted(set(found))} 가 남아 있습니다. 실제 내용으로 채운 뒤 제출합니다.")
    issues = sections.get("## 관련 이슈", "")
    if issues.strip() and not ISSUE_LINK_RE.search(issues):
        errors.append("`## 관련 이슈` 는 `Closes #번호`, `Refs #번호` 또는 `없음` 으로 적습니다.")
    check_kind(sections, pr_type, errors)
    check_review_marker(sections, head, errors)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", default=os.environ.get("GITHUB_EVENT_PATH"))
    parser.add_argument("--head", help="리뷰 마커와 비교할 head SHA. 없으면 이벤트의 pull_request.head.sha")
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
    head = args.head or (pr.get("head") or {}).get("sha")

    errors: list[str] = []
    pr_type = check_title(title, labels, errors)
    if draft:
        annotate("notice", "Draft PR이라 본문 양식 검사는 건너뜁니다. 제목·라벨만 확인했습니다.")
    else:
        check_body(body, errors, pr_type, head)

    for e in errors:
        annotate("error", e)
    if errors:
        print(f"PR 규약 검사 실패: {len(errors)}건. 기준: .claude/skills/dino-pr/references/writing.md, kinds.md, dino-review/references/report.md")
        return 1
    annotate("notice", "PR 제목·라벨·본문 양식이 규약과 일치합니다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
