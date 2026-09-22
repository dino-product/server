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


GOOD_BODY = """## 변경 목적과 결과

목적.

## 관련 이슈

없음

## 주요 변경

내용.

## 검증

명령과 결과.
"""


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
        _, out = run_meta("feat(api)!: 계약 변경", ["type:feat"], GOOD_BODY)
        self.assertIn("compatibility:breaking", out)
        code, _ = run_meta("feat(api)!: 계약 변경", ["type:feat", "compatibility:breaking"], GOOD_BODY)
        self.assertEqual(code, 0)

    def test_body_template_rules(self):
        bad = "<!-- 안내 -->\n## 검증\n\n## 변경 목적과 결과\n\n## 관련 이슈\n\n뭔가\n## 주요 변경\n"
        code, out = run_meta("ci: 제목", ["type:ci"], bad)
        self.assertEqual(code, 1)
        for msg in ["안내 주석", "절 순서", "비어 있습니다", "Closes #번호"]:
            self.assertIn(msg, out)

    def test_draft_skips_body(self):
        code, out = run_meta("ci: 제목", ["type:ci"], "<!-- 미완성 -->", draft=True)
        self.assertEqual(code, 0, out)
        self.assertIn("Draft", out)


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


if __name__ == "__main__":
    unittest.main()
