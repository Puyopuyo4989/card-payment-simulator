#!/usr/bin/env bash
# Codespace（devcontainer）を作ったあとに1回だけ動く。
# Claude Code と Superpowers プラグイン、git hooks（lefthook・gitleaks）を入れる。
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

# ---- git hooks（ADR 0008 の層②）----
# 版とチェックサムを固定する。版を上げるときは、公開から7日以上たった版を選び（ADR 0008）、
# リリースの checksums ファイルから sha256 を書き写す。
LEFTHOOK_VERSION="2.2.2"
GITLEAKS_VERSION="8.30.1"
BIN_DIR="$HOME/.local/bin"
mkdir -p "$BIN_DIR"

case "$(uname -m)" in
  x86_64)
    LEFTHOOK_ARCH="x86_64"
    LEFTHOOK_SHA256="2b8d7fd52771f26d7a50260ef10cd6fe704d66f60102e566362b84da09884ab7"
    GITLEAKS_ARCH="x64"
    GITLEAKS_SHA256="551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb"
    ;;
  aarch64 | arm64)
    LEFTHOOK_ARCH="arm64"
    LEFTHOOK_SHA256="f28231481dc0539776165565d4c3a425a775b047030708b914d52ff91be3a536"
    GITLEAKS_ARCH="arm64"
    GITLEAKS_SHA256="e4a487ee7ccd7d3a7f7ec08657610aa3606637dab924210b3aee62570fb4b080"
    ;;
  *)
    echo "!! 対応していない CPU（$(uname -m)）なので、lefthook と gitleaks を入れられない" >&2
    exit 1
    ;;
esac

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "==> lefthook ${LEFTHOOK_VERSION} をインストールする"
LEFTHOOK_FILE="lefthook_${LEFTHOOK_VERSION}_Linux_${LEFTHOOK_ARCH}.gz"
curl -fsSL -o "$TMP_DIR/$LEFTHOOK_FILE" \
  "https://github.com/evilmartians/lefthook/releases/download/v${LEFTHOOK_VERSION}/${LEFTHOOK_FILE}"
echo "${LEFTHOOK_SHA256}  $TMP_DIR/$LEFTHOOK_FILE" | sha256sum --check --quiet
gunzip -c "$TMP_DIR/$LEFTHOOK_FILE" > "$BIN_DIR/lefthook"
chmod +x "$BIN_DIR/lefthook"

echo "==> gitleaks ${GITLEAKS_VERSION} をインストールする"
GITLEAKS_FILE="gitleaks_${GITLEAKS_VERSION}_linux_${GITLEAKS_ARCH}.tar.gz"
curl -fsSL -o "$TMP_DIR/$GITLEAKS_FILE" \
  "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/${GITLEAKS_FILE}"
echo "${GITLEAKS_SHA256}  $TMP_DIR/$GITLEAKS_FILE" | sha256sum --check --quiet
tar -xzf "$TMP_DIR/$GITLEAKS_FILE" -C "$BIN_DIR" gitleaks

echo "==> フロントの依存（Prettier を含む）を入れる"
npm ci --prefix frontend

# Claude Code の PostToolUse hook（format_and_test.py）はコマンド 1 つを 25 秒で打ち切る。
# 初回の ./mvnw は Maven 本体・依存・Spotless（google-java-format）のダウンロードで遅いので、ここで済ませておく。
# spotless:apply だとファイルを書き換えてしまうので、確認だけの spotless:check を使う。
echo "==> Maven の依存と Spotless を先にダウンロードしておく"
if ./mvnw --quiet --batch-mode dependency:go-offline spotless:check; then
  echo "==> Maven の依存と Spotless をダウンロードした"
else
  # フォーマットのずれやネットワークの失敗でも、コンテナ自体は使えるようにする（失敗は表示して続ける）。
  echo "!! Maven の準備が途中で失敗した。初回の hook がタイムアウトしたら、ターミナルで次を実行する：" >&2
  echo "   ./mvnw dependency:go-offline spotless:check" >&2
fi

echo "==> git hooks を入れる（lefthook install）"
"$BIN_DIR/lefthook" install
