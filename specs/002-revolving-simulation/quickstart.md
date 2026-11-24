# 動作確認の手順（quickstart）：リボ払い（定額方式）の支払シミュレーション

**対象**: [spec.md](spec.md) ／ **計画**: [plan.md](plan.md)

実装が終わったあとに、この機能が端から端まで動くことを確かめる手順。
前提・ビルド・起動の方法は分割払いと同じ（`specs/001-installment-simulation/quickstart.md`）。ここでは違うところだけを書く。

## 前提

- 分割払いの quickstart の「前提」と同じ。`java -jar`・`spring-boot:run` の前に `docker compose up -d db` で DB を起動する。

## 1. テストとカバレッジ

```bash
./mvnw verify
```

**期待する結果**:

- バックエンド・フロントエンドのテストが、すべて成功する。**分割払いのテストも、共通化のあと、確かめる内容を変えずに通る**（[research.md](research.md) R6）。
- 行・分岐カバレッジが、バックエンド・フロントエンドとも90%以上（constitution 原則II、[ADR 0006](../../docs/adr/0006-カバレッジ基準.md)）。

## 2. 1つの jar を作って起動する

分割払いの quickstart の 2 と同じ。`http://localhost:8080/` に、タブ「分割払い」「リボ払い」が表示される。

## 3. API を直接呼んで確かめる

[contracts/revolving-simulation-api.md](contracts/revolving-simulation-api.md) の形で返ることを確かめる。

**正しい入力（spec User Story 1 の1）**:

```bash
curl -s -X POST http://localhost:8080/api/revolving-simulations \
  -H 'Content-Type: application/json' \
  -d '{"balance":"100000","monthlyPayment":"10000","annualRate":"15.0"}'
```

期待する結果：200。`paymentCount` 11、`totalFee` 7497、`totalPayment` 107497、`schedule` が11行で、spec User Story 2 の受け入れシナリオ6の表と一致する。

**払い終わらない支払額（spec User Story 3 の5）**:

```bash
curl -s -i -X POST http://localhost:8080/api/revolving-simulations \
  -H 'Content-Type: application/json' \
  -d '{"balance":"10000000","monthlyPayment":"126000","annualRate":"15.0"}'
```

期待する結果：400、`Content-Type: application/problem+json`。`errors` は `monthlyPayment` の1件で、360回を超えることと、支払額を増やすことが書いてある。

**複数の誤り（spec User Story 3 の7）**:

```bash
curl -s -i -X POST http://localhost:8080/api/revolving-simulations \
  -H 'Content-Type: application/json' \
  -d '{"balance":"","monthlyPayment":"1500","annualRate":"12.25"}'
```

期待する結果：400。`errors` に `balance`・`monthlyPayment`・`annualRate` の3件があり、結果の項目は含まれない。

**分割払いが変わっていないこと**: 分割払いの quickstart の 3 を実行し、同じ結果が返ることを確かめる。

## 4. 画面で確かめる

`http://localhost:8080/` で「リボ払い」のタブを選んで、次を行う。

| # | 入力 | 期待する結果 | 根拠 |
|---|---|---|---|
| 1 | 100,000円・10,000円・15.0% | 完済までの支払回数 11回、手数料の合計 7,497円、支払総額 107,497円。金額は3桁区切り | US1-1、FR-013・FR-015 |
| 2 | 1の続きで返済表を見る | 11行あり、spec の表と一致。最終回は支払額 7,497円・残高0円 | US2-1〜US2-6 |
| 3 | 100,000円・10,000円・0.0% | 10回、手数料 0円、支払総額 100,000円 | US1-2 |
| 4 | 1,000円・1,000円・15.0% | 2回。1回目は手数料12円・元金988円・残高12円、2回目は支払額12円・残高0円 | US2-7 |
| 5 | 利用残高を空のまま実行 | 結果は表示されず、利用残高の下に「利用残高を入力してください」 | US3-1 |
| 6 | 毎月の支払額 1500 | 結果は表示されず、毎月の支払額の下に、入力できる額の条件 | US3-3 |
| 7 | 10,000,000円・125,000円・15.0% | 結果は表示されず、毎月の支払額を1回目の手数料より大きくするよう示される | US3-4 |
| 8 | 10,000,000円・127,000円・15.0% | 335回で結果が表示される。返済表は335行 | US3-6 |
| 9 | 1の結果が出たあと、5の入力で再実行 | 前回の結果が消え、エラーだけが表示される | 分割払いと同じ画面の動き |
| 10 | 「分割払い」のタブに切り替える | 分割払いのフォームが表示され、分割払いの quickstart の 4 がそのまま通る | [research.md](research.md) R6・R7 |
| 11 | 画面を開いてから結果を見るまで | 30秒以内に操作でき、実行から1秒以内に結果が出る（8 の335行のときも） | SC-001・SC-005 |

## 5. 本番（EC2）で確かめる

main に入ったあと、自動デプロイ（[ADR 0009](../../docs/adr/0009-デプロイ構成.md)）が終わってから、EC2 の画面で 4 の 1・7・10 を行う
（`docs/sprint-plan.md` Day6 のゴール「リボ払いが EC2 上の画面から使える」）。
