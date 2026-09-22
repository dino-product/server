#!/usr/bin/env python3
"""변경된 Markdown 파일의 상대 링크·앵커 실재 검사.

`--base`…`--head` 사이에 추가·수정된 `*.md`(및 `.toml` 지침 파일의 경로 문자열은 제외)를 대상으로,
상대 경로 링크의 파일 존재와 `#앵커`의 실재를 확인한다. 외부 URL은 확인하지 않는다.
앵커는 `<a id="...">`, HTML `id=`, 제목의 GitHub 슬러그를 인정한다.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

LINK_RE = re.compile(r"(?<!!)\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
ANCHOR_ID_RE = re.compile(r"<a\s+(?:id|name)=\"([^\"]+)\"|\bid=\"([^\"]+)\"")
HEADING_RE = re.compile(r"^#{1,6}\s+(.*?)\s*#*\s*$")


def slugify(text: str) -> str:
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)  # 링크 텍스트만
    text = re.sub(r"[`*_~]", "", text).strip().lower()
    text = re.sub(r"[^\w\- ]", "", text)
    return text.replace(" ", "-")


def anchors_of(path: Path, cache: dict[Path, set[str]]) -> set[str]:
    if path in cache:
        return cache[path]
    found: set[str] = set()
    counts: dict[str, int] = {}
    in_fence = False
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip().startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        for m in ANCHOR_ID_RE.finditer(line):
            found.add(m.group(1) or m.group(2))
        h = HEADING_RE.match(line)
        if h:
            slug = slugify(h.group(1))
            n = counts.get(slug, 0)
            counts[slug] = n + 1
            found.add(slug if n == 0 else f"{slug}-{n}")
    cache[path] = found
    return found


def changed_markdown(base: str, head: str, root: Path) -> list[Path]:
    out = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=AMR", f"{base}...{head}", "--", "*.md"],
        check=True, capture_output=True, text=True, cwd=root,
    ).stdout.split()
    return [root / p for p in out if (root / p).exists()]


def check_file(md: Path, root: Path, cache: dict[Path, set[str]]) -> list[str]:
    errors: list[str] = []
    in_fence = False
    for lineno, line in enumerate(md.read_text(encoding="utf-8").splitlines(), 1):
        if line.strip().startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        for m in LINK_RE.finditer(line):
            target = m.group(1)
            if re.match(r"^[a-z][a-z0-9+.-]*:", target):  # http:, mailto: 등
                continue
            path_part, _, anchor = target.partition("#")
            dest = md if not path_part else (md.parent / path_part).resolve()
            rel = md.relative_to(root)
            if path_part and not dest.exists():
                errors.append(f"file={rel},line={lineno}::링크 대상이 없습니다: `{target}`")
                continue
            if anchor:
                if dest.is_dir() or dest.suffix.lower() != ".md":
                    continue
                if anchor not in anchors_of(dest, cache):
                    errors.append(f"file={rel},line={lineno}::앵커가 없습니다: `{target}`")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--all", action="store_true", help="변경 여부와 관계없이 모든 .md 검사")
    args = parser.parse_args()
    root = Path(subprocess.run(["git", "rev-parse", "--show-toplevel"], check=True, capture_output=True, text=True).stdout.strip())
    if args.all:
        files = [p for p in root.rglob("*.md") if "build" not in p.parts and ".git" not in p.parts]
    else:
        if not args.base:
            print("--base 가 필요합니다 (또는 --all).", file=sys.stderr)
            return 2
        files = changed_markdown(args.base, args.head, root)
    cache: dict[Path, set[str]] = {}
    errors: list[str] = []
    for md in sorted(files):
        errors.extend(check_file(md, root, cache))
    for e in errors:
        print(f"::error {e}")
    print(f"검사한 Markdown {len(files)}개, 깨진 링크·앵커 {len(errors)}건")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
