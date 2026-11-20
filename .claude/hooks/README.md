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
| git hooks の回避 | `git commit --no-verify` / `-n` / `-anm`, `git push --no-verify`（`git push -n` は `--dry-run` なので許可）, `--no-veri` / `--no-verif` などの省略形 |
| lefthook を飛ばす環境変数 | `LEFTHOOK=0` / `LEFTHOOK=false` / `LEFTHOOK_EXCLUDE=...` / `LEFTHOOK_CONFIG=...` / `LEFTHOOK_BIN=...` を付けた `git commit` / `git push`, `export LEFTHOOK=0`（表示を変えるだけの `LEFTHOOK_OUTPUT` / `LEFTHOOK_VERBOSE` と `LEFTHOOK=1` は許可） |
| hook の置き場所の変更 | `git -c core.hooksPath=...`, `git --config-env=core.hooksPath=...`, `git config core.hooksPath ...` / `set` / `--unset` / `unset`（読むだけの `git config --get core.hooksPath` は許可） |
| find による一括削除 | `find ... -delete`, `find ... -exec rm -rf {} \;` |
| 間接実行 | `eval "rm -rf x"`, `bash -c "git push -f"` |
| 秘密情報ファイル | `.env`, `.env.*` の読み書き（ツール経由・Bash 経由とも） |

`.env.example` はテンプレートで秘密情報を含まないため、読み書きを許可しています。
Bash では `.env.example。` や `.env.example,` のように句読点・記号が続いても `.env.example` として扱います
（`、` `。` `,` `` ` `` `（` `）` などをファイル名の区切りとみなし、文末の `.` は外します）。

git commit のメッセージは `.env` の判定の対象にしません。`-m` / `--message` の値と、
`git commit -F - <<'EOF' ... EOF` のヒアドキュメントの本文が対象外です。
ただし `-F` / `--file` に渡すファイルのパスは判定します（`git commit -F .env` は `.env` の中身がメッセージになるため）。
git commit 以外のヒアドキュメントの本文（`bash <<'EOF' ... EOF` など）は今までどおり判定します。

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
- **git hooks の別の回避方法**: `GIT_CONFIG_COUNT` / `GIT_CONFIG_PARAMETERS` などの環境変数による設定の上書き、`.git/config` の直接の編集、`.git/hooks` の中のファイルの削除や書き換え
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

## format_and_test.py

`.claude/settings.json` の `PostToolUse` hook から呼ばれ、Claude が `Edit` / `Write` / `MultiEdit` で
ファイルを変えた **あと** に、そのファイルをフォーマットし、フロントは関係するテストを動かします。

### やること

| 変更したファイル | やること |
|------------------|----------|
| `src/**/*.java` | そのファイルだけを Spotless（`spotless:apply`）でフォーマットする。テストは動かさない（Stop の hook の `verify_on_stop.py` で動かす） |
| `frontend/src/**/*.ts`, `*.tsx` | そのファイルを Prettier でフォーマットし、`vitest related` でそのファイルに関係するテストだけを動かす |
| それ以外 | 何もしない |

フォーマットやテストが失敗したときは、`decision: "block"` と失敗の出力（末尾 4000 文字まで）を Claude に返します。
PostToolUse の時点で編集は済んでいるので、編集が取り消されるわけではありません。Claude が出力を読んで直します。

関係するテストがないファイルでも失敗にしないよう、`vitest related` には `--passWithNoTests` を付けています。

### タイムアウト

| 制限 | 秒数 | 場所 |
|------|------|------|
| hook 全体 | 60 秒 | `.claude/settings.json` の `PostToolUse` の `timeout` |
| コマンド 1 つ | 25 秒 | `format_and_test.py` の `COMMAND_TIMEOUT` |

フロントは 2 つのコマンド（Prettier と vitest related）を順に動かすので、合わせても 60 秒に収まるよう 25 秒にしています。
コマンドが 25 秒を超えたときは、`25 秒でタイムアウトしました` を失敗として Claude に返します。
どちらかの秒数を変えるときは、もう一方も見直してください。

コマンド 1 つの制限は、環境変数 `FORMAT_AND_TEST_TIMEOUT`（秒）で上書きできます。
テストで制限を短くするためのもので、ふだんは設定しません。

初回の `./mvnw` は Maven 本体・依存・Spotless のダウンロードで 25 秒を超えることがあります。
そのため `.devcontainer/post-create.sh` で、コンテナを作るときに `./mvnw dependency:go-offline spotless:check` を動かしてダウンロードを済ませています。
これが失敗していて初回の hook がタイムアウトしたら、ターミナルで同じコマンドを一度実行してください。

### テスト

`guard_dangerous_ops.py` と同じく `unittest` だけで書いています（上の「テスト」のコマンドでまとめて動きます）。

本物の Maven・Prettier・vitest は動かしません。一時ディレクトリを `CLAUDE_PROJECT_DIR` にして、
`./mvnw` と `npx` を「呼ばれた引数を記録し、指定した呼び出しだけ失敗する」偽物に差し替え、
どのファイルでどのコマンドが呼ばれるか、失敗が Claude に返るかを確かめます。

タイムアウトのテストでは、`FORMAT_AND_TEST_TIMEOUT=1` で制限を 1 秒にして、偽の `npx` を `exec sleep 5` で待たせます。
`exec` を付けないと、時間切れでシェルを止めても `sleep` が残って出力を読み終えられず、テストが固まります。

### 実際の動きを確かめる手順

Claude Code を **起動し直してから**（hook の設定は起動時に読まれます）、次のように頼みます。
`/hooks` で `PostToolUse` に `format_and_test.py` が入っていることも確認できます。

1. **Java のフォーマットが直ること**
   - 頼む：「`src/main/java/com/example/cardpayment/CardPaymentSimulatorApplication.java` の
     `public class` を `public     class` にして、インデントをタブにして。フォーマットは直さないで」
   - 確かめる：編集のあと `git diff` に差分がない（Spotless が元のフォーマットに戻した）。
2. **フロントのフォーマットが直ること**
   - 頼む：「`frontend/src/App.tsx` の `export default` を `export     default` にして、
     セミコロンを付けて。フォーマットは直さないで」
   - 確かめる：`git diff` に差分がない（Prettier が戻した）。Claude の画面にテストの失敗は出ない。
3. **テストの失敗が Claude に返ること**
   - 頼む：「`frontend/src/App.tsx` の見出しを『分割払い』に変えて。テストは直さないで」
   - 確かめる：編集のあと `PostToolUse:Edit hook ... vitest related が失敗しました` と出て、
     Claude が `App.test.tsx` の `見出し「分割払いシミュレーション」が表示される` が落ちたことに触れる。
     （「テストは直さないで」を外すと、Claude がそのまま直しにいきます）
4. **それ以外のファイルでは何もしないこと**
   - 頼む：「`README.md` の末尾に空行を 1 つ足して」
   - 確かめる：hook のメッセージが出ず、すぐ終わる。
5. 後片付け：`git restore src frontend/src README.md` で戻す。

`claude --debug` で起動すると、hook に渡った入力と出力を詳しく見られます。

## verify_on_stop.py

`.claude/settings.json` の `Stop` hook から呼ばれ、Claude が応答を終えようとしたときに
`./mvnw --batch-mode verify`（テスト・カバレッジ 90%・フォーマット）を動かします。

### やること

| 状況 | やること |
|------|----------|
| hook 入力の `stop_hook_active` が `true` | 何もしない（この hook で一度止めたあとなので、同じ失敗で止まり続けないようにする） |
| 前回 verify が成功したときから `src/` と `frontend/src/` の中身が変わっていない | 何もしない（verify は 1〜2 分かかるため） |
| それ以外 | verify を動かす。成功したら何も出さずに終わる。失敗したら `decision: "block"` と出力の末尾 4000 文字を Claude に返し、Claude は応答を終えずに直す |

- 変わったかどうかは、`src/` と `frontend/src/` の全ファイルのパスと中身のハッシュで比べます。
  ファイルの追加・削除・名前の変更も変更として扱います。
- ハッシュは **成功したときだけ** `target/verify-on-stop.last-ok` に残します。失敗したままなら、次に応答を終えるときにもう一度動かします。
- verify の全部の出力は `target/verify-on-stop.log` に残します（失敗したときは、その場所も Claude に返します）。
- `./mvnw clean` で `target/` を消すと、次は必ず verify を動かします。

`stop_hook_active` が `true` のときは確かめないので、Claude が直したあとの verify は **次に応答を終えるとき** に動きます
（ソースが変わっているのでハッシュが合わず、動き直します）。

### タイムアウト

| 制限 | 秒数 | 場所 |
|------|------|------|
| hook 全体 | 600 秒 | `.claude/settings.json` の `Stop` の `timeout` |
| verify | 540 秒 | `verify_on_stop.py` の `COMMAND_TIMEOUT`（環境変数 `VERIFY_ON_STOP_TIMEOUT` で上書きできる。テスト用） |

### テスト

上の「テスト」のコマンドでまとめて動きます。本物の Maven は動かさず、一時ディレクトリの `./mvnw` を
「呼ばれた引数を記録し、`FAKE_FAIL` が空でなければ失敗する」偽物に差し替えて、
いつ verify が動くか、失敗が Claude に返るかを確かめます。

### 実際の動きを確かめる手順

Claude Code を **起動し直してから** 行います。`/hooks` で `Stop` に `verify_on_stop.py` が入っていることも確認できます。

1. **変更がないと動かないこと**
   - ターミナルで一度 `python3 .claude/hooks/verify_on_stop.py <<< '{}'` を動かし、成功させて `target/verify-on-stop.last-ok` を作る
   - 頼む：「`README.md` を読んで、1 行で要約して」
   - 確かめる：応答がすぐに終わる（verify が動かない）。
2. **テストの失敗で止まり、Claude が直しにいくこと**
   - 頼む：「`frontend/src/App.tsx` の見出しを『分割払い』に変えて。テストはまだ直さないで、そこで応答を終えて」
   - 確かめる：応答を終えようとしたところで `Stop hook ... ./mvnw --batch-mode verify が失敗しました` と出て、
     Claude が `target/verify-on-stop.log` や失敗したテストに触れて作業を続ける。
     2 回目に応答を終えるときは（`stop_hook_active` が `true` なので）止まらずに終わる。
3. **カバレッジの不足で止まること**
   - 頼む：「`src/main/java` に、テストのない public メソッドを 30 行ほど持つクラスを 1 つ足して。テストは書かないで」
   - 確かめる：`jacoco` の `Coverage checks have not been met` で止まる。
4. 後片付け：`git restore src frontend/src` と、足したファイルの削除。
