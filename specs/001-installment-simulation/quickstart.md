# 動作確認の手順（quickstart）：分割払いの支払シミュレーション

**対象**: [spec.md](spec.md) ／ **計画**: [plan.md](plan.md)

実装が終わったあとに、この機能が端から端まで動くことを確かめる手順。
コマンドや設定は [plan.md](plan.md) で採用した構成のとおり。構成が変わったら、この文書も直す。

## 前提

- GitHub Codespaces（[ADR 0004](../../docs/adr/0004-開発環境.md)）。Java・Node.js の版は [research.md](research.md) R1・R3 のとおり。
- この機能は DB を使わない（[research.md](research.md) R10）。ただし、アプリは起動するときに PostgreSQL へ接続する
  （[ADR 0011](../../docs/adr/0011-DB.md)。申込管理のために、Flyway と DB の設定が入っている）。
  そのため、2 で `java -jar` を、5 で `spring-boot:run` を動かす前に、`docker compose up -d db` で DB を起動する。
  1 の `./mvnw verify` は、Testcontainers が DB を用意するので、起動しなくてよい（Docker は要る）。

## 1. テストとカバレッジ

```bash
./mvnw verify
```

**期待する結果**:

- バックエンドのテスト（JUnit）と、フロントエンドのテスト（Vitest。Maven から呼ばれる）が、すべて成功する。
- 行・分岐カバレッジが、バックエンド・フロントエンドとも90%以上（constitution 原則II。閾値の設定は Day3、[ADR 0006](../../docs/adr/0006-カバレッジ基準.md)）。

フロントだけを確かめるとき：

```bash
cd frontend && npm test
```

## 2. 1つの jar を作って起動する

```bash
docker compose up -d db
./mvnw package
java -jar target/*.jar
```

**期待する結果**:

- `target/` に jar が1つだけできる（constitution 原則V）。
- jar の中に画面のファイルが入っている：`unzip -l target/*.jar | grep static/index.html` で1行出る。
- `http://localhost:8080/` を開くと、分割払いの画面が表示される。

## 3. API を直接呼んで確かめる

[contracts/installment-simulation-api.md](contracts/installment-simulation-api.md) の形で返ることを確かめる。

**正しい入力（spec User Story 1 の1）**:

```bash
curl -s -X POST http://localhost:8080/api/installment-simulations \
  -H 'Content-Type: application/json' \
  -d '{"amount":"100000","paymentCount":"12","annualRate":"15.0"}'
```

期待する結果：200。`totalFee` 8309、`totalPayment` 108309、`firstPayment` 9034、`subsequentPayment` 9025、
`schedule` が12行で、各行が spec User Story 2 の受け入れシナリオ5の表と一致する。

**入力エラー（spec User Story 3 の3）**:

```bash
curl -s -i -X POST http://localhost:8080/api/installment-simulations \
  -H 'Content-Type: application/json' \
  -d '{"amount":"","paymentCount":"4","annualRate":"12.25"}'
```

期待する結果：400、`Content-Type: application/problem+json`。`errors` に `amount`・`paymentCount`・`annualRate` の3件があり、
結果の項目（`totalFee` など）は含まれない。

## 4. 画面で確かめる

`http://localhost:8080/` で次を行う。

| # | 入力 | 期待する結果 | 根拠 |
|---|---|---|---|
| 1 | 100,000円・12回・15.0% | 手数料の合計 8,309円、支払総額 108,309円、初回 9,034円、2回目以降 9,025円。金額は3桁区切り | US1-1、FR-012・FR-014 |
| 2 | 1の続きで返済表を見る | 12行あり、各行が spec の表と一致。最終回の残高は0円 | US2-1・US2-5 |
| 3 | 100,000円・3回・0.0% | 手数料 0円、支払総額 100,000円、初回 33,334円、2回目以降 33,333円 | US1-3 |
| 4 | 1,000円・36回・15.0% | 手数料 247円、支払総額 1,247円、初回 57円、2回目以降 34円。36回目は手数料30円・元金4円・残高0円 | US2-6 |
| 5 | 利用金額を空のまま実行 | 結果は表示されず、利用金額の下に「利用金額を入力してください」 | US3-1 |
| 6 | 利用金額 `10000001`（カンマなしで入れる） | 結果は表示されず、入力できる範囲がメッセージで示される | US3-2 |
| 7 | 1の結果が出たあと、5の入力で再実行 | 前回の結果が消え、エラーだけが表示される | エッジケース |
| 8 | 画面を開いてから結果を見るまで | 30秒以内に操作でき、実行から1秒以内に結果が出る | SC-001・SC-005 |

## 5. 開発中の動かし方（参考）

画面を直しながら確かめるときは、バックエンドとフロントの開発サーバーを別々に起動する。
フロントの開発サーバーは `/api` を Spring Boot に転送する（[research.md](research.md) R5）。

```bash
docker compose up -d db         # 先に DB を起動する
./mvnw spring-boot:run          # ターミナル1：http://localhost:8080
cd frontend && npm run dev      # ターミナル2：Vite の開発サーバー
```
