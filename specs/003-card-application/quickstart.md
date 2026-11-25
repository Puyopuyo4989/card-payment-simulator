# 動作確認の手順（quickstart）：カードの申込管理

**対象**: [spec.md](spec.md) ／ **計画**: [plan.md](plan.md)

実装が終わったあとに、この機能が端から端まで動くことを確かめる手順。
前提・ビルド・起動の方法は分割払いと同じ（`specs/001-installment-simulation/quickstart.md`）。ここでは違うところだけを書く。
入力の例は、すべて架空の値（[research.md](research.md) R11）。本物の氏名・電話番号などは入力しない（constitution 原則IV）。

**Must は 1〜6。Should は 7。** Should を削ったときは、7 を行わない。

## 前提

- 分割払いの quickstart の「前提」と同じ。`java -jar`・`spring-boot:run` の前に `docker compose up -d db` で DB を起動する。
- `./mvnw verify` の前に、起動しているアプリ（`spring-boot:run`・`npm run dev`）を止める（CLAUDE.md。メモリ不足を避ける）。

## 1. テストとカバレッジ

```bash
./mvnw verify
```

**期待する結果**:

- バックエンド・フロントエンドのテストが、すべて成功する。分割払い・リボ払いのテストも、そのまま通る。
- 行・分岐カバレッジが、バックエンド・フロントエンドとも90%以上（constitution 原則II、[ADR 0006](../../docs/adr/0006-カバレッジ基準.md)）。新しい除外はない。
- DB を使うテストを2回続けて実行しても、同じ結果になる（各テストの前に表を空にしている。[research.md](research.md) R6）。
- JVM が途中で止まったときは、CLAUDE.md「./mvnw verify でテスト用の JVM が止まったとき」の手順に従う（`clean` の前に証拠を残す）。

## 2. DB の変更が V2 で入ることを確かめる

アプリを起動したあと（3）、DB に次の結果があることを確かめる。

```bash
docker compose exec db psql -U card_payment -d card_payment \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

期待する結果：`1 | init | t` と `2 | card application | t` の2行。`V1__init.sql` の中身は変わっていない（`git diff main -- src/main/resources/db/migration/V1__init.sql` が空）。

## 3. 1つの jar を作って起動する

分割払いの quickstart の 2 と同じ。`http://localhost:8080/` に、タブ「分割払い」「リボ払い」「申込管理」が表示される。

## 4. API を直接呼んで確かめる

[contracts/card-application-api.md](contracts/card-application-api.md) の形で返ることを確かめる。

**登録する（US1-1）**:

```bash
curl -s -i -X POST http://localhost:8080/api/card-applications \
  -H 'Content-Type: application/json' \
  -d '{"nameKanji":"テスト 太郎","nameKana":"テスト　タロウ","birthDate":"1990-04-01","annualIncome":"450","employerName":"テスト株式会社","phoneNumber":"0000000000"}'
```

期待する結果：201、`Location` に申込番号（`A` ＋ 8桁）。本文の `status` は `APPLIED`、`version` は 0、`history` は空。
以下、返った申込番号を `$N` とする。

**複数の誤り（US1-3）**:

```bash
curl -s -i -X POST http://localhost:8080/api/card-applications \
  -H 'Content-Type: application/json' \
  -d '{"nameKanji":"","nameKana":"てすと","birthDate":"2026-02-30","annualIncome":"10000","employerName":"テスト株式会社","phoneNumber":"03-0000-0000"}'
```

期待する結果：400、`application/problem+json`。`errors` に `nameKanji`・`nameKana`・`birthDate`・`annualIncome`・`phoneNumber` の5件。何も保存されない（一覧の件数が増えない）。

**状態を進める（US3-1・US3-3）と、ぶつかったとき（FR-019）**:

```bash
curl -s -X POST http://localhost:8080/api/card-applications/$N/status-changes \
  -H 'Content-Type: application/json' -d '{"toStatus":"UNDER_REVIEW","staffName":"テスト 担当A","version":"0"}'
# もう一度、古い version（0）で送る
curl -s -i -X POST http://localhost:8080/api/card-applications/$N/status-changes \
  -H 'Content-Type: application/json' -d '{"toStatus":"UNDER_REVIEW","staffName":"テスト 担当B","version":"0"}'
curl -s -X POST http://localhost:8080/api/card-applications/$N/status-changes \
  -H 'Content-Type: application/json' -d '{"toStatus":"REJECTED","staffName":"テスト 担当B","rejectionReason":"テスト用の否決理由","version":"1"}'
```

期待する結果：1つ目は 200（`UNDER_REVIEW`、`version` 1、履歴1行）。2つ目は **409**（何も変わらない）。3つ目は 200（`REJECTED`、`version` 2、履歴2行で、2行目に理由）。

**許されない変更（US3-6・US3-7）**: 新しく登録した申込に `{"toStatus":"APPROVED","staffName":"テスト 担当A","version":"0"}` を送ると 400
（`toStatus` に「先に「審査中」にしてください」）。上の否決した申込に `version` 2 で `UNDER_REVIEW` を送ると 400（「審査が終わった申込の状態は変えられません」）。

**一覧と詳細（US2）**: `GET /api/card-applications`、`GET /api/card-applications?status=REJECTED`、`GET /api/card-applications/$N`、
`GET /api/card-applications/A99999999`（404）を呼び、contracts の形と並び順を確かめる。

## 5. 画面で確かめる

`http://localhost:8080/` で「申込管理」のタブを選んで、次を行う。

| # | 操作 | 期待する結果 | 根拠 |
|---|---|---|---|
| 1 | 申込が0件の状態で開く | 「該当する申込はありません」 | US2-6 |
| 2 | 新規登録を開く | ダミーの情報だけを入力するよう注意書きがある。カード番号の入力欄はない | FR-020・FR-021 |
| 3 | 6項目に架空の値を入れて登録 | 詳細が開き、申込番号・状態「申込」・申込日時（日本時間）・年収「450万円」が出る。2分以内に終わる | US1-1、FR-011、SC-001 |
| 4 | 未入力・範囲外を混ぜて登録 | 登録されず、誤った項目すべての下に赤字のメッセージ | US1-3 |
| 5 | ブラウザを再読み込み、またはアプリを再起動して一覧を開く | 3 の申込が残っている | US1-2、SC-005 |
| 6 | 3件登録し、状態を変えてから一覧を開く。状態で絞り込み、「すべて」に戻す | 新しい順。絞り込みどおりに出る。否決が0件なら「該当する申込はありません」 | US2-1〜US2-4 |
| 7 | 一覧から1件を選ぶ | 全項目・状態・申込日時・更新日時・変更履歴（古い順）が出る。10秒以内に確かめられる | US2-5、SC-002 |
| 8 | 担当者名を空にして「審査中にする」 | 変更されず、担当者名を入力するよう赤字 | US3-5 |
| 9 | 審査中で、「否決」を選び、理由を空にして「決定する」 | 変更されず、否決の理由を入力するよう赤字 | US3-4 |
| 10 | 状態が「申込」の詳細を見る | 「審査中にする」だけが出る（承認・否決のボタンはない） | US3-6 |
| 11 | 承認・否決の詳細を見る | 状態を変えるフォームの代わりに「審査が終わった申込の状態は変えられません」 | US3-7 |
| 12 | ブラウザを2つ開き、同じ審査中の申込を表示。片方で承認し、もう片方で否決する | あとの方は変更されず、最新の状態を確かめるよう促すメッセージ。詳細が読み直され「承認」と履歴が見える | FR-019、エッジケース |
| 13 | 「分割払い」「リボ払い」のタブに切り替える | それぞれの quickstart の画面の確認がそのまま通る | 既存の機能 |
| 14 | 狭い画面（幅 360px ほど）で一覧・詳細・登録を見る | 表は横にスクロールでき、入力欄は縦に並ぶ（`index.css` の既存の動き） | 002 の Should のデザイン |

**性能（SC-006）**: 4 の登録の `curl` を、氏名を変えながら100回くり返して登録し、`curl -s -o /dev/null -w '%{time_total}\n' http://localhost:8080/api/card-applications`
が1秒より十分に短いこと、画面の一覧と絞り込みが1秒以内に出ることを確かめる。確かめたあとは `docker compose down -v` などで開発用の DB を片付けてよい。

## 6. 本番（EC2）で確かめる

main に入ったあと、自動デプロイ（[ADR 0009](../../docs/adr/0009-デプロイ構成.md)）が終わってから、EC2 の画面で 5 の 3・7・12 を行う
（`docs/sprint-plan.md` Day7 のゴール「全機能（Mustだけ）がEC2上の画面から使える」）。
Flyway の V2 が EC2 の DB に適用されたこと（起動のログ、または 2 の SQL）も確かめる。入力は架空の値だけにする。
登録した申込の申込日時が、日本時間の今の時刻で表示されることも見る（コンテナの既定のタイムゾーンは UTC だが、コードで `Asia/Tokyo` を明示している。research.md R10）。
アプリのログ（`docker compose logs app` など）に、入力した氏名・電話番号が出ていないことも見る（research.md R11-1）。

## 7. 【Should】キーワード検索とページ分け

Should を作ったときだけ行う（[research.md](research.md) R13）。

| # | 操作 | 期待する結果 | 根拠 |
|---|---|---|---|
| 1 | 氏名（カナ）「テスト　タロウ」の申込があるとき、キーワード「タロウ」で検索 | 氏名（漢字）・氏名（カナ）・申込番号のどれかに「タロウ」を含む申込だけが出る | US4-1 |
| 2 | 1 に状態の絞り込みを合わせる | 両方に合う申込だけが出る | US4-2 |
| 3 | 25件あるとき一覧を開き、次のページへ | 1ページ目に新しい順の20件、2ページ目に5件 | US4-3 |
| 4 | 合う申込がないキーワード | 「該当する申込はありません」 | US4-4 |
| 5 | キーワードに `%` や `_` を入れる | 文字そのものとして探す（すべての申込が出たりしない） | research.md R13 |
