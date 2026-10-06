#!/usr/bin/env python3
"""PreToolUse(Bash) hook: `git commit` 제목을 팀 규칙으로 검사한다.

규칙 원본: .claude/skills/dino-commit/references/commits.md#commits
제목 형식(type·scope·!)은 PR 규약 검사와 같은 정규식을 재사용한다.
메시지를 명령에서 읽을 수 없으면(-F 파일 없음, 편집기 사용 등) 판단하지 않고 통과시킨다.
위반이면 종료 코드 2와 stderr로 Claude에게 이유를 돌려주어 명령을 막는다.
"""
from __future__ import annotations

import json
import re
import shlex
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / ".github" / "scripts"))
from check_pr_metadata import TITLE_RE, TYPES  # noqa: E402

RULE = ".claude/skills/dino-commit/references/commits.md#commits"
COMMIT_RE = re.compile(r"(^|[;&|(\s])git(\s+-C\s+\S+)?\s+commit\b")
HEREDOC_RE = re.compile(r"<<-?\s*['\"]?(\w+)['\"]?[^\n]*\n(.*?)\n\s*\1\b", re.S)
HANGUL_RE = re.compile(r"[가-힣]")


def find_commit(command: str) -> re.Match | None:
    """heredoc 본문(예: python3 - <<'EOF' 안의 문자열) 밖에 있는 첫 `git commit`."""
    bodies = [(m.start(2), m.end(2)) for m in HEREDOC_RE.finditer(command)]
    for m in COMMIT_RE.finditer(command):
        if not any(start <= m.start() < end for start, end in bodies):
            return m
    return None


def extract_message(command: str) -> str | None:
    # 같은 명령의 앞선 heredoc을 메시지로 읽지 않도록 실제 `git commit`부터 본다.
    commit = find_commit(command)
    if commit:
        command = command[commit.start():].lstrip(";&|( \t\n")
    heredoc = HEREDOC_RE.search(command)
    if heredoc:
        return heredoc.group(2)
    try:
        tokens = shlex.split(command.split("\n", 1)[0], posix=True)
    except ValueError:
        return None
    for i, tok in enumerate(tokens):
        if tok in ("-m", "--message") and i + 1 < len(tokens):
            return tokens[i + 1]
        if tok.startswith("--message="):
            return tok.split("=", 1)[1]
        if tok.startswith("-m") and len(tok) > 2 and not tok.startswith("--"):
            return tok[2:]
        if tok in ("-F", "--file") and i + 1 < len(tokens) and tokens[i + 1] != "-":
            path = Path(tokens[i + 1])
            return path.read_text(encoding="utf-8") if path.is_file() else None
    return None


def problems(title: str) -> list[str]:
    found = []
    if not TITLE_RE.match(title):
        found.append(f"제목은 `type(scope): 한국어 변경 요약` 형식입니다. type은 {', '.join(TYPES)} 중 하나입니다.")
    if not HANGUL_RE.search(title):
        found.append("변경 요약은 한국어로 씁니다.")
    if re.search(r"(다|요)[.!]?$", title.strip()):
        found.append("제목은 `추가`, `차단`, `검증` 같은 명사형으로 끝냅니다(`~한다`, `~합니다` 금지).")
    return found


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        return 0
    command = (payload.get("tool_input") or {}).get("command") or ""
    if not find_commit(command) or "--no-edit" in command:
        return 0
    message = extract_message(command)
    if message is None:
        return 0
    title = next((line.strip() for line in message.splitlines() if line.strip()), "")
    errors = problems(title)
    if not errors:
        return 0
    print(f"커밋 제목 `{title}` 이 팀 규칙과 다릅니다 ({RULE}):", file=sys.stderr)
    for e in errors:
        print(f"- {e}", file=sys.stderr)
    print("예: `fix(auth): 다른 발급자의 Access Token 거부`", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
