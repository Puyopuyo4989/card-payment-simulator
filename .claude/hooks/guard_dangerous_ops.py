#!/usr/bin/env python3
"""Claude Code PreToolUse hook: 危険な操作をブロックする。

目的は AI のうっかりミスを止めること。意図的な回避への対策ではない（README.md 参照）。

ブロック対象:
  - Bash:
      rm -rf（-r と -f の組み合わせ全般）
      git push --force / -f / refspec 先頭の +（git push origin +main）
      git reset --hard
      git clean -f（-fd, -fdx などを含む）
      git commit --no-verify / -n（-anm などを含む）、git push --no-verify（push の -n は --dry-run なので許可）
        --no-veri / --no-verif などの省略形、LEFTHOOK=0 や LEFTHOOK_EXCLUDE などを付けた git commit / push、
        export LEFTHOOK=0、git -c core.hooksPath=...、git config での core.hooksPath の変更
      find -delete、find -exec / -execdir で上記の危険コマンドを実行するもの
      eval / bash -c 経由で上記を実行するもの
  - .env / .env.* の読み書き（.env.example は除く）
    （Read / Write / Edit / MultiEdit / NotebookEdit / Grep、および Bash コマンド内での参照）

テスト: python3 -m unittest discover -s .claude/hooks/tests -v

入力: stdin に PreToolUse の hook 入力 JSON
出力: ブロック時は permissionDecision=deny と理由を含む JSON を stdout に出して exit 0
      （理由は Claude に返される）。許可時は何も出力せず exit 0。
"""
import json
import os
import re
import shlex
import sys

# .env, .env.local, .env.production, .env* などにマッチ
ENV_FILE_RE = re.compile(r"^\.env([.*?\[].*)?$")
# 秘密情報を含まないテンプレートなので読み書きを許可する
ENV_ALLOWED_NAMES = {".env.example"}

# コマンドの区切りとみなすトークン（;, &&, ||, |, &, (, ) など）
SEPARATOR_RE = re.compile(r"^[;&|()]+$")

# コマンドの前に付いても実体のコマンドは後ろにあるラッパー
WRAPPERS = {"sudo", "command", "env", "nohup", "time", "exec", "nice", "xargs"}

SHELLS = {"bash", "sh", "zsh", "dash"}


def deny(reason: str) -> None:
    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": reason,
                }
            },
            ensure_ascii=False,
        )
    )
    sys.exit(0)


def is_env_path(path: str) -> bool:
    if not isinstance(path, str):
        return False
    p = path.strip().strip("'\"").rstrip("/")
    if not p:
        return False
    name = os.path.basename(p)
    if name in ENV_ALLOWED_NAMES:
        return False
    return bool(ENV_FILE_RE.match(name))


# ---------------------------------------------------------------- Bash 解析


def tokenize(command: str) -> list:
    # 改行もコマンド区切りとして扱う
    command = command.replace("\n", " ; ")
    try:
        lex = shlex.shlex(command, posix=True, punctuation_chars=True)
        lex.whitespace_split = True
        return list(lex)
    except ValueError:
        # クォートが閉じていない等。粗く分割して判定を続ける（安全側）
        return [t.strip("'\"") for t in re.findall(r"[;&|()]+|[^\s;&|()]+", command)]


def split_segments(tokens: list) -> list:
    segments, current = [], []
    for tok in tokens:
        if SEPARATOR_RE.match(tok):
            if current:
                segments.append(current)
            current = []
        else:
            current.append(tok)
    if current:
        segments.append(current)
    return segments


ASSIGNMENT_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")


def strip_wrappers(seg: list):
    """前置き（FOO=bar, sudo, env など）を外し、(実体のコマンド, 前置きの環境変数の代入) を返す。"""
    assignments = []
    i = 0
    while i < len(seg):
        tok = seg[i]
        if ASSIGNMENT_RE.match(tok):  # FOO=bar cmd
            assignments.append(tok)
            i += 1
        elif os.path.basename(tok) in WRAPPERS:
            i += 1
            while i < len(seg) and seg[i].startswith("-"):
                i += 1
        elif tok == "$":  # $( ... ) の残骸
            i += 1
        else:
            break
    return seg[i:], assignments


def check_rm(args: list):
    recursive = force = False
    for a in args:
        if a == "--":
            break
        if a in ("--recursive",):
            recursive = True
        elif a == "--force":
            force = True
        elif a.startswith("-") and not a.startswith("--"):
            flags = a[1:]
            recursive |= "r" in flags or "R" in flags
            force |= "f" in flags
    if recursive and force:
        return "rm -rf（再帰的な強制削除）は禁止されています。削除が必要な場合は対象を明示してユーザーに実行を依頼してください。"
    return None


def git_subcommand(args: list):
    i = 0
    while i < len(args):
        a = args[i]
        if a in ("-C", "-c", "--git-dir", "--work-tree", "--namespace"):
            i += 2
        elif a.startswith("-"):
            i += 1
        else:
            return a, args[i + 1 :]
    return None, []


def is_hooks_path_key(key: str) -> bool:
    return key.split("=", 1)[0].lower() == "core.hookspath"


def git_overrides_hooks_path(args: list) -> bool:
    """git -c core.hooksPath=... / --config-env=core.hooksPath=... で hook の置き場所を変えているか。"""
    i = 0
    while i < len(args):
        a = args[i]
        if a == "-c" and i + 1 < len(args) and is_hooks_path_key(args[i + 1]):
            return True
        if a.startswith("--config-env=") and is_hooks_path_key(a.removeprefix("--config-env=")):
            return True
        if a in ("-C", "-c", "--git-dir", "--work-tree", "--namespace"):
            i += 2
        elif a.startswith("-"):
            i += 1
        else:
            break
    return False


# git config の値を取るオプション（-f file など）。次のトークンはキーや値ではない
CONFIG_OPTS_WITH_VALUE = {"-f", "--file", "--blob", "--type", "--default", "--comment"}
# git config の書き込みのオプション
CONFIG_WRITE_OPTS = {"--unset", "--unset-all", "--add", "--replace-all"}
# git config の読むだけのオプション（--get key value-pattern のように、キーのあとに値が来ても書き込みではない）
CONFIG_READ_OPTS = {"--get", "--get-all", "--get-regexp", "--get-urlmatch", "-l", "--list"}


def config_writes_hooks_path(args: list) -> bool:
    """git config で core.hooksPath を書き換えるか（設定・--unset とも）。読むだけなら False。"""
    options, positionals = [], []
    i = 0
    while i < len(args):
        a = args[i]
        if a in CONFIG_OPTS_WITH_VALUE:
            i += 1
        elif a.startswith("-"):
            options.append(a)
        else:
            positionals.append(a)
        i += 1
    if positionals and positionals[0] in ("set", "unset"):  # git config set / unset（git 2.46 以降）
        return len(positionals) >= 2 and is_hooks_path_key(positionals[1])
    if not positionals or not is_hooks_path_key(positionals[0]):
        return False
    if any(o in CONFIG_READ_OPTS for o in options):
        return False
    return len(positionals) >= 2 or any(o in CONFIG_WRITE_OPTS for o in options)


# lefthook の環境変数のうち、表示を変えるだけのもの。ほかの LEFTHOOK_*（EXCLUDE, CONFIG, BIN など）は
# チェックを飛ばしたり差し替えたりできるのでブロックする
LEFTHOOK_HARMLESS_VARS = {"LEFTHOOK_OUTPUT", "LEFTHOOK_VERBOSE"}


def lefthook_skip_var(assignment: str):
    """lefthook のチェックを飛ばす環境変数の代入なら、その変数名を返す。"""
    name, _, value = assignment.partition("=")
    if name == "LEFTHOOK":
        return None if value.lower() in ("1", "true") else name
    if name.startswith("LEFTHOOK_") and name not in LEFTHOOK_HARMLESS_VARS:
        return name
    return None


def has_short_flag(args: list, flag: str) -> bool:
    """-f や -fdx のような短いオプションの束に flag が含まれるか。"""
    return any(a.startswith("-") and not a.startswith("--") and flag in a[1:] for a in args)


# git commit の短いオプションのうち、値を取るもの（-m msg / -mmsg など）。この文字より後ろは値なので見ない
COMMIT_SHORT_OPTS_WITH_VALUE = set("mFCct")
# 値を省略できるもの（-S<keyid>, -u<mode>）。値は同じトークンにくっつけて書くので、次のトークンは値ではない
COMMIT_SHORT_OPTS_WITH_OPTIONAL_VALUE = set("Su")
# 値を次のトークンに書ける長いオプション（--message msg など）
COMMIT_LONG_OPTS_WITH_VALUE = {
    "--message", "--file", "--reuse-message", "--reedit-message", "--template", "--author",
    "--date", "--fixup", "--squash", "--cleanup", "--trailer", "--pathspec-from-file",
}


def options_before_double_dash(args: list) -> list:
    return args[: args.index("--")] if "--" in args else args


# git は一意に決まる省略形を受け付ける。--no-ver より短いと --no-verbose とも読めるので git がエラーにする
NO_VERIFY_FORMS = {"--no-veri", "--no-verif", "--no-verify"}


def commit_has_no_verify(args: list) -> bool:
    """git commit の引数に --no-verify / -n（-anm のような束を含む）があるか。-m などの値は見ない。"""
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--":
            break
        if a in NO_VERIFY_FORMS:
            return True
        if a in COMMIT_LONG_OPTS_WITH_VALUE:
            i += 1
        elif a.startswith("-") and not a.startswith("--"):
            for j, c in enumerate(a[1:], start=1):
                if c == "n":
                    return True
                if c in COMMIT_SHORT_OPTS_WITH_VALUE:
                    if j == len(a) - 1:  # -m msg のように、値が次のトークン
                        i += 1
                    break
                if c in COMMIT_SHORT_OPTS_WITH_OPTIONAL_VALUE:
                    break
        i += 1
    return False


NO_VERIFY_REASON = (
    "{} は禁止されています。lefthook の git hooks（main への直接コミットの禁止・秘密情報の検出・"
    "フォーマットとメッセージの確認）が飛ばされます。hook が失敗したら、その原因を直してください。"
)


def check_git(args: list, assignments: list):
    sub, rest = git_subcommand(args)
    if git_overrides_hooks_path(args):
        return NO_VERIFY_REASON.format("git -c core.hooksPath=...（hook の置き場所の変更）")
    if sub == "config" and config_writes_hooks_path(rest):
        return NO_VERIFY_REASON.format("git config での core.hooksPath の変更")
    if sub in ("commit", "push"):
        for assignment in assignments:
            name = lefthook_skip_var(assignment)
            if name:
                return NO_VERIFY_REASON.format(f"環境変数 {name} を付けた git {sub}")
    if sub == "commit" and commit_has_no_verify(rest):
        return NO_VERIFY_REASON.format("git commit --no-verify（-n）")
    # git push の -n は --dry-run なので、ブロックするのは --no-verify（と省略形）だけ
    if sub == "push" and NO_VERIFY_FORMS & set(options_before_double_dash(rest)):
        return NO_VERIFY_REASON.format("git push --no-verify")
    if sub == "push":
        for a in rest:
            if a == "--":
                break
            # --force, --force-with-lease, --force-if-includes / -f, -uf など
            if a.startswith("--force") or re.match(r"^-[A-Za-z0-9]*f[A-Za-z0-9]*$", a):
                return f"git push の強制プッシュ（{a}）は禁止されています。リモートの履歴を壊す恐れがあります。"
        for a in rest:
            # refspec 先頭の + は、その ref だけ強制プッシュする指定
            if a.startswith("+") and len(a) > 1:
                return f"git push の強制プッシュ（refspec {a}）は禁止されています。リモートの履歴を壊す恐れがあります。"
    if sub == "reset" and "--hard" in rest:
        return "git reset --hard は禁止されています。未コミットの変更が失われます。git stash や git restore を検討してください。"
    if sub == "clean" and ("--force" in rest or has_short_flag(rest, "f")):
        return "git clean -f は禁止されています。追跡されていないファイルが復元できない形で削除されます。git clean -n で対象を確認し、ユーザーに実行を依頼してください。"
    return None


FIND_EXEC_ACTIONS = {"-exec", "-execdir", "-ok", "-okdir"}


def check_find(args: list, depth: int):
    if "-delete" in args:
        return "find -delete は禁止されています。条件に一致したファイルがまとめて削除されます。"
    for i, a in enumerate(args):
        if a in FIND_EXEC_ACTIONS:
            sub = []
            for t in args[i + 1 :]:
                if t in (";", "+"):
                    break
                sub.append(t)
            reason = check_segment(sub, depth + 1)
            if reason:
                return f"find {a} 経由の実行もチェック対象です: {reason}"
    return None


def check_segment(seg: list, depth: int):
    """1 つの単純コマンド（区切り記号を含まないトークン列）を判定する。"""
    if depth > 3:
        return None
    seg, assignments = strip_wrappers(seg)
    if not seg:
        return None
    cmd, args = os.path.basename(seg[0]), seg[1:]
    if cmd == "rm":
        return check_rm(args)
    if cmd == "git":
        return check_git(args, assignments)
    if cmd == "export":  # export LEFTHOOK=0 は、同じシェルのあとの git commit / push に効く
        for a in args:
            name = lefthook_skip_var(a) if ASSIGNMENT_RE.match(a) else None
            if name:
                return NO_VERIFY_REASON.format(f"環境変数 {name} の export")
    if cmd == "find":
        return check_find(args, depth)
    if cmd == "eval":
        return check_bash(" ".join(args), depth + 1)
    if cmd in SHELLS and "-c" in args:
        idx = args.index("-c")
        if idx + 1 < len(args):
            return check_bash(args[idx + 1], depth + 1)
    return None


def check_bash(command: str, depth: int = 0):
    if not isinstance(command, str) or depth > 3:
        return None
    tokens = tokenize(command)

    # .env の参照（cat .env, echo x > .env.local, --env-file=.env など）
    for tok in tokens:
        for part in re.split(r"[=:]", tok):
            if is_env_path(part):
                return f".env ファイル（{part}）へのアクセスは禁止されています。秘密情報を含むため、Bash からも読み書きできません。"

    for seg in split_segments(tokens):
        reason = check_segment(seg, depth)
        if reason:
            return reason
    return None


# ---------------------------------------------------------------- メイン


def main() -> None:
    try:
        data = json.load(sys.stdin)
    except Exception as e:  # 入力が読めない場合は安全側に倒してブロック
        print(f"guard_dangerous_ops: hook 入力を解析できませんでした: {e}", file=sys.stderr)
        sys.exit(2)

    tool = data.get("tool_name", "")
    tool_input = data.get("tool_input") or {}

    if tool == "Bash":
        reason = check_bash(tool_input.get("command", ""))
        if reason:
            deny(reason)
        sys.exit(0)

    for key in ("file_path", "notebook_path", "path", "glob"):
        value = tool_input.get(key)
        if is_env_path(value):
            deny(f".env ファイル（{value}）の読み書きは禁止されています。秘密情報を含むため {tool} ツールでは扱えません。")

    sys.exit(0)


if __name__ == "__main__":
    main()
