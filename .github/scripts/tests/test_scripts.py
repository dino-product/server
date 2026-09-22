"""링크 검사·요약 게시 스크립트의 고정 입력 회귀 검사.

실행: python3 -m unittest discover -s .github/scripts/tests
GitHub API 는 호출하지 않는다.
"""
from __future__ import annotations

import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]


def load(name: str):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


links = load("check_markdown_links")
summary = load("post_review_summary")


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
