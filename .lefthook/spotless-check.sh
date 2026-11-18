#!/usr/bin/env bash
# コミットする Java ファイルだけを Spotless で確かめる。
# 引数：リポジトリのルートからの相対パス（lefthook の {staged_files}）
set -euo pipefail

# spotlessFiles は「絶対パスに完全一致する正規表現」をカンマ区切りで受け取る。
# パスの中の . などを文字どおりに扱うため、\Q...\E で囲む。
patterns=""
for f in "$@"; do
  patterns="${patterns:+$patterns,}\\Q$PWD/$f\\E"
done

if ! ./mvnw --quiet --batch-mode spotless:check -DspotlessFiles="$patterns"; then
  echo "" >&2
  echo "フォーマットが google-java-format と違います。次で直してから、もう一度 git add してください：" >&2
  echo "  ./mvnw spotless:apply" >&2
  exit 1
fi
