"""guard_dangerous_ops.py のテスト（標準ライブラリ unittest のみ）。

実行方法（リポジトリのルートで）:
    python3 -m unittest discover -s .claude/hooks/tests -v

hook を実際に subprocess で起動し、PreToolUse の入力 JSON を stdin に渡して
出力（deny の JSON か、無出力か）を確認する。
"""
import json
import subprocess
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent.parent / "guard_dangerous_ops.py"


def run_hook(tool_name, tool_input):
    payload = json.dumps({"tool_name": tool_name, "tool_input": tool_input})
    proc = subprocess.run(
        [sys.executable, str(SCRIPT)],
        input=payload,
        capture_output=True,
        text=True,
        timeout=10,
    )
    return proc


class HookTestCase(unittest.TestCase):
    def assertBlocked(self, tool_name, tool_input):
        proc = run_hook(tool_name, tool_input)
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        self.assertTrue(proc.stdout.strip(), msg=f"ブロックされなかった: {tool_input}")
        out = json.loads(proc.stdout)["hookSpecificOutput"]
        self.assertEqual(out["hookEventName"], "PreToolUse")
        self.assertEqual(out["permissionDecision"], "deny")
        self.assertTrue(out["permissionDecisionReason"], msg="理由が空")

    def assertAllowed(self, tool_name, tool_input):
        proc = run_hook(tool_name, tool_input)
        self.assertEqual(proc.returncode, 0, msg=proc.stderr)
        self.assertEqual(proc.stdout.strip(), "", msg=f"誤ってブロックされた: {tool_input}\n{proc.stdout}")

    def assertBashBlocked(self, command):
        with self.subTest(command=command):
            self.assertBlocked("Bash", {"command": command})

    def assertBashAllowed(self, command):
        with self.subTest(command=command):
            self.assertAllowed("Bash", {"command": command})


# ------------------------------------------------------------------ 既存のブロック


class TestCurrentlyBlocked(HookTestCase):
    def test_rm_recursive_force_variants(self):
        for cmd in [
            "rm -rf build",
            "rm -fr build",
            "rm -r -f build",
            "rm -Rf build",
            "rm --recursive --force build",
            "sudo rm -rf /tmp/x",
            "/bin/rm -rf build",
            "FOO=1 rm -rf build",
            "npm test && rm -rf dist",
            "ls\nrm -rf dist",
            'bash -c "rm -rf build"',
        ]:
            self.assertBashBlocked(cmd)

    def test_git_push_force_variants(self):
        for cmd in [
            "git push --force",
            "git push -f origin main",
            "git push -uf origin main",
            "git push --force-with-lease",
            "git -C repo push --force",
        ]:
            self.assertBashBlocked(cmd)

    def test_git_reset_hard(self):
        for cmd in ["git reset --hard", "git reset --hard HEAD~1", "npm test; git reset --hard"]:
            self.assertBashBlocked(cmd)

    def test_env_via_file_tools(self):
        self.assertBlocked("Read", {"file_path": "/repo/.env"})
        self.assertBlocked("Read", {"file_path": "/repo/.env.local"})
        self.assertBlocked("Write", {"file_path": ".env", "content": "X=1"})
        self.assertBlocked("Edit", {"file_path": "config/.env.production", "old_string": "a", "new_string": "b"})
        self.assertBlocked("MultiEdit", {"file_path": ".env.test", "edits": []})
        self.assertBlocked("Grep", {"pattern": "KEY", "path": ".env"})

    def test_env_via_bash(self):
        for cmd in [
            "cat .env",
            "cat ./config/.env.local",
            "echo X=1 >> .env",
            "docker run --env-file=.env app",
            "source .env",
            "cp .env.example .env",
        ]:
            self.assertBashBlocked(cmd)


# ------------------------------------------------------------------ すり抜けケース


class TestBypassCases(HookTestCase):
    def test_git_push_plus_refspec(self):
        for cmd in [
            "git push origin +main",
            "git push origin +HEAD:main",
            "git push origin main +feature",
        ]:
            self.assertBashBlocked(cmd)

    def test_git_clean_force(self):
        for cmd in [
            "git clean -f",
            "git clean -fd",
            "git clean -fdx",
            "git clean -xdf",
            "git clean --force",
            "git clean -d -f",
        ]:
            self.assertBashBlocked(cmd)

    def test_find_delete(self):
        for cmd in [
            "find . -name '*.log' -delete",
            "find /tmp/x -type f -delete",
        ]:
            self.assertBashBlocked(cmd)

    def test_find_exec_rm_rf(self):
        for cmd in [
            "find . -name node_modules -exec rm -rf {} \\;",
            "find . -name node_modules -exec rm -rf {} +",
            "find . -type d -execdir rm -rf {} \\;",
        ]:
            self.assertBashBlocked(cmd)

    def test_eval(self):
        for cmd in [
            'eval "rm -rf x"',
            "eval 'git push --force'",
            "eval rm -rf x",
        ]:
            self.assertBashBlocked(cmd)


# ------------------------------------------------------------------ 許可すべきケース


class TestAllowed(HookTestCase):
    def test_env_example_file_tools(self):
        self.assertAllowed("Read", {"file_path": "/repo/.env.example"})
        self.assertAllowed("Write", {"file_path": ".env.example", "content": "X="})
        self.assertAllowed("Edit", {"file_path": "config/.env.example", "old_string": "a", "new_string": "b"})

    def test_env_example_bash(self):
        for cmd in ["cat .env.example", "echo X= >> .env.example", "cp .env.example config/.env.example"]:
            self.assertBashAllowed(cmd)

    def test_similar_but_not_env_files(self):
        self.assertAllowed("Read", {"file_path": "src/environment.ts"})
        self.assertAllowed("Read", {"file_path": "docs/env.md"})
        self.assertAllowed("Read", {"file_path": ".envrc"})

    def test_normal_rm(self):
        for cmd in ["rm file.txt", "rm -f file.txt", "rm -r build", "rm -i -r build"]:
            self.assertBashAllowed(cmd)

    def test_normal_git(self):
        for cmd in [
            "git push",
            "git push origin main",
            "git push -u origin feature/x",
            "git reset --soft HEAD~1",
            "git reset HEAD file.txt",
            "git clean -n",
            "git clean --dry-run -d",
            "git status && git log --oneline",
            "git commit -m 'fix: remove -f flag handling'",
        ]:
            self.assertBashAllowed(cmd)

    def test_normal_find(self):
        for cmd in [
            "find . -name '*.py'",
            "find . -name '*.py' -exec grep -l TODO {} \\;",
            "find . -type f -print",
        ]:
            self.assertBashAllowed(cmd)

    def test_other_tools_ignored(self):
        self.assertAllowed("Glob", {"pattern": "**/*.ts"})
        self.assertAllowed("Bash", {"command": "ls -la"})


if __name__ == "__main__":
    unittest.main()
