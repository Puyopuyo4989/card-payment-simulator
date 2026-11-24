#!/usr/bin/env python3
"""Claude Code PostToolUse hook: 変更したファイルをフォーマットし、フロントは関係するテストを動かす。

対象:
  - src/**/*.java
      そのファイルだけを Spotless（spotless:apply）でフォーマットする。
      テストは動かさない（Stop の hook の verify_on_stop.py で動かす）。
  - frontend/src/**/*.ts, frontend/src/**/*.tsx
      そのファイルを Prettier でフォーマットし、
      vitest related でそのファイルに関係するテストだけを動かす。
  - それ以外のファイルでは何もしない。

ほかの Maven のビルド（verify、pre-push、Stop の hook など）が動いているとき:
  何も動かさず、その理由を additionalContext で Claude に伝える（other_builds.py）。

テスト: python3 -m unittest discover -s .claude/hooks/tests -v

入力: stdin に PostToolUse の hook 入力 JSON
出力: フォーマットかテストが失敗したときは decision=block と失敗の内容を含む JSON を
      stdout に出して exit 0（内容は Claude に返され、Claude が直せる）。
      成功したとき・対象外のときは何も出力せず exit 0。
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath

import other_builds

# Claude に返す出力の上限（長すぎると読みにくいので末尾だけ残す）
MAX_OUTPUT_CHARS = 4000

# コマンド 1 つの制限（秒）。settings.json の hook の timeout（60 秒）に 2 つ分が収まるようにする。
# テストで短くできるよう、環境変数 FORMAT_AND_TEST_TIMEOUT で上書きできる
COMMAND_TIMEOUT = float(os.environ.get("FORMAT_AND_TEST_TIMEOUT", "25"))

# NO_COLOR を無視して色を付けるツール（Testing Library など）があるので、出力から取り除く
ANSI_ESCAPE_RE = re.compile(r"\x1b\[[0-9;]*m")


def classify(file_path: str, project_dir: str):
    """変更したファイルの種類を返す: "java" / "frontend" / None（対象外）。"""
    if not isinstance(file_path, str) or not file_path:
        return None
    try:
        rel = Path(file_path).resolve().relative_to(Path(project_dir).resolve())
    except ValueError:  # プロジェクトの外のファイル
        return None
    parts = PurePosixPath(rel.as_posix()).parts
    suffix = rel.suffix
    if len(parts) >= 2 and parts[0] == "src" and suffix == ".java":
        return "java"
    if len(parts) >= 3 and parts[:2] == ("frontend", "src") and suffix in (".ts", ".tsx"):
        return "frontend"
    return None


def java_commands(abs_path: str, project_dir: str) -> list:
    # spotlessFiles は「絶対パスに完全一致する正規表現」。. などを文字どおりに扱うため \Q...\E で囲む
    return [
        (
            "Spotless",
            ["./mvnw", "--quiet", "--batch-mode", "spotless:apply", f"-DspotlessFiles=\\Q{abs_path}\\E"],
            project_dir,
        )
    ]


def frontend_commands(abs_path: str, project_dir: str) -> list:
    frontend_dir = os.path.join(project_dir, "frontend")
    rel = os.path.relpath(abs_path, frontend_dir)
    return [
        ("Prettier", ["npx", "--no-install", "prettier", "--write", rel], frontend_dir),
        # 関係するテストがないファイルでも失敗にしない
        ("vitest related", ["npx", "--no-install", "vitest", "related", "--run", "--passWithNoTests", rel], frontend_dir),
    ]


def run(cmd: list, cwd: str):
    """コマンドを実行し (終了コード, 出力) を返す。"""
    env = {**os.environ, "NO_COLOR": "1", "FORCE_COLOR": "0", "CI": "1"}
    try:
        proc = subprocess.run(
            cmd, cwd=cwd, env=env, capture_output=True, text=True, timeout=COMMAND_TIMEOUT
        )
    except FileNotFoundError as e:
        return 127, str(e)
    except subprocess.TimeoutExpired:
        return 124, f"{COMMAND_TIMEOUT:g} 秒でタイムアウトしました"
    return proc.returncode, proc.stdout + proc.stderr


def truncate(text: str) -> str:
    text = ANSI_ESCAPE_RE.sub("", text).strip()
    if len(text) <= MAX_OUTPUT_CHARS:
        return text
    return "...（前半を省略）...\n" + text[-MAX_OUTPUT_CHARS:]


def block(reason: str) -> None:
    print(json.dumps({"decision": "block", "reason": reason}, ensure_ascii=False))
    sys.exit(0)


def skip(context: str) -> None:
    """確認を飛ばしたことを、止めずに（block にせず）Claude に伝える。"""
    output = {"hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": context}}
    print(json.dumps(output, ensure_ascii=False))
    sys.exit(0)


def main() -> None:
    try:
        data = json.load(sys.stdin)
    except Exception as e:  # PostToolUse は止められないので、ユーザーに知らせるだけにする
        print(f"format_and_test: hook 入力を解析できませんでした: {e}", file=sys.stderr)
        sys.exit(1)

    project_dir = os.environ.get("CLAUDE_PROJECT_DIR") or data.get("cwd") or os.getcwd()
    file_path = (data.get("tool_input") or {}).get("file_path", "")

    kind = classify(file_path, project_dir)
    if kind is None:
        sys.exit(0)

    builds = other_builds.find_other_maven_builds(project_dir)
    if builds:
        skip(
            f"ほかの Maven のビルドが動いているので、{file_path} の変更後のフォーマットとテストを今回は飛ばしました: "
            f"{other_builds.summary(builds)}。\n"
            f"同時に動かすと、node_modules や target/ が入れ替わる途中のものを読んで失敗するためです。"
            f"フォーマットとテストは、ビルドが終わったあとに、Stop の hook の ./mvnw verify で確かめます。"
        )

    abs_path = str(Path(file_path).resolve())
    commands = (java_commands if kind == "java" else frontend_commands)(abs_path, project_dir)

    for name, cmd, cwd in commands:
        code, output = run(cmd, cwd)
        if code != 0:
            block(
                f"{file_path} の変更後に {name} が失敗しました（終了コード {code}）。"
                f"内容を確認して直してください。\n"
                f"コマンド: {' '.join(cmd)}\n\n{truncate(output)}"
            )

    sys.exit(0)


if __name__ == "__main__":
    main()
