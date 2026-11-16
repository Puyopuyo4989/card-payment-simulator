# Claude Code hooks

## guard_dangerous_ops.py

`.claude/settings.json` の `PreToolUse` hook から呼ばれ、危険な操作を **実行前にブロック** します。
ブロックしたときは理由を Claude に返すので、Claude は別の方法を考えるか、ユーザーに確認を求めます。

### 目的

**AI のうっかりミスを止めるための安全柵** です。

- 「ビルド成果物を消すつもりで `rm -rf` した」「push が拒否されたので `--force` を付けた」
  「`.env` の中身を確認しようとして読み込んだ」といった、悪意のない事故を防ぎます。
- **わざと回避しようとする相手への対策ではありません。** 下の「既知の限界」にあるように、
  回避する方法はいくらでもあります。セキュリティ境界が必要な場合は、サンドボックス・
  OS の権限・秘密情報管理サービスなど、別の仕組みで守ってください。

### ブロック対象

| 種類 | 例 |
|------|----|
| 再帰的な強制削除 | `rm -rf`, `rm -fr`, `rm -r -f`, `rm --recursive --force` |
| 強制 push | `git push --force` / `-f` / `--force-with-lease`, `git push origin +main` |
| 変更の破棄 | `git reset --hard` |
| 未追跡ファイルの削除 | `git clean -f` / `-fd` / `-fdx` / `--force` |
| find による一括削除 | `find ... -delete`, `find ... -exec rm -rf {} \;` |
| 間接実行 | `eval "rm -rf x"`, `bash -c "git push -f"` |
| 秘密情報ファイル | `.env`, `.env.*` の読み書き（ツール経由・Bash 経由とも） |

`.env.example` はテンプレートで秘密情報を含まないため、読み書きを許可しています。

`&&` / `;` / `|` / 改行でつないだコマンド、`sudo` / `env` / `FOO=1` などの前置きも分解して判定します。

### permissions.deny との関係

`settings.json` の `permissions.deny` にも、代表的な `.env` 系ファイルの `Read` / `Edit` を登録しています。
hook が動かない場合（`python3` がない、など）の二重の備えです。

deny ルールには「`.env.*` のうち `.env.example` だけ除く」という例外を書けません（deny は allow より優先されます）。
そのため `.env.local`, `.env.production` など、よく使う名前を個別に列挙しています。
それ以外の `.env.*` は hook 側でブロックします。

### 既知の限界

以下はブロックできません（意図的な回避には無力です）。

- **バッククォート・コマンド置換の一部**: `` `echo rm` -rf x `` や `$(printf rm) -rf x` のように、実行時に組み立てられるコマンド
- **変数経由**: `F=.env; cat $F`、`CMD="rm -rf"; $CMD x`
- **スクリプトファイル経由**: 危険なコマンドを書いた `cleanup.sh` を作ってから `bash cleanup.sh` を実行する
- **別言語での削除・読み込み**: `python -c "import shutil; shutil.rmtree('x')"`、`node -e "fs.rmSync(...)"`、`perl -e 'unlink ...'`
- **git のエイリアス・設定**: `git config alias.fp "push --force"` → `git fp`、`git -c alias.x=...`、`push.default` などの設定による挙動
- **ワイルドカード・パターン指定**: `cat .e*`、`cat .en?`、`cat */.env*` のように、シェルが展開して初めて `.env` になるもの
- **別のコマンドでの同等操作**: `rsync --delete`、`shred`、`truncate`、`dd`、`mv x /dev/null`、`git checkout -- .`、`git branch -D` など
- **ディレクトリ単位の検索**: `Grep` でディレクトリ全体を検索した場合、中の `.env` の内容がヒットする可能性がある
- **hook 自体の無効化**: `.claude/settings.json` やこのスクリプトを書き換えれば止められる

### テスト

標準ライブラリの `unittest` だけで書いています。リポジトリのルートで実行してください。

```bash
python3 -m unittest discover -s .claude/hooks/tests -v
```

テストは hook スクリプトを実際に subprocess で起動し、PreToolUse の入力 JSON を stdin に渡して、
出力（`permissionDecision: "deny"` の JSON か、無出力か）を確認します。

ブロック対象を増やすときは、先に `tests/test_guard_dangerous_ops.py` にケースを足し、
失敗することを確認してからスクリプトを直してください。
