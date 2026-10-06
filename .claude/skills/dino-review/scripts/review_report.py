#!/usr/bin/env python3
"""로컬 리뷰 결과(JSON)를 HTML 보고서로 만들고 PR 본문에 리뷰 마커를 남긴다.

결과 파일은 `.claude/reviews/<브랜치>-<head7>.json`(gitignore)에 둔다. 형식은
.claude/skills/dino-review/references/report.md#result 가 원본이다.

하위 명령
    render <결과.json>                      검증 후 같은 이름의 .html 생성(코드 발췌 포함)
    marker <결과.json> [--draft <초안.md>]  head·작업 트리를 확인하고 마커 출력.
                                            --draft 면 초안 `## 검증` 절에 넣거나 교체
"""
from __future__ import annotations

import argparse
import html
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
VIEWS = ("비즈니스", "기술")
ACTIONS = ("수정 필요", "판단 필요")
TYPES = {
    "비즈니스": ("명세 불일치", "명세 미구현", "명세 없음", "버전 밀림", "차이 장부"),
    "기술": ("결함", "모듈 계약", "컨벤션", "다이어그램", "테스트", "문서", "PR 단위"),
}
TRACE_RESULTS = ("일치", "불일치", "미구현")
MARKER_RE = re.compile(r"<!--\s*dino-review\s+head=[0-9a-f]{40}\b[^>]*-->")
LOCATION_RE = re.compile(r"^(?P<path>[^:\s]+):(?P<line>\d+)(?:-(?P<end>\d+))?$")


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()


def load(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    errors: list[str] = []
    for key in ("base", "head", "kind", "business", "findings", "reviewed", "unreviewed"):
        if key not in data:
            errors.append(f"`{key}` 가 없습니다.")
    if not re.fullmatch(r"[0-9a-f]{40}", str(data.get("head", ""))):
        errors.append("`head` 는 40자 커밋 SHA 입니다.")
    if data.get("business") not in ("checked", "skip"):
        errors.append("`business` 는 checked 또는 skip 입니다.")
    if data.get("business") == "skip" and not data.get("skip_reason"):
        errors.append("`business` 가 skip 이면 `skip_reason` 이 필요합니다.")
    if data.get("business") == "checked" and not data.get("spec"):
        errors.append("`business` 가 checked 면 `spec`(대상 노션 절)이 필요합니다.")
    for row in data.get("trace", []):
        if row.get("result") not in TRACE_RESULTS:
            errors.append(f"trace `{row.get('rule', '?')}` 의 result 는 {', '.join(TRACE_RESULTS)} 중 하나입니다.")
    for i, f in enumerate(data.get("findings", []), 1):
        view = f.get("view")
        if view not in VIEWS:
            errors.append(f"finding {i}: view 는 {', '.join(VIEWS)} 중 하나입니다.")
            continue
        if f.get("type") not in TYPES[view]:
            errors.append(f"finding {i}: {view} 의 type 은 {', '.join(TYPES[view])} 중 하나입니다.")
        if f.get("action") not in ACTIONS:
            errors.append(f"finding {i}: action 은 {', '.join(ACTIONS)} 중 하나입니다.")
        for key in ("location", "title", "basis", "request"):
            if not f.get(key):
                errors.append(f"finding {i}: `{key}` 가 비어 있습니다.")
    if errors:
        sys.exit(f"{path}: 결과 형식 오류\n- " + "\n- ".join(errors))
    return data


def excerpt(location: str, context: int = 3) -> str | None:
    m = LOCATION_RE.match(location)
    if not m:
        return None
    path = ROOT / m.group("path")
    if not path.is_file():
        return None
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    start, end = int(m.group("line")), int(m.group("end") or m.group("line"))
    lo, hi = max(start - context, 1), min(end + context, len(lines))
    rows = []
    for n in range(lo, hi + 1):
        hit = " hit" if start <= n <= end else ""
        rows.append(f'<div class="cl{hit}"><span class="ln">{n}</span><span class="src">{html.escape(lines[n - 1]) or " "}</span></div>')
    return '<div class="code">' + "".join(rows) + "</div>" if rows else None


def short_path(text: str) -> str:
    """`경로:줄`을 파일명은 굵게, 디렉터리는 흐리게 보여 준다. 전체 경로는 title로 남긴다."""
    e = html.escape
    if not text or text.strip() in ("-", "—"):
        return '<span class="none">—</span>'
    head, sep, tail = text.rpartition("/")
    if not sep:
        return f'<span class="path" title="{e(text)}"><b>{e(text)}</b></span>'
    return f'<span class="path" title="{e(text)}"><span class="dir">{e(head)}/</span><b>{e(tail)}</b></span>'


STYLE = """
:root{color-scheme:light;--bg:#f4f5f7;--card:#fff;--fg:#1d2129;--sub:#4b5361;--muted:#7a8291;--line:#e3e6eb;--line2:#d4d8df;
--fix:#c4291c;--fixbg:#fdecea;--decide:#8a5a00;--decidebg:#fff4d6;--ok:#17723a;--okbg:#e3f6e9;--info:#2456c9;--infobg:#e8effd;
--code:#f7f8fa;--hit:#fff1c2;--hitbar:#e0a800}
@media (prefers-color-scheme: dark){:root{color-scheme:dark;--bg:#0f1115;--card:#181b21;--fg:#e8eaed;--sub:#c3c8d1;--muted:#8e96a3;
--line:#272b33;--line2:#323742;--fix:#ff8a80;--fixbg:#3a1c1b;--decide:#f2c14e;--decidebg:#372b0f;--ok:#6fd690;--okbg:#14301f;
--info:#8fb3ff;--infobg:#1a2640;--code:#12141a;--hit:#2f2a14;--hitbar:#c99a1a}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.7 -apple-system,BlinkMacSystemFont,"Apple SD Gothic Neo","Pretendard","Noto Sans KR",sans-serif;
-webkit-font-smoothing:antialiased;word-break:keep-all;overflow-wrap:anywhere}
.wrap{max-width:960px;margin:0 auto;padding:32px 20px 80px}
header h1{font-size:24px;line-height:1.3;margin:0 0 10px;letter-spacing:-.01em}
.chips{display:flex;flex-wrap:wrap;gap:6px}.chip{font-size:13px;color:var(--sub);background:var(--card);border:1px solid var(--line);border-radius:6px;padding:2px 8px}
.chip code{font:12.5px ui-monospace,SFMono-Regular,Menlo,monospace;color:var(--fg)}
.stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:22px 0 8px}
.stat{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px}
.stat .n{font-size:26px;font-weight:700;line-height:1.2}.stat .l{font-size:13px;color:var(--muted)}
.stat.fix .n{color:var(--fix)}.stat.decide .n{color:var(--decide)}.stat.ok .n{color:var(--ok)}
.bar{position:sticky;top:0;z-index:5;background:var(--bg);padding:10px 0;margin-bottom:4px;display:flex;gap:6px;border-bottom:1px solid var(--line)}
.bar button{font:inherit;font-size:13px;color:var(--sub);background:var(--card);border:1px solid var(--line2);border-radius:999px;padding:3px 12px;cursor:pointer}
.bar button[aria-pressed=true]{color:var(--card);background:var(--fg);border-color:var(--fg)}
h2{font-size:19px;margin:36px 0 4px;letter-spacing:-.01em}.lead{color:var(--muted);font-size:14px;margin:0 0 14px}
.specs{display:flex;flex-wrap:wrap;gap:8px;margin:0 0 14px}
.spec{display:inline-flex;align-items:center;gap:8px;background:var(--card);border:1px solid var(--line);border-radius:8px;padding:6px 10px;color:var(--fg);text-decoration:none;font-weight:600;font-size:14px}
.spec:hover{border-color:var(--info)}
.badge{display:inline-block;font-size:12px;font-weight:700;border-radius:6px;padding:1px 8px;white-space:nowrap;line-height:1.7}
.b-fix{color:var(--fix);background:var(--fixbg)}.b-decide{color:var(--decide);background:var(--decidebg)}.b-ok{color:var(--ok);background:var(--okbg)}.b-info{color:var(--info);background:var(--infobg)}
.trace{background:var(--card);border:1px solid var(--line);border-radius:10px;overflow:hidden}
.tr{display:grid;grid-template-columns:72px 1fr;gap:12px;padding:12px 16px;border-top:1px solid var(--line)}.tr:first-child{border-top:0}
.tr .rule{font-weight:600}.tr .src{color:var(--muted);font-size:13px;margin-left:6px;font-weight:400}
.kv{display:grid;grid-template-columns:52px 1fr;gap:2px 10px;font-size:13px;margin-top:4px}.kv dt{color:var(--muted)}.kv dd{margin:0;min-width:0}
.path{font:12.5px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace}.path .dir{color:var(--muted)}.path b{font-weight:600;color:var(--fg)}
.none{color:var(--muted)}
.card{background:var(--card);border:1px solid var(--line);border-left:4px solid var(--line2);border-radius:10px;padding:16px 18px;margin:12px 0}
.card.fix{border-left-color:var(--fix)}.card.decide{border-left-color:var(--decide)}
.card .top{display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-size:13px;color:var(--muted)}
.card h3{font-size:17px;line-height:1.45;margin:8px 0 2px}.card .loc{margin-bottom:10px}
.card p{margin:6px 0 10px;color:var(--sub)}
.req{background:var(--infobg);border-radius:8px;padding:8px 12px;margin-top:10px;font-size:14px}.req b{color:var(--info);margin-right:8px}
.basis{font-size:13.5px;color:var(--sub)}.basis b{color:var(--muted);font-weight:600;margin-right:8px}
.code{margin-top:12px;border:1px solid var(--line);border-radius:8px;background:var(--code);max-height:340px;overflow:auto;
font:12.5px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace;padding:6px 0}
.cl{display:grid;grid-template-columns:48px 1fr;padding-right:12px}.cl .ln{color:var(--muted);text-align:right;padding-right:12px;user-select:none}
.cl .src{white-space:pre-wrap;word-break:break-all}.cl.hit{background:var(--hit);box-shadow:inset 3px 0 var(--hitbar)}
.empty{color:var(--muted);background:var(--card);border:1px dashed var(--line2);border-radius:10px;padding:14px 16px}
ul.plain{margin:6px 0;padding-left:20px;color:var(--sub)}
body.only-fix .card.decide,body.only-decide .card.fix{display:none}
@media (max-width:640px){.stats{grid-template-columns:repeat(2,minmax(0,1fr))}.tr{grid-template-columns:1fr}}
"""

SCRIPT = """
document.querySelectorAll('.bar button').forEach(b => b.addEventListener('click', () => {
  document.querySelectorAll('.bar button').forEach(x => x.setAttribute('aria-pressed', x === b));
  document.body.className = b.dataset.f ? 'only-' + b.dataset.f : '';
}));
"""


def render(data: dict, source: Path) -> str:
    e = html.escape
    findings = list(enumerate(data["findings"], 1))
    n_fix = sum(1 for _, f in findings if f["action"] == "수정 필요")
    n_decide = len(findings) - n_fix
    trace = data.get("trace", [])
    matched = sum(1 for t in trace if t["result"] == "일치")
    out = ['<!doctype html><html lang="ko"><head><meta charset="utf-8">'
           '<meta name="viewport" content="width=device-width, initial-scale=1">'
           f'<title>리뷰 보고서 · {e(data.get("branch") or data["head"][:7])}</title><style>{STYLE}</style></head><body><div class="wrap">']
    out.append('<header><h1>리뷰 보고서</h1><div class="chips">'
               f'<span class="chip">{e(data["kind"])}</span>'
               + (f'<span class="chip"><code>{e(data["branch"])}</code></span>' if data.get("branch") else "")
               + f'<span class="chip"><code>{e(data["base"])}</code> … <code>{e(data["head"][:7])}</code></span>'
               f'<span class="chip">{e(source.name)}</span></div></header>')
    biz = f"{matched}/{len(trace)}" if data["business"] == "checked" and trace else ("확인" if data["business"] == "checked" else "해당 없음")
    out.append('<div class="stats">'
               f'<div class="stat fix"><div class="n">{n_fix}</div><div class="l">수정 필요</div></div>'
               f'<div class="stat decide"><div class="n">{n_decide}</div><div class="l">판단 필요</div></div>'
               f'<div class="stat ok"><div class="n">{e(biz)}</div><div class="l">명세 규칙 일치</div></div>'
               f'<div class="stat"><div class="n">{len(data["unreviewed"])}</div><div class="l">확인 못한 범위</div></div></div>')
    out.append('<nav class="bar"><button data-f="" aria-pressed="true">전체</button>'
               f'<button data-f="fix" aria-pressed="false">수정 필요 {n_fix}</button>'
               f'<button data-f="decide" aria-pressed="false">판단 필요 {n_decide}</button></nav>')

    def card(i: int, f: dict) -> str:
        cls = "fix" if f["action"] == "수정 필요" else "decide"
        code = excerpt(f["location"])
        loc = short_path(f["location"]) if LOCATION_RE.match(f["location"]) else f'<span class="none">{e(f["location"])}</span>'
        return (f'<article class="card {cls}"><div class="top"><span class="badge b-{cls}">{e(f["action"])}</span>'
                f'<span>#{i}</span><span>·</span><span>{e(f["type"])}</span></div>'
                f'<h3>{e(f["title"])}</h3><div class="loc">{loc}</div>'
                + (f'<p>{e(f["detail"])}</p>' if f.get("detail") else "")
                + f'<div class="basis"><b>근거</b>{e(f["basis"])}</div>'
                f'<div class="req"><b>요청</b>{e(f["request"])}</div>'
                + (code or "") + "</article>")

    def cards(view: str) -> str:
        items = [card(i, f) for action in ACTIONS for i, f in findings if f["view"] == view and f["action"] == action]
        return "".join(items) or f'<div class="empty">{view} 지적 없음</div>'

    out.append('<h2>비즈니스</h2><p class="lead">노션 명세의 규칙이 코드 흐름에 그대로 구현됐는지</p>')
    if data["business"] == "skip":
        out.append(f'<div class="empty">해당 없음 — {e(data["skip_reason"])}</div>')
    else:
        out.append('<div class="specs">' + "".join(
            f'<a class="spec" href="{e(s.get("url") or "#")}" target="_blank" rel="noopener">{e(s["doc"])}'
            + (f'<span class="badge {"b-ok" if s["version"] == "최신" else "b-decide"}">{e(s["version"])}</span>' if s.get("version") else "")
            + "</a>" for s in data["spec"]) + "</div>")
        if trace:
            rows = []
            for t in trace:
                cls = {"일치": "b-ok", "불일치": "b-fix", "미구현": "b-decide"}[t["result"]]
                rows.append(f'<div class="tr"><div><span class="badge {cls}">{e(t["result"])}</span></div><div>'
                            f'<div class="rule">{e(t["rule"])}<span class="src">{e(t.get("source", ""))}</span></div>'
                            f'<dl class="kv"><dt>코드</dt><dd>{short_path(t.get("code", ""))}</dd>'
                            f'<dt>테스트</dt><dd>{short_path(t.get("test", ""))}</dd></dl></div></div>')
            out.append('<div class="trace">' + "".join(rows) + "</div>")
        out.append(cards("비즈니스"))
    out.append('<h2>기술</h2><p class="lead">컨벤션 · 모듈 계약 · 결함 · 다이어그램과 코드의 일치</p>')
    out.append(cards("기술"))
    out.append('<h2>검토 범위</h2><ul class="plain">' + "".join(f"<li>{e(r)}</li>" for r in data["reviewed"]) + "</ul>")
    out.append('<h2>확인하지 못한 범위</h2>' + ('<ul class="plain">' + "".join(f"<li>{e(r)}</li>" for r in data["unreviewed"]) + "</ul>"
                                         if data["unreviewed"] else '<div class="empty">없음</div>'))
    out.append(f"</div><script>{SCRIPT}</script></body></html>")
    return "".join(out)


def cmd_render(args) -> int:
    path = Path(args.result).resolve()
    data = load(path)
    out = path.with_suffix(".html")
    out.write_text(render(data, path), encoding="utf-8")
    print(out)
    return 0


def marker_for(data: dict) -> str:
    fix = sum(1 for f in data["findings"] if f["action"] == "수정 필요")
    decide = sum(1 for f in data["findings"] if f["action"] == "판단 필요")
    return f"<!-- dino-review head={data['head']} fix={fix} decide={decide} business={data['business']} -->"


def insert_marker(text: str, marker: str) -> str:
    """초안의 `## 검증` 절 끝에 마커를 넣는다. 이미 있으면 그 자리를 교체한다."""
    if MARKER_RE.search(text):
        return MARKER_RE.sub(marker, text, count=1)
    m = re.search(r"^## 검증\n", text, re.M)
    if not m:
        raise ValueError("`## 검증` 절이 없습니다.")
    nxt = re.search(r"^## ", text[m.end():], re.M)
    end = m.end() + nxt.start() if nxt else len(text)
    section = text[m.end():end].rstrip("\n")
    tail = "\n\n" + text[end:] if nxt else "\n"
    return text[:m.end()] + section + "\n\n" + marker + tail


def review_recorded(head: str, root: Path = ROOT) -> bool:
    """`.claude/hooks/record_review_skill.py`가 이 HEAD에서 dino-review 스킬 실행을 기록했는지."""
    return (root / ".claude" / "reviews" / "invoked" / head).is_file()


def cmd_marker(args) -> int:
    data = load(Path(args.result).resolve())
    head = git("rev-parse", "HEAD")
    if data["head"] != head:
        sys.exit(f"리뷰한 head({data['head'][:7]})와 현재 HEAD({head[:7]})가 다릅니다. 현재 HEAD를 다시 리뷰합니다.")
    if not review_recorded(head):
        sys.exit(f"현재 HEAD({head[:7]})에서 dino-review 스킬을 실행한 기록이 없습니다. "
                 "스킬로 리뷰한 뒤 마커를 남깁니다(결과 JSON만 써서 마커를 만들지 않습니다).")
    dirty = git("status", "--porcelain", "--untracked-files=no")
    if dirty:
        sys.exit("커밋하지 않은 변경이 있어 리뷰한 head와 작업 트리가 다릅니다. 커밋한 뒤 다시 리뷰합니다.\n" + dirty)
    marker = marker_for(data)
    if not args.draft:
        print(marker)
        return 0
    draft = Path(args.draft)
    try:
        text = insert_marker(draft.read_text(encoding="utf-8"), marker)
    except ValueError as e:
        sys.exit(f"{draft}: {e}")
    draft.write_text(text, encoding="utf-8")
    print(f"{draft}: {marker}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("render").add_argument("result")
    p = sub.add_parser("marker")
    p.add_argument("result")
    p.add_argument("--draft")
    args = parser.parse_args()
    return {"render": cmd_render, "marker": cmd_marker}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
