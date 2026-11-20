"""verify_on_stop.py のテスト（標準ライブラリ unittest のみ）。

実行方法（リポジトリのルートで）:
    python3 -m unittest discover -s .claude/hooks/tests -v

hook を実際に subprocess で起動し、Stop の入力 JSON を stdin に渡して
出力（block の JSON か、無出力か）と、./mvnw が呼ばれたかを確認する。

本物の Maven は動かさない。一時ディレクトリを CLAUDE_PROJECT_DIR にして、
./mvnw を「呼ばれた引数を記録し、FAKE_FAIL が空でなければ失敗する」偽物に差し替える。
"""
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent.parent / "verify_on_stop.py"

FAKE_MVNW = """#!/bin/sh
echo "$*" >> "$FAKE_LOG"
if [ -n "$FAKE_FAIL" ]; then
  echo "[ERROR] FAKE FAILURE: $FAKE_FAIL"
  exit 1
fi
echo "[INFO] BUILD SUCCESS"
exit 0
"""


class HookTestCase(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.project = Path(tmp.name).resolve()
        self.log = self.project / "calls.log"
        mvnw = self.project / "mvnw"
        mvnw.write_text(FAKE_MVNW)
        mvnw.chmod(0o755)
        self.write("src/main/java/App.java", "class App {}\n")
        self.write("frontend/src/App.tsx", "export default 1;\n")

    def write(self, rel, text):
        path = self.project / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def run_hook(self, stop_hook_active=False, fail="", extra_env=None, raw_input=None):
        payload = raw_input
        if payload is None:
            payload = json.dumps(
                {
                    "hook_event_name": "Stop",
                    "stop_hook_active": stop_hook_active,
                    "cwd": str(self.project),
                }
            )
        env = {
            **os.environ,
            "CLAUDE_PROJECT_DIR": str(self.project),
            "FAKE_LOG": str(self.log),
            "FAKE_FAIL": fail,
            **(extra_env or {}),
        }
        return subprocess.run(
            [sys.executable, str(SCRIPT)],
            input=payload,
            capture_output=True,
            text=True,
            env=env,
            timeout=10,
        )

    def calls(self):
        """偽の ./mvnw が呼ばれた引数の一覧"""
        if not self.log.exists():
            return []
        return self.log.read_text().splitlines()

    def assert_passed(self, result):
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "")

    def assert_blocked(self, result):
        self.assertEqual(result.returncode, 0, result.stderr)
        out = json.loads(result.stdout)
        self.assertEqual(out["decision"], "block")
        return out["reason"]


class TestRunsVerify(HookTestCase):
    def test_初回は_mvnw_verify_を動かす(self):
        result = self.run_hook()
        self.assert_passed(result)
        self.assertEqual(len(self.calls()), 1)
        self.assertIn("verify", self.calls()[0].split())

    def test_成功したあと変更がなければ動かさない(self):
        self.assert_passed(self.run_hook())
        self.assert_passed(self.run_hook())
        self.assertEqual(len(self.calls()), 1)

    def test_src_を変えたらもう一度動かす(self):
        self.assert_passed(self.run_hook())
        self.write("src/main/java/App.java", "class App { int x; }\n")
        self.assert_passed(self.run_hook())
        self.assertEqual(len(self.calls()), 2)

    def test_frontend_src_を変えたらもう一度動かす(self):
        self.assert_passed(self.run_hook())
        self.write("frontend/src/App.tsx", "export default 2;\n")
        self.assert_passed(self.run_hook())
        self.assertEqual(len(self.calls()), 2)

    def test_src_にファイルを足したらもう一度動かす(self):
        self.assert_passed(self.run_hook())
        self.write("src/test/java/AppTest.java", "class AppTest {}\n")
        self.assert_passed(self.run_hook())
        self.assertEqual(len(self.calls()), 2)

    def test_src_のファイルを消したらもう一度動かす(self):
        self.write("src/main/java/Old.java", "class Old {}\n")
        self.assert_passed(self.run_hook())
        (self.project / "src/main/java/Old.java").unlink()
        self.assert_passed(self.run_hook())
        self.assertEqual(len(self.calls()), 2)

    def test_ファイル名だけ変えてももう一度動かす(self):
        self.assert_passed(self.run_hook())
        (self.project / "src/main/java/App.java").rename(self.project / "src/main/java/Main.java")
        self.assert_passed(self.run_hook())
        self.assertEqual(len(self.calls()), 2)

    def test_src_の外を変えても動かさない(self):
        self.assert_passed(self.run_hook())
        self.write("README.md", "changed\n")
        self.write("frontend/package.json", "{}\n")
        self.assert_passed(self.run_hook())
        self.assertEqual(len(self.calls()), 1)


class TestFailure(HookTestCase):
    def test_失敗したら_block_して出力を返す(self):
        reason = self.assert_blocked(self.run_hook(fail="Tests run: 3, Failures: 1"))
        self.assertIn("./mvnw", reason)
        self.assertIn("FAKE FAILURE: Tests run: 3, Failures: 1", reason)

    def test_失敗したあとは変更がなくてももう一度動かす(self):
        self.assert_blocked(self.run_hook(fail="x"))
        self.assert_passed(self.run_hook())
        self.assertEqual(len(self.calls()), 2)

    def test_失敗したときは全部の出力をログに残し場所を伝える(self):
        reason = self.assert_blocked(self.run_hook(fail="x"))
        log = self.project / "target" / "verify-on-stop.log"
        self.assertIn(str(log), reason)
        self.assertIn("FAKE FAILURE", log.read_text())

    def test_長い出力は末尾だけ返す(self):
        long_fail = "A" * 10000 + "END"
        reason = self.assert_blocked(self.run_hook(fail=long_fail))
        self.assertIn("END", reason)
        self.assertLess(len(reason), 6000)

    def test_タイムアウトしたら_block_する(self):
        # exec を付けないと、時間切れでシェルを止めても sleep が残り、出力を読み終えられない
        (self.project / "mvnw").write_text("#!/bin/sh\nexec sleep 5\n")
        reason = self.assert_blocked(self.run_hook(extra_env={"VERIFY_ON_STOP_TIMEOUT": "1"}))
        self.assertIn("タイムアウト", reason)


class TestStopHookActive(HookTestCase):
    def test_stop_hook_active_なら動かさない(self):
        self.assert_passed(self.run_hook(stop_hook_active=True, fail="x"))
        self.assertEqual(self.calls(), [])


class TestBadInput(HookTestCase):
    def test_入力が壊れていたら止めずに_stderr_に出す(self):
        result = self.run_hook(raw_input="not json")
        self.assertEqual(result.returncode, 1)
        self.assertEqual(result.stdout.strip(), "")
        self.assertIn("verify_on_stop", result.stderr)
        self.assertEqual(self.calls(), [])


if __name__ == "__main__":
    unittest.main()
