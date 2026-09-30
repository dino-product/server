"""규약 검사·요약 게시 스크립트의 고정 입력 회귀 검사.

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
summary = load("post_review_summary")


def run_meta(title: str, labels: list[str], body: str, draft: bool = False) -> tuple[int, str]:
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        json.dump({"pull_request": {"title": title, "body": body, "draft": draft,
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


class SummaryTest(unittest.TestCase):
    def test_head_marker_and_prefix_match(self):
        self.assertEqual(summary.head_of("<!-- dino-pr-review head=3362726 -->\n본문"), "3362726")
        self.assertIsNone(summary.head_of("<!-- dino-pr-review -->"))
        self.assertTrue(summary.same_head("3362726", "3362726abcdef0123456789012345678901234567"))
        self.assertFalse(summary.same_head("3362726", "a5384b9"))
        self.assertFalse(summary.same_head(None, "a5384b9"))



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
        ]:
            code, err = run_hook(cmd)
            self.assertEqual(code, 0, err)

    def test_invalid_titles_blocked(self):
        cases = {
            'git commit -m "작업 상세 조회 추가"': "형식",
            'git commit -m "feat(schedule): add work detail"': "한국어",
            'git commit -m "feat(schedule): 작업 상세 조회를 추가한다"': "명사형",
        }
        for cmd, msg in cases.items():
            code, err = run_hook(cmd)
            self.assertEqual(code, 2, cmd)
            self.assertIn(msg, err)

    def test_non_commit_or_unknown_message_passes(self):
        for cmd in ["git status", "git commit", "git commit --amend --no-edit", "echo git commit-tree"]:
            self.assertEqual(run_hook(cmd)[0], 0, cmd)


if __name__ == "__main__":
    unittest.main()
