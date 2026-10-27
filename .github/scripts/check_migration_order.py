#!/usr/bin/env python3
"""Flyway 마이그레이션 버전 순서 검사 (ADR-005 파일 규칙).

develop 머지가 곧 dev DB 마이그레이션이라(infra 레포 dev 자동 배포), `--base`…`--head` 사이에 추가된
`V{정수}__{설명}.sql`이 base 최신 버전 다음부터 1씩 이어지는지, base에 있던 버전 파일을 고치거나
지우지 않았는지 확인한다. 이름이 규칙에 맞지 않는 파일은 Flyway가 조용히 건너뛰므로 함께 막는다.
결과는 CI가 돈 시점의 base 기준이라, 다른 PR이 그 뒤에 마이그레이션을 머지하면 base를 받아 다시 돌린다.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

MIGRATION_DIR = "src/main/resources/db/migration"
VERSION_RE = re.compile(r"^V([1-9][0-9]*)__[^/]+\.sql$")


def git(root: Path, *args: str) -> list[str]:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True, cwd=root).stdout.splitlines()


def base_versions(base: str, root: Path) -> list[int]:
    names = git(root, "ls-tree", "-r", "--name-only", base, "--", MIGRATION_DIR)
    return sorted(int(m.group(1)) for name in names if (m := VERSION_RE.match(Path(name).name)))


def check(base: str, head: str, root: Path) -> list[str]:
    existing = base_versions(base, root)
    latest = existing[-1] if existing else 0
    errors: list[str] = []
    added: list[tuple[int, str]] = []
    # 이름 변경은 삭제+추가로 본다: 머지된 파일을 옮기는 것도 체크섬 기록과 어긋난다.
    for line in git(root, "diff", "--name-status", "--no-renames", f"{base}...{head}", "--", MIGRATION_DIR):
        status, path = line.split("\t", 1)
        match = VERSION_RE.match(Path(path).name)
        if status == "A":
            if match:
                added.append((int(match.group(1)), path))
            else:
                errors.append(f"file={path}::이름이 `V{{정수}}__{{설명}}.sql` 규칙에 맞지 않아 Flyway가 건너뜁니다")
        elif match:
            errors.append(f"file={path}::머지된 마이그레이션은 고치거나 지우지 않습니다. 새 버전으로 추가하세요")
    for i, (version, path) in enumerate(sorted(added)):
        expected = latest + 1 + i
        if version != expected:
            errors.append(
                f"file={path}::V{version}이 아니라 V{expected}이어야 합니다"
                f" (base 최신 V{latest}, 1씩 증가). base 브랜치를 받아 번호를 맞추세요"
            )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", default="HEAD")
    args = parser.parse_args()
    root = Path(git(Path.cwd(), "rev-parse", "--show-toplevel")[0])
    errors = check(args.base, args.head, root)
    for e in errors:
        print(f"::error {e}")
    print(f"마이그레이션 순서 위반 {len(errors)}건")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
