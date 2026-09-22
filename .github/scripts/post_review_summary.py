#!/usr/bin/env python3
"""자동 리뷰 요약 댓글을 PR당 하나로 유지한다.

`<!-- dino-pr-review` 마커가 있는 이슈 댓글을 찾아 있으면 본문을 갱신(PATCH)하고 없으면 생성한다.
이 스크립트가 다루는 것은 해당 마커 댓글 하나뿐이며 다른 댓글·리뷰·PR 메타데이터는 건드리지 않는다.
인증은 `gh` CLI의 GH_TOKEN을 사용한다.

  --show       기존 요약 댓글 본문을 출력(없으면 빈 출력, 종료 코드 0)
  --body-file  이 파일 내용으로 생성/갱신
  --dry-run    게시하지 않고 본문과 예정 동작만 출력
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

MARKER = "<!-- dino-pr-review"


def gh_api(*args: str, input_json: dict | None = None) -> str:
    cmd = ["gh", "api", *args]
    data = json.dumps(input_json).encode() if input_json is not None else None
    if data is not None:
        cmd += ["--input", "-"]
    return subprocess.run(cmd, check=True, capture_output=True, input=data).stdout.decode()


def find_summary(repo: str, pr: int) -> dict | None:
    comments = json.loads(gh_api(f"repos/{repo}/issues/{pr}/comments", "--paginate", "--slurp"))
    for page in comments:
        for c in page:
            if MARKER in (c.get("body") or ""):
                return c
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--pr", type=int, required=True)
    parser.add_argument("--show", action="store_true")
    parser.add_argument("--body-file")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not args.repo:
        print("--repo 또는 GITHUB_REPOSITORY 가 필요합니다.", file=sys.stderr)
        return 2

    existing = find_summary(args.repo, args.pr)
    if args.show:
        if existing:
            print(existing["body"])
        return 0
    if not args.body_file:
        print("--show 또는 --body-file 이 필요합니다.", file=sys.stderr)
        return 2
    body = Path(args.body_file).read_text(encoding="utf-8")
    if MARKER not in body:
        print(f"본문 첫 줄에 `{MARKER} head=<sha> -->` 마커가 있어야 합니다.", file=sys.stderr)
        return 2
    action = "갱신" if existing else "생성"
    if args.dry_run:
        print(f"[dry-run] 요약 댓글 {action} 예정 (PR #{args.pr})\n")
        print(body)
        return 0
    if existing:
        result = gh_api(f"repos/{args.repo}/issues/comments/{existing['id']}", "--method", "PATCH", input_json={"body": body})
    else:
        result = gh_api(f"repos/{args.repo}/issues/{args.pr}/comments", "--method", "POST", input_json={"body": body})
    print(f"요약 댓글 {action}: {json.loads(result)['html_url']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
