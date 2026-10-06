"""규약 검사·리뷰 보고서·커밋 hook 스크립트의 고정 입력 회귀 검사.

실행: python3 -m unittest discover -s .github/scripts/tests
GitHub API 는 호출하지 않는다.
"""
from __future__ import annotations

import importlib.util
import io
import json
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]


def load(name: str):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


meta = load("check_pr_metadata")
links = load("check_markdown_links")
REPORT = SCRIPTS.parents[1] / ".claude" / "skills" / "dino-review" / "scripts" / "review_report.py"
_spec = importlib.util.spec_from_file_location("review_report", REPORT)
report = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(report)

HEAD = "3362726" + "a" * 33
MARKER = f"<!-- dino-review head={HEAD} fix=0 decide=1 business=skip -->"


def run_meta(title: str, labels: list[str], body: str, draft: bool = False, head: str = HEAD) -> tuple[int, str]:
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        json.dump({"pull_request": {"title": title, "body": body, "draft": draft, "head": {"sha": head},
                                    "labels": [{"name": l} for l in labels]}}, f)
    out = io.StringIO()
    argv = sys.argv
    sys.argv = ["check_pr_metadata.py", "--event", f.name]
    try:
        with redirect_stdout(out):
            code = meta.main()
    finally:
        sys.argv = argv
    return code, out.getvalue()


def body(kind: str = "유지보수", spec: str = "없음", diagram: str = "없음") -> str:
    return f"""## PR 종류

{kind}

## 기능 명세

{spec}

## 관련 이슈

없음

## 핵심 다이어그램

{diagram}

## 구현 내용

내용.

## 기술적 선택

없음

## 검증

명령과 결과.

{MARKER}
"""


GOOD_BODY = body()
NOTION = "[작업] 기능명세 §4 https://www.notion.so/3cef190e1166819b9f21ca729f69fc97"
SEQ = "```mermaid\nsequenceDiagram\n    A->>B: call\n```"
FLOW = "```mermaid\nflowchart LR\n    A --> B\n```"


class MetadataTest(unittest.TestCase):
    def test_valid_pr_passes(self):
        code, out = run_meta("ci: 워크플로 추가", ["type:ci"], GOOD_BODY)
        self.assertEqual(code, 0, out)

    def test_title_format_and_label_match(self):
        code, out = run_meta("잘못된 제목", ["type:ci"], GOOD_BODY)
        self.assertEqual(code, 1)
        self.assertIn("형식이 아닙니다", out)
        code, out = run_meta("fix(user): 수정", ["type:ci"], GOOD_BODY)
        self.assertIn("일치하지 않습니다", out)
        code, out = run_meta("fix(user): 수정", [], GOOD_BODY)
        self.assertIn("라벨이 없습니다", out)

    def test_breaking_marker_pairs_with_label(self):
        compat = body("호환성 변경", diagram=FLOW)
        _, out = run_meta("feat(api)!: 계약 변경", ["type:feat"], compat)
        self.assertIn("compatibility:breaking", out)
        code, out = run_meta("feat(api)!: 계약 변경", ["type:feat", "compatibility:breaking"], compat)
        self.assertEqual(code, 0, out)

    def test_body_template_rules(self):
        bad = "<!-- 안내 -->\n## 검증\n\n## PR 종류\n\n## 관련 이슈\n\n뭔가\n## 구현 내용\n"
        code, out = run_meta("ci: 제목", ["type:ci"], bad)
        self.assertEqual(code, 1)
        for msg in ["안내 주석", "절 순서", "비어 있습니다", "Closes #번호", "필수 절이 없습니다"]:
            self.assertIn(msg, out)

    def test_draft_skips_body(self):
        code, out = run_meta("ci: 제목", ["type:ci"], "<!-- 미완성 -->", draft=True)
        self.assertEqual(code, 0, out)
        self.assertIn("Draft", out)

    def test_usecase_requires_notion_and_sequence_diagram(self):
        code, out = run_meta("feat(schedule): 작업 상세 조회 추가", ["type:feat"], body("유즈케이스", NOTION, SEQ))
        self.assertEqual(code, 0, out)
        code, out = run_meta("feat(schedule): 작업 상세 조회 추가", ["type:feat"], body("유즈케이스", "없음", FLOW))
        self.assertEqual(code, 1)
        self.assertIn("노션 기능명세 링크", out)
        self.assertIn("sequenceDiagram", out)

    def test_kind_and_title_type_must_match(self):
        code, out = run_meta("feat(schedule): 구조 정리", ["type:feat"], body("구조 변경", diagram=FLOW))
        self.assertEqual(code, 1)
        self.assertIn("refactor", out)
        code, out = run_meta("refactor(schedule): 잠금 추출", ["type:refactor"], body("구조 변경", diagram=FLOW))
        self.assertEqual(code, 0, out)

    def test_placeholder_rejected(self):
        code, out = run_meta("ci: 제목", ["type:ci"], GOOD_BODY.replace("명령과 결과.", "VERIFICATION_PENDING"))
        self.assertEqual(code, 1)
        self.assertIn("자리표시자", out)

    def test_review_marker_required_and_matches_head(self):
        code, out = run_meta("ci: 제목", ["type:ci"], GOOD_BODY.replace(MARKER, ""))
        self.assertEqual(code, 1)
        self.assertIn("리뷰 마커가 없습니다", out)
        code, out = run_meta("ci: 제목", ["type:ci"], GOOD_BODY, head="b" * 40)
        self.assertEqual(code, 1)
        self.assertIn("다시 리뷰", out)
        code, out = run_meta("ci: 제목", ["type:ci"], GOOD_BODY + MARKER + "\n")
        self.assertIn("여러 개", out)

    def test_review_marker_is_not_template_comment(self):
        code, out = run_meta("ci: 제목", ["type:ci"], GOOD_BODY)
        self.assertEqual(code, 0, out)
        self.assertNotIn("안내 주석", out)

    def test_unknown_or_missing_diagram_rejected(self):
        code, out = run_meta("ci: 제목", ["type:ci"], body("기능 추가"))
        self.assertIn("PR 종류", out)
        code, out = run_meta("refactor: 정리", ["type:refactor"], body("구조 변경"))
        self.assertIn("mermaid 블록", out)


class LinkCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "docs").mkdir()
        (self.root / "docs/a.md").write_text("<a id=\"custom\"></a>\n# 제목 하나\n\n## 읽기 경로\n", encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    def check(self, text: str) -> list[str]:
        md = self.root / "docs/b.md"
        md.write_text(text, encoding="utf-8")
        return links.check_file(md, self.root, {})

    def test_existing_file_and_anchors_pass(self):
        self.assertEqual(self.check("[a](a.md) [b](a.md#custom) [c](a.md#읽기-경로) [d](a.md#제목-하나) [e](https://x.y/)"), [])

    def test_missing_file_and_anchor_fail(self):
        errors = self.check("[a](nope.md)\n[b](a.md#없는-앵커)")
        self.assertEqual(len(errors), 2)
        self.assertIn("line=1", errors[0])
        self.assertIn("앵커가 없습니다", errors[1])

    def test_links_inside_code_fences_ignored(self):
        self.assertEqual(self.check("```\n[a](nope.md)\n```\n"), [])

    def test_all_markdown_skips_gitignored_files(self):
        subprocess.run(["git", "init", "-q", "-b", "main"], cwd=self.root, check=True)
        (self.root / ".gitignore").write_text("drafts/\n", encoding="utf-8")
        (self.root / "drafts").mkdir()
        (self.root / "drafts/local.md").write_text("[a](nope.md)\n", encoding="utf-8")
        (self.root / "docs/new.md").write_text("[a](a.md)\n", encoding="utf-8")
        names = sorted(p.relative_to(self.root).as_posix() for p in links.all_markdown(self.root))
        self.assertEqual(names, ["docs/a.md", "docs/new.md"])

    def test_deleted_markdown_escalates_to_all(self):
        subprocess.run(["git", "init", "-q", "-b", "main"], cwd=self.root, check=True)
        env = {"GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t"}
        (self.root / "docs/b.md").write_text("[a](a.md)\n", encoding="utf-8")
        subprocess.run(["git", "add", "."], cwd=self.root, check=True)
        subprocess.run(["git", "commit", "-qm", "base"], cwd=self.root, check=True, env=env)
        subprocess.run(["git", "rm", "-q", "docs/a.md"], cwd=self.root, check=True)
        subprocess.run(["git", "commit", "-qm", "delete"], cwd=self.root, check=True, env=env)
        files, escalated = links.changed_markdown("HEAD~1", "HEAD", self.root)
        self.assertTrue(escalated)
        self.assertEqual([p.name for p in files], ["b.md"])
        self.assertEqual(len(links.check_file(files[0], self.root, {})), 1)


class ReviewReportTest(unittest.TestCase):
    def result(self, **over) -> dict:
        data = {"base": "origin/develop", "head": HEAD, "kind": "유지보수", "business": "skip",
                "skip_reason": "노션 명세 없음", "findings": [], "reviewed": ["전체 diff"], "unreviewed": []}
        data.update(over)
        return data

    def load(self, data: dict) -> dict:
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        return report.load(Path(f.name))

    def test_valid_result_renders_and_marker_counts(self):
        finding = {"view": "기술", "type": "결함", "action": "수정 필요", "location": "README.md:1",
                   "title": "<b>문제</b>", "basis": "근거", "request": "요청"}
        data = self.load(self.result(findings=[finding]))
        page = report.render(data, Path("r.json"))
        self.assertIn("&lt;b&gt;문제&lt;/b&gt;", page)
        self.assertIn('class="code"', page)
        self.assertEqual(report.marker_for(data), f"<!-- dino-review head={HEAD} fix=1 decide=0 business=skip -->")
        self.assertRegex(report.marker_for(data), meta.REVIEW_MARKER_RE)

    def test_invalid_result_rejected(self):
        for over in [{"head": "abc"}, {"business": "checked"}, {"business": "skip", "skip_reason": ""},
                     {"findings": [{"view": "기술", "type": "명세 불일치", "action": "수정 필요"}]},
                     {"trace": [{"rule": "r", "result": "애매"}]}]:
            with self.assertRaises(SystemExit, msg=over):
                self.load(self.result(**over))

    def test_marker_inserted_into_verification_or_replaced(self):
        text = "## 검증\n\n명령.\n\n## 리뷰 포인트\n\n없음\n"
        once = report.insert_marker(text, MARKER)
        self.assertEqual(once, f"## 검증\n\n명령.\n\n{MARKER}\n\n## 리뷰 포인트\n\n없음\n")
        newer = MARKER.replace("fix=0", "fix=2")
        self.assertEqual(report.insert_marker(once, newer).count("dino-review"), 1)
        self.assertIn("fix=2", report.insert_marker(once, newer))
        with self.assertRaises(ValueError):
            report.insert_marker("## 구현 내용\n", MARKER)


DRAFT = SCRIPTS.parents[1] / ".claude" / "skills" / "dino-pr" / "scripts" / "pr_draft.py"


class PrDraftTest(unittest.TestCase):
    def test_labels_prints_all_front_matter_labels(self):
        with tempfile.TemporaryDirectory() as tmp:
            draft = Path(tmp) / "d.md"
            draft.write_text("---\ntitle: docs: 링크 정리\nlabels: type:docs, area:docs\n---\n## PR 종류\n", encoding="utf-8")
            out = subprocess.run([sys.executable, str(DRAFT), "labels", str(draft)], capture_output=True, text=True)
        self.assertEqual(out.returncode, 0, out.stderr)
        self.assertEqual(out.stdout.strip(), "type:docs,area:docs")


HOOK = SCRIPTS.parents[1] / ".claude" / "hooks" / "check_commit_message.py"


def run_hook(command: str) -> tuple[int, str]:
    proc = subprocess.run([sys.executable, str(HOOK)], input=json.dumps({"tool_input": {"command": command}}),
                          capture_output=True, text=True)
    return proc.returncode, proc.stderr


class CommitHookTest(unittest.TestCase):
    def test_valid_titles_pass(self):
        for cmd in [
            'git commit -m "fix(auth): 다른 발급자의 Access Token 거부"',
            "git commit -q -F - <<'EOF'\nfeat(schedule): 작업 상세 조회 추가\n\n본문.\nEOF",
            'git commit -m "$(cat <<\'EOF\'\ndocs: 링크 정리\nEOF\n)"',
            "python3 - <<'EOF'\nprint(1)\nEOF\ngit add a && git commit -m \"docs: 링크 정리\"",
            "python3 - <<'PY'\nprint(1)\nPY\ngit commit -q -F - <<'EOF'\ndocs: 링크 정리\nEOF",
            "python3 - <<'EOF'\n# x && git commit -m wrong\nEOF\ngit commit -m \"docs: 링크 정리\"",
        ]:
            code, err = run_hook(cmd)
            self.assertEqual(code, 0, err)

    def test_invalid_titles_blocked(self):
        cases = {
            'git commit -m "작업 상세 조회 추가"': "형식",
            'git commit -m "feat(schedule): add work detail"': "한국어",
            'git commit -m "feat(schedule): 작업 상세 조회를 추가한다"': "명사형",
            "python3 - <<'EOF'\nprint(1)\nEOF\ngit commit -m \"작업 상세 조회 추가\"": "형식",
        }
        for cmd, msg in cases.items():
            code, err = run_hook(cmd)
            self.assertEqual(code, 2, cmd)
            self.assertIn(msg, err)

    def test_non_commit_or_unknown_message_passes(self):
        for cmd in ["git status", "git commit", "git commit --amend --no-edit", "echo git commit-tree",
                    "python3 - <<'EOF'\n# x && git commit -m wrong\nEOF"]:
            self.assertEqual(run_hook(cmd)[0], 0, cmd)


if __name__ == "__main__":
    unittest.main()
