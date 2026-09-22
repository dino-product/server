#!/usr/bin/env python3
"""자동 리뷰 요약 댓글을 PR당 하나로 유지한다.

`<!-- dino-pr-review` 마커가 있는 이슈 댓글을 찾아 있으면 본문을 갱신(PATCH)하고 없으면 생성한다.
이 스크립트가 다루는 것은 해당 마커 댓글 하나뿐이며 다른 댓글·리뷰·PR 메타데이터는 건드리지 않는다.
인증은 `gh` CLI의 GH_TOKEN을 사용한다. 본문은 파일이나 표준입력으로 받으며 RUNNER_TEMP가 있으면
`review-summary.md`로 사본을 남긴다.

  --show              기존 요약 댓글 본문을 출력(없으면 빈 출력). --head 를 주면 첫 줄에 SAME_HEAD/OTHER_HEAD/NONE 을 찍는다
  --body-file F | -   이 파일(또는 표준입력) 내용으로 생성/갱신
  --head SHA          본문 마커의 head 와 일치해야 하며, 기존 요약이 같은 head 면 게시하지 않는다
  --dry-run           게시하지 않고 본문과 예정 동작만 출력
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

MARKER = "<!-- dino-pr-review"
HEAD_RE = re.compile(r"<!-- dino-pr-review head=([0-9a-f]{7,40}) -->")


def gh_api(*args: str, input_json: dict | None = None) -> str:
    cmd = ["gh", "api", *args]
    data = json.dumps(input_json).encode() if input_json is not None else None
    if data is not None:
        cmd += ["--input", "-"]
    try:
        return subprocess.run(cmd, check=True, capture_output=True, input=data).stdout.decode()
    except subprocess.CalledProcessError as e:
        sys.stderr.write(e.stderr.decode(errors="replace"))
        raise


def find_summary(repo: str, pr: int) -> dict | None:
    comments = json.loads(gh_api(f"repos/{repo}/issues/{pr}/comments", "--paginate", "--slurp"))
    for page in comments:
        for c in page:
            if MARKER in (c.get("body") or ""):
                return c
    return None


def head_of(body: str | None) -> str | None:
    m = HEAD_RE.search(body or "")
    return m.group(1) if m else None


def same_head(a: str | None, b: str | None) -> bool:
    return bool(a and b and (a.startswith(b) or b.startswith(a)))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--pr", type=int, required=True)
    parser.add_argument("--show", action="store_true")
    parser.add_argument("--body-file")
    parser.add_argument("--head")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not args.repo:
        print("--repo 또는 GITHUB_REPOSITORY 가 필요합니다.", file=sys.stderr)
        return 2

    try:
        existing = find_summary(args.repo, args.pr)
    except subprocess.CalledProcessError as e:
        print(f"기존 요약 조회 실패 (exit {e.returncode})", file=sys.stderr)
        return e.returncode or 1
    existing_head = head_of(existing["body"]) if existing else None

    if args.show:
        if args.head:
            print("NONE" if not existing else ("SAME_HEAD" if same_head(existing_head, args.head) else "OTHER_HEAD"))
        if existing:
            print(existing["body"])
        return 0
    if not args.body_file:
        print("--show 또는 --body-file 이 필요합니다.", file=sys.stderr)
        return 2

    body = sys.stdin.read() if args.body_file == "-" else Path(args.body_file).read_text(encoding="utf-8")
    body_head = head_of(body)
    if not body_head:
        print(f"본문 첫 줄에 `{MARKER} head=<sha> -->` 마커가 있어야 합니다.", file=sys.stderr)
        return 2
    if args.head and not same_head(body_head, args.head):
        print(f"본문 마커 head `{body_head}` 가 --head `{args.head}` 와 다릅니다.", file=sys.stderr)
        return 2
    if os.environ.get("RUNNER_TEMP"):
        Path(os.environ["RUNNER_TEMP"], "review-summary.md").write_text(body, encoding="utf-8")

    if existing and same_head(existing_head, args.head or body_head):
        print(f"같은 head `{existing_head}` 의 요약이 이미 있어 게시하지 않습니다: {existing['html_url']}")
        return 0
    action = "갱신" if existing else "생성"
    if args.dry_run:
        print(f"[dry-run] 요약 댓글 {action} 예정 (PR #{args.pr})\n")
        print(body)
        return 0
    try:
        if existing:
            result = gh_api(f"repos/{args.repo}/issues/comments/{existing['id']}", "--method", "PATCH", input_json={"body": body})
        else:
            result = gh_api(f"repos/{args.repo}/issues/{args.pr}/comments", "--method", "POST", input_json={"body": body})
    except subprocess.CalledProcessError as e:
        print(f"요약 댓글 {action} 실패 (exit {e.returncode})", file=sys.stderr)
        return e.returncode or 1
    print(f"요약 댓글 {action}: {json.loads(result)['html_url']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
