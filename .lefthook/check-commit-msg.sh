#!/usr/bin/env bash
# コミットメッセージの1行目が Conventional Commits の形式か確かめる。説明は日本語でよい。
# 引数：コミットメッセージのファイル（lefthook の {1}）
set -euo pipefail

# 最初の「空でも # 始まりでもない行」を1行目とみなす（エディタで書いたときのコメント行を飛ばす）
subject="$(grep -v -E '^[[:space:]]*(#|$)' "$1" | head -n 1 || true)"

# git revert・git merge が作るメッセージと、rebase --autosquash 用のメッセージは通す
if printf '%s' "$subject" | grep -q -E '^(Revert "|Merge |fixup! |squash! |amend! )'; then
  exit 0
fi

# Conventional Commits の標準の種類だけを通す（過去の feature: のコミットはそのまま残す）
types='feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert'
if printf '%s' "$subject" | grep -q -E "^($types)(\([^()]+\))?!?: [^[:space:]]"; then
  exit 0
fi

cat >&2 <<EOF
コミットメッセージが Conventional Commits の形式ではありません。
  1行目：${subject:-（空）}

「種類: 説明」の形で書いてください（説明は日本語でよい）。
  種類：${types//|/ / }
  例：feat: 分割払いの手数料を計算する
      fix(frontend): 金額の桁区切りを直す
      refactor!: API の返り値を変える
EOF
exit 1
