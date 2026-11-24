"""format_and_test.py のテスト（標準ライブラリ unittest のみ）。

実行方法（リポジトリのルートで）:
    python3 -m unittest discover -s .claude/hooks/tests -v

hook を実際に subprocess で起動し、PostToolUse の入力 JSON を stdin に渡して
出力（block の JSON か、無出力か）と、呼ばれたコマンドを確認する。

本物の Maven・Prettier・vitest は動かさない。一時ディレクトリを CLAUDE_PROJECT_DIR にして、
./mvnw と npx を「呼ばれた引数を記録し、FAKE_FAIL_ON を含む呼び出しだけ失敗する」偽物に差し替える。
"""
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import fake_maven

SCRIPT = Path(__file__).resolve().parent.parent / "format_and_test.py"

FAKE_COMMAND = """#!/bin/sh
echo "$PWD|$*" >> "$FAKE_LOG"
if [ -n "$FAKE_FAIL_ON" ]; then
  case "$*" in
    *"$FAKE_FAIL_ON"*) echo "FAKE FAILURE: $*"; exit 1 ;;
  esac
fi
exit 0
"""


class HookTestCase(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.project = Path(tmp.name).resolve()
        self.bin = self.project / "fake-bin"
        self.bin.mkdir()
        (self.project / "frontend").mkdir()
        self.log = self.project / "calls.log"
        for path in (self.project / "mvnw", self.bin / "npx"):
            path.write_text(FAKE_COMMAND)
            path.chmod(0o755)

    def run_hook(self, file_path, tool_name="Edit", fail_on="", extra_env=None, under_build=False):
        payload = json.dumps(
            {
                "hook_event_name": "PostToolUse",
                "tool_name": tool_name,
                "tool_input": {"file_path": file_path},
                "cwd": str(self.project),
            }
        )
        env = {
            **os.environ,
            "CLAUDE_PROJECT_DIR": str(self.project),
            "PATH": f"{self.bin}{os.pathsep}{os.environ.get('PATH', '')}",
            "FAKE_LOG": str(self.log),
            "FAKE_FAIL_ON": fail_on,
            **(extra_env or {}),
        }
        command = [sys.executable, str(SCRIPT)]
        if under_build:
            command = fake_maven.under_build(command)
        return subprocess.run(
            command,
            cwd=self.project,
            input=payload,
            capture_output=True,
            text=True,
            env=env,
            timeout=10,
        )

    def calls(self):
        """偽物のコマンドが呼ばれた記録: [(カレントディレクトリ, 引数), ...]"""
        if not self.log.exists():
            return []
        return [tuple(line.split("|", 1)) for line in self.log.read_text().splitlines()]

    def path(self, rel):
        return str(self.project / rel)

    def assertSilent(self, proc):
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        self.assertEqual(proc.stdout.strip(), "", msg=proc.stdout)

    def assertBlockedWith(self, proc, *texts):
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        self.assertTrue(proc.stdout.strip(), msg="失敗が Claude に返されなかった")
        out = json.loads(proc.stdout)
        self.assertEqual(out["decision"], "block")
        for text in texts:
            self.assertIn(text, out["reason"])


# ------------------------------------------------------------------ Java


class TestJava(HookTestCase):
    def test_formats_only_that_file_with_spotless(self):
        for tool in ("Edit", "Write", "MultiEdit"):
            with self.subTest(tool=tool):
                self.log.unlink(missing_ok=True)
                target = self.path("src/main/java/com/example/Foo.java")
                proc = self.run_hook(target, tool_name=tool)
                self.assertSilent(proc)
                self.assertEqual(
                    self.calls(),
                    [
                        (
                            str(self.project),
                            f"--quiet --batch-mode spotless:apply -DspotlessFiles=\\Q{target}\\E",
                        )
                    ],
                )

    def test_test_sources_are_also_formatted(self):
        proc = self.run_hook(self.path("src/test/java/com/example/FooTest.java"))
        self.assertSilent(proc)
        self.assertEqual(len(self.calls()), 1)
        self.assertIn("spotless:apply", self.calls()[0][1])

    def test_does_not_run_tests(self):
        self.run_hook(self.path("src/main/java/com/example/Foo.java"))
        for _, args in self.calls():
            self.assertNotIn("test", args.split())
            self.assertNotIn("vitest", args)

    def test_spotless_failure_is_returned_to_claude(self):
        proc = self.run_hook(self.path("src/main/java/com/example/Foo.java"), fail_on="spotless:apply")
        self.assertBlockedWith(proc, "Spotless", "FAKE FAILURE", "Foo.java")


# ------------------------------------------------------------------ フロント


class TestFrontend(HookTestCase):
    def test_prettier_then_vitest_related(self):
        for rel in ("frontend/src/App.tsx", "frontend/src/lib/util.ts"):
            with self.subTest(file=rel):
                self.log.unlink(missing_ok=True)
                frontend_rel = rel.removeprefix("frontend/")
                proc = self.run_hook(self.path(rel))
                self.assertSilent(proc)
                self.assertEqual(
                    self.calls(),
                    [
                        (str(self.project / "frontend"), f"--no-install prettier --write {frontend_rel}"),
                        (
                            str(self.project / "frontend"),
                            f"--no-install vitest related --run --passWithNoTests {frontend_rel}",
                        ),
                    ],
                )

    def test_test_failure_is_returned_to_claude(self):
        proc = self.run_hook(self.path("frontend/src/App.tsx"), fail_on="vitest")
        self.assertBlockedWith(proc, "vitest related", "FAKE FAILURE", "App.tsx")

    def test_prettier_failure_stops_before_tests(self):
        proc = self.run_hook(self.path("frontend/src/App.tsx"), fail_on="prettier")
        self.assertBlockedWith(proc, "Prettier", "FAKE FAILURE")
        self.assertEqual(len(self.calls()), 1)

    def test_long_output_is_truncated(self):
        # 失敗の出力が長すぎても Claude に返す内容は上限までに収める
        (self.bin / "npx").write_text('#!/bin/sh\nyes "long line of test output" | head -n 5000\nexit 1\n')
        proc = self.run_hook(self.path("frontend/src/App.tsx"))
        self.assertBlockedWith(proc, "省略")
        self.assertLess(len(json.loads(proc.stdout)["reason"]), 5000)

    def test_color_codes_are_removed(self):
        (self.bin / "npx").write_text("#!/bin/sh\nprintf '\\033[36m<h1 />\\033[39m\\n'\nexit 1\n")
        proc = self.run_hook(self.path("frontend/src/App.tsx"))
        self.assertBlockedWith(proc, "<h1 />")
        self.assertNotIn("\x1b", json.loads(proc.stdout)["reason"])

    def test_timeout_is_returned_to_claude(self):
        # 制限を 1 秒に縮め、5 秒待つ偽物の npx がタイムアウトすることを確かめる
        # （exec にして、殺したときに sleep が残って出力のパイプを開いたままにしないようにする）
        (self.bin / "npx").write_text("#!/bin/sh\nexec sleep 5\n")
        proc = self.run_hook(self.path("frontend/src/App.tsx"), extra_env={"FORMAT_AND_TEST_TIMEOUT": "1"})
        self.assertBlockedWith(proc, "Prettier", "1 秒でタイムアウトしました")


# ------------------------------------------------------------------ ほかのビルドとの衝突


class TestOtherBuild(HookTestCase):
    """ほかの ./mvnw verify（手で動かしたもの、pre-push、Stop の hook）とぶつからないようにする。"""

    def assertSkippedWith(self, proc, *texts):
        # 飛ばしたことは、止めずに（block にせず）additionalContext で Claude に伝える
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        self.assertTrue(proc.stdout.strip(), msg="飛ばした理由が Claude に返されなかった")
        out = json.loads(proc.stdout)
        self.assertNotIn("decision", out)
        self.assertEqual(out["hookSpecificOutput"]["hookEventName"], "PostToolUse")
        for text in texts:
            self.assertIn(text, out["hookSpecificOutput"]["additionalContext"])

    def test_frontend_の変更で_ほかのビルドが動いていたら何も動かさず理由を伝える(self):
        other = fake_maven.start_java_build(self, self.project)
        proc = self.run_hook(self.path("frontend/src/App.tsx"))
        self.assertSkippedWith(proc, "ほかの Maven のビルド", str(other.pid), "App.tsx")
        self.assertEqual(self.calls(), [])

    def test_java_の変更で_ほかのビルドが動いていたら何も動かさず理由を伝える(self):
        other = fake_maven.start_mvnw_build(self, self.project)
        proc = self.run_hook(self.path("src/main/java/com/example/Foo.java"))
        self.assertSkippedWith(proc, "ほかの Maven のビルド", str(other.pid), "Foo.java")
        self.assertEqual(self.calls(), [])

    def test_対象外のファイルでは_ほかのビルドが動いていても何も言わない(self):
        fake_maven.start_java_build(self, self.project)
        proc = self.run_hook(self.path("README.md"))
        self.assertSilent(proc)

    def test_自分を動かしたビルドはほかのビルドと見間違えない(self):
        proc = self.run_hook(self.path("frontend/src/App.tsx"), under_build=True)
        self.assertSilent(proc)
        self.assertEqual(len(self.calls()), 2)

    def test_別のディレクトリのビルドは数えない(self):
        elsewhere = tempfile.TemporaryDirectory()
        self.addCleanup(elsewhere.cleanup)
        fake_maven.start_java_build(self, elsewhere.name)
        proc = self.run_hook(self.path("frontend/src/App.tsx"))
        self.assertSilent(proc)
        self.assertEqual(len(self.calls()), 2)


# ------------------------------------------------------------------ 対象外


class TestIgnored(HookTestCase):
    def test_other_files_do_nothing(self):
        for rel in [
            "README.md",
            "pom.xml",
            ".claude/settings.json",
            "frontend/package.json",
            "frontend/vite.config.ts",  # frontend/src の外
            "frontend/src/index.css",
            "frontend/src/App.jsx",
            "docs/Example.java",  # src の外
            "frontend/src/Foo.java",
            "src/main/resources/application.properties",
        ]:
            with self.subTest(file=rel):
                proc = self.run_hook(self.path(rel))
                self.assertSilent(proc)
                self.assertEqual(self.calls(), [])

    def test_files_outside_project_do_nothing(self):
        proc = self.run_hook("/tmp/elsewhere/src/Foo.java")
        self.assertSilent(proc)
        self.assertEqual(self.calls(), [])

    def test_missing_file_path_does_nothing(self):
        proc = self.run_hook("")
        self.assertSilent(proc)
        self.assertEqual(self.calls(), [])

    def test_invalid_input_is_reported_without_blocking(self):
        proc = subprocess.run(
            [sys.executable, str(SCRIPT)], input="not json", capture_output=True, text=True, timeout=10
        )
        self.assertEqual(proc.returncode, 1)
        self.assertIn("解析できませんでした", proc.stderr)
        self.assertEqual(proc.stdout.strip(), "")


if __name__ == "__main__":
    unittest.main()
