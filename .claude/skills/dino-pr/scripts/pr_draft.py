#!/usr/bin/env python3
"""로컬 PR 초안 도구. 초안은 `.claude/pr-drafts/<브랜치>.md`(gitignore)에 둔다.

초안 형식: 첫 부분에 front matter, 그 아래가 PR 본문.
    ---
    title: feat(schedule): 작업 상세 조회 추가 (HM-243)
    labels: type:feat
    base: develop
    draft: false
    ---
    ## PR 종류
    ...

하위 명령
    context [--base develop]   base…HEAD 커밋·변경 통계·모듈/계층별 파일 요약
    check <초안>               .github/scripts/check_pr_metadata.py 와 같은 규약으로 검사
    preview <초안>             mermaid까지 렌더링하는 HTML 미리보기(<초안>.html) 생성
    body <초안>                front matter를 뺀 본문 출력(gh pr create --body-file - 용)
"""
from __future__ import annotations

import argparse
import html
import json
import re
import subprocess
import sys
import tempfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
CHECKER = ROOT / ".github" / "scripts" / "check_pr_metadata.py"


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()


def parse(path: Path) -> tuple[dict, str]:
    text = path.read_text(encoding="utf-8")
    m = re.match(r"^---\n(.*?)\n---\n?(.*)$", text, re.S)
    if not m:
        sys.exit(f"{path}: front matter(---)가 없습니다. 모듈 docstring의 형식을 따릅니다.")
    meta: dict = {}
    for line in m.group(1).splitlines():
        if ":" in line:
            key, value = line.split(":", 1)
            meta[key.strip()] = value.strip()
    labels = meta.get("labels", "").strip("[]")
    meta["labels"] = [l.strip().strip("'\"") for l in labels.split(",") if l.strip()]
    meta["draft"] = meta.get("draft", "false").lower() == "true"
    return meta, m.group(2).lstrip("\n")


def cmd_context(args) -> int:
    base = git("merge-base", args.base, "HEAD")
    print(f"base {args.base} ({base[:7]}) … head {git('rev-parse', '--short', 'HEAD')} ({git('branch', '--show-current')})")
    print("\n# 커밋 (오래된 순)")
    print(git("log", "--reverse", "--format=%h %s", f"{base}..HEAD") or "(없음)")
    rows = [l.split("\t") for l in git("diff", "--numstat", f"{base}...HEAD").splitlines() if l]
    added = sum(int(a) for a, _, _ in rows if a.isdigit())
    deleted = sum(int(d) for _, d, _ in rows if d.isdigit())
    print(f"\n# 통계\n{base[:7]}…{git('rev-parse', '--short', 'HEAD')} · 파일 {len(rows)} · +{added}/−{deleted} · "
          f"커밋 {len(git('rev-list', f'{base}..HEAD').split())}")
    groups: dict[str, list[str]] = defaultdict(list)
    for _, _, f in rows:
        m = re.match(r"src/(main|test)/java/com/orbit/([^/]+)/(?:([^/]+)/)?", f)
        key = f"{m.group(1)}:{m.group(2)}/{m.group(3) or ''}" if m else f.split("/")[0]
        groups[key].append(f)
    print("\n# 모듈·계층별 파일")
    for key in sorted(groups):
        print(f"- {key} ({len(groups[key])})")
        for f in groups[key]:
            print(f"    {f}")
    public = [f for _, _, f in rows if re.match(r"src/main/java/com/orbit/[^/]+/[^/]+\.java$", f)]
    if public:
        print("\n# 모듈 루트 파일 변경(공개 계약·package-info 가능성 → 모듈 연동 검토)")
        print("\n".join(f"- {f}" for f in public))
    return 0


def cmd_check(args) -> int:
    meta, body = parse(Path(args.draft))
    event = {"pull_request": {"title": meta.get("title", ""), "body": body, "draft": meta["draft"],
                              "labels": [{"name": l} for l in meta["labels"]]}}
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        json.dump(event, f)
    return subprocess.run([sys.executable, str(CHECKER), "--event", f.name]).returncode


PAGE = """<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>PR 초안 미리보기</title>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.5.1/github-markdown-light.min.css">
<style>body{{background:#f6f8fa;margin:0}}.wrap{{max-width:980px;margin:24px auto;background:#fff;border:1px solid #d0d7de;border-radius:8px;padding:32px}}
.meta{{font:14px -apple-system,sans-serif;color:#57606a;margin-bottom:16px}}.meta b{{color:#1f2328}}.label{{display:inline-block;border-radius:12px;padding:0 8px;background:#ddf4ff;margin-right:4px}}
.mermaid{{background:#fff}}</style></head>
<body><div class="wrap"><div class="meta"><h1 style="margin:0 0 8px;font-size:24px;color:#1f2328">{title}</h1>
<b>{head}</b> → <b>{base}</b> {labels} {draft}</div><article class="markdown-body" id="body"></article></div>
<script id="src" type="text/plain">{body}</script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/marked/12.0.2/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>
<script>
const src = document.getElementById('src').textContent;
const el = document.getElementById('body');
el.innerHTML = marked.parse(src);
el.querySelectorAll('code.language-mermaid').forEach(c => {{
  const d = document.createElement('div'); d.className = 'mermaid'; d.textContent = c.textContent;
  c.parentElement.replaceWith(d);
}});
mermaid.initialize({{startOnLoad: false, securityLevel: 'strict'}});
mermaid.run().then(() => document.body.dataset.rendered = 'ok').catch(e => {{ document.body.dataset.rendered = 'error'; console.error(e); }});
</script></body></html>
"""


def cmd_preview(args) -> int:
    path = Path(args.draft)
    meta, body = parse(path)
    out = path.with_suffix(".html")
    out.write_text(PAGE.format(
        title=html.escape(meta.get("title", "")), base=html.escape(meta.get("base", "develop")),
        head=html.escape(git("branch", "--show-current")),
        labels=" ".join(f'<span class="label">{html.escape(l)}</span>' for l in meta["labels"]),
        draft="· Draft" if meta["draft"] else "", body=body.replace("</script", "<\\/script")), encoding="utf-8")
    print(out)
    return 0


def cmd_body(args) -> int:
    print(parse(Path(args.draft))[1], end="")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("context")
    p.add_argument("--base", default="develop")
    for name in ("check", "preview", "body"):
        sub.add_parser(name).add_argument("draft")
    args = parser.parse_args()
    return {"context": cmd_context, "check": cmd_check, "preview": cmd_preview, "body": cmd_body}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
