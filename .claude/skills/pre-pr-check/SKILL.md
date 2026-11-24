---
name: pre-pr-check
description: "PR を出す前の確認（自作スキル②）。ブランチと未コミットの変更、./mvnw verify、金額に double・float を使っていないこと（金額の計算に関わるファイルが変わっていたらスキル①も使う）、/speckit-analyze、コードレビューと完了前の確認を順に行い、結果を一覧で報告する。PR の作成と push はしない。PR を出す前、または「PR 前の確認」を頼まれたときに使う。"
user-invocable: true
disable-model-invocation: false
---

# PR 前の確認（自作スキル②）

PR を出す前に、次の 1〜6 を**この順に、すべて**行う（constitution 原則VI、[ADR 0008](../../../docs/adr/0008-品質の多層防御.md)）。
途中で失敗しても止めずに残りを行い、最後に結果を一覧で報告する。ただし 1 が失敗したときは、2 以降を行う前に人に報告して判断を仰ぐ。

## 守ること

- **PR の作成と push はしない。** 結果を報告して、人の判断を待つ。
- 確認の途中で見つけた問題を、その場で直さない。直すかどうかは、報告を見て人が決める。
- hook や権限の設定で操作が止められたら、別の書き方で回避しない。止められた理由と、やりたかったことを報告する（CLAUDE.md）。
- 結果は、実際に実行したコマンドの出力にもとづいて書く。実行していない確認を「通過」と書かない。

## 1. ブランチと未コミットの変更

```bash
git branch --show-current
git status --porcelain
```

- 今のブランチが `main` でなければ通過。
- `git status --porcelain` が何も出さなければ通過。出したときは、そのファイルの一覧を報告に載せる。

## 2. `./mvnw verify`（テスト・カバレッジ・フォーマット）

```bash
./mvnw --batch-mode verify
```

- `BUILD SUCCESS` なら通過。
- 報告に載せるもの：
  - バックエンドとフロントのテストの件数と失敗の数
  - カバレッジ（行・分岐。バックエンド・フロントとも 90% 以上か、[ADR 0006](../../../docs/adr/0006-カバレッジ基準.md)）
  - フォーマットの確認（Spotless・Prettier）の結果
- 失敗したときは、失敗したテストの名前か、失敗したプラグインと理由を載せる。

バックエンドのカバレッジは `target/site/jacoco/jacoco.csv` から、フロントのカバレッジは出力の `Coverage summary` から読む。

## 3. 金額に `double`・`float` を使っていない

```bash
grep -rnE '\b(double|float|Double|Float)\b|Math\.pow' src/main/java
```

- 何も出さなければ通過（constitution 原則III、[ADR 0005](../../../docs/adr/0005-金額計算と端数処理.md)）。
- 出したときは、該当する行をすべて報告に載せる。金額でない使い方に見えても、通過とは書かず、人の判断を仰ぐ。

### 金額の計算に関わるファイルが変わっていたら、スキル①も使う

```bash
git diff --name-only main...HEAD -- src/main/java frontend/src
git status --porcelain -- src/main/java frontend/src
```

- どちらかが、テスト（`*Test.java`・`*.test.ts(x)`・`setupTests.ts`）以外のファイルを1つでも出したら、自作スキル①（**money-calc-review**）を使う。
- スキル①の「確認ごとの結果」の表と「見つけたもの」の一覧を、そのまま報告に載せる。スキル①の「違反」が0件なら通過、「要確認」があれば要確認、「違反」があれば失敗。
- 該当するファイルがなければ、スキル①は使わず、報告に「金額の計算に関わるファイルの変更なし」と書く。

## 4. `/speckit-analyze`

1. `.specify/feature.json` の `feature_directory` を読み、対象の機能を確かめる。
2. その機能に対して、speckit-analyze のスキルを実行する。speckit-analyze は読むだけで、ファイルを書き換えない。
3. 報告に載せるもの：CRITICAL・HIGH・MEDIUM・LOW の件数と、CRITICAL・HIGH の内容。
4. CRITICAL・HIGH が0件なら通過。

## 5. コードレビューと完了前の確認

1. Superpowers の **requesting-code-review** で、`main` との差分（`git diff main...HEAD`）をレビューする。
2. Superpowers の **verification-before-completion** に従い、1〜4 とレビューの結果を、コマンドの出力と照らして確かめる。
3. 報告に載せるもの：レビューの指摘の件数（重要度ごと）と内容。重要度が高い指摘がなければ通過。

## 6. 結果を一覧で報告する

次の形で報告する。「結果」は「通過」「失敗」「要確認」のどれかにする。

| # | 確認 | 結果 | 内容 |
|---|---|---|---|
| 1 | ブランチ・未コミットの変更 | | ブランチ名、未コミットの変更の有無 |
| 2 | `./mvnw verify` | | テストの件数、カバレッジ、フォーマット |
| 3 | `double`・`float` の grep | | 出た行の数 |
| 3a | 金額計算のレビュー（スキル①） | | 使ったかどうか、違反・要確認の件数 |
| 4 | `/speckit-analyze` | | 重要度ごとの件数 |
| 5 | コードレビュー・完了前の確認 | | 指摘の件数 |

一覧のあとに、失敗と要確認の項目について、人が判断すべきことを箇条書きで書く。
最後に「PR の作成と push はしていません。判断をお願いします。」と書いて終える。
