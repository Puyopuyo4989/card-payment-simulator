#!/usr/bin/env bash
# main ブランチへの直接コミットを止める（constitution 原則VI：main への変更は必ず PR を通す）。
set -euo pipefail

branch="$(git branch --show-current)"
if [ "$branch" = "main" ]; then
  echo "main ブランチには直接コミットできません。ブランチを作ってから PR を出してください。" >&2
  echo "  例：git switch -c feature/xxx" >&2
  exit 1
fi
