#!/usr/bin/env python3
"""Claude Code Stop hook: 応答を終える前に ./mvnw verify（テスト・カバレッジ・フォーマット）を動かす。

動かさないとき:
  - hook 入力の stop_hook_active が true のとき（この hook で一度止めたあと。同じ失敗で止まり続けないため）
  - 前回 verify が成功したときから src/ と frontend/src/ の中身が変わっていないとき
    （verify は 1〜2 分かかるため。中身のハッシュを target/verify-on-stop.last-ok に残して比べる）

テスト: python3 -m unittest discover -s .claude/hooks/tests -v

入力: stdin に Stop の hook 入力 JSON
出力: verify が失敗したときは decision=block と失敗の内容を含む JSON を stdout に出して exit 0
      （Claude は応答を終えずに、内容を読んで直す）。全部の出力は target/verify-on-stop.log に残す。
      成功したとき・動かさないときは何も出力せず exit 0。
"""
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

# 変わったら verify を動かし直すディレクトリ（プロジェクトのルートから）
WATCHED_DIRS = ("src", "frontend/src")

# 前回成功したときのハッシュと、verify の全部の出力の置き場所（target/ は .gitignore 済み）
STATE_FILE = Path("target") / "verify-on-stop.last-ok"
LOG_FILE = Path("target") / "verify-on-stop.log"

# Claude に返す出力の上限（長すぎると読みにくいので末尾だけ残す）
MAX_OUTPUT_CHARS = 4000

# verify の制限（秒）。settings.json の hook の timeout（600 秒）に収まるようにする。
# テストで短くできるよう、環境変数 VERIFY_ON_STOP_TIMEOUT で上書きできる
COMMAND_TIMEOUT = float(os.environ.get("VERIFY_ON_STOP_TIMEOUT", "540"))

VERIFY_COMMAND = ["./mvnw", "--batch-mode", "verify"]

# NO_COLOR を無視して色を付けるツール（Testing Library など）があるので、出力から取り除く
ANSI_ESCAPE_RE = re.compile(r"\x1b\[[0-9;]*m")


def source_hash(project_dir: Path) -> str:
    """監視するディレクトリの全ファイルのパスと中身から、ハッシュを作る。"""
    digest = hashlib.sha256()
    for watched in WATCHED_DIRS:
        root = project_dir / watched
        if not root.is_dir():
            continue
        for path in sorted(p for p in root.rglob("*") if p.is_file()):
            digest.update(path.relative_to(project_dir).as_posix().encode())
            digest.update(b"\0")
            digest.update(hashlib.sha256(path.read_bytes()).digest())
    return digest.hexdigest()


def run(cmd: list, cwd: Path):
    """コマンドを実行し (終了コード, 出力) を返す。"""
    env = {**os.environ, "NO_COLOR": "1", "FORCE_COLOR": "0", "CI": "1"}
    try:
        proc = subprocess.run(
            cmd,
            cwd=cwd,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=COMMAND_TIMEOUT,
        )
    except FileNotFoundError as e:
        return 127, str(e)
    except subprocess.TimeoutExpired:
        return 124, f"{COMMAND_TIMEOUT:g} 秒でタイムアウトしました"
    return proc.returncode, proc.stdout


def truncate(text: str) -> str:
    text = ANSI_ESCAPE_RE.sub("", text).strip()
    if len(text) <= MAX_OUTPUT_CHARS:
        return text
    return "...（前半を省略）...\n" + text[-MAX_OUTPUT_CHARS:]


def block(reason: str) -> None:
    print(json.dumps({"decision": "block", "reason": reason}, ensure_ascii=False))
    sys.exit(0)


def main() -> None:
    try:
        data = json.load(sys.stdin)
    except Exception as e:  # 入力が読めないときは止めずに、ユーザーに知らせるだけにする
        print(f"verify_on_stop: hook 入力を解析できませんでした: {e}", file=sys.stderr)
        sys.exit(1)

    if data.get("stop_hook_active"):
        sys.exit(0)

    project_dir = Path(os.environ.get("CLAUDE_PROJECT_DIR") or data.get("cwd") or os.getcwd())
    state_file = project_dir / STATE_FILE
    current = source_hash(project_dir)
    if state_file.is_file() and state_file.read_text().strip() == current:
        sys.exit(0)

    code, output = run(VERIFY_COMMAND, project_dir)
    log_file = project_dir / LOG_FILE
    log_file.parent.mkdir(parents=True, exist_ok=True)
    log_file.write_text(output)

    if code != 0:
        block(
            f"応答を終える前の {' '.join(VERIFY_COMMAND)} が失敗しました（終了コード {code}）。"
            f"テスト・カバレッジ（90%）・フォーマットのどれが落ちたかを確認して直してください。\n"
            f"全部の出力: {log_file}\n\n{truncate(output)}"
        )

    state_file.write_text(current + "\n")
    sys.exit(0)


if __name__ == "__main__":
    main()
