#!/usr/bin/env bash
# Codespace（devcontainer）を作ったあとに1回だけ動く。
# Claude Code と Superpowers プラグインを入れる。
set -euo pipefail

CLAUDE_BIN="$HOME/.local/bin/claude"

echo "==> Claude Code をインストールする"
curl -fsSL https://claude.ai/install.sh | bash -s stable

echo "==> Superpowers プラグインをインストールする"
if "$CLAUDE_BIN" plugin marketplace add anthropics/claude-plugins-official \
  && "$CLAUDE_BIN" plugin install superpowers@claude-plugins-official; then
  echo "==> Superpowers を入れた"
else
  # プラグインが入らなくても、コンテナ自体は使えるようにする（失敗は表示して続ける）。
  echo "!! Superpowers を入れられなかった。ターミナルで次を実行する：" >&2
  echo "   claude plugin marketplace add anthropics/claude-plugins-official" >&2
  echo "   claude plugin install superpowers@claude-plugins-official" >&2
fi
