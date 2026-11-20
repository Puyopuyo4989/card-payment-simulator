# API契約：分割払いの支払シミュレーション

**状態**: 採用（2026-11-17）。選んだ理由は [research.md](../research.md) R6 を参照。
**項目の意味と計算**: [data-model.md](../data-model.md) を参照。

## エンドポイント

```text
POST /api/installment-simulations
Content-Type: application/json
```

- 計算だけを行い、何も保存しない。同じ入力には、いつも同じ結果を返す。
- 認証はない（[ADR 0010](../../../docs/adr/0010-認証を作らない.md)）。

## リクエスト

| 項目 | 型 | 必須 | 内容 |
|---|---|---|---|
| `amount` | string | ○ | 利用金額（円）。画面に入力されたままの文字列 |
| `paymentCount` | string | ○ | 支払回数 |
| `annualRate` | string | ○ | 実質年率（%）。例：`"15.0"`・`"15"` |

3項目とも、画面に入力された値を**変換しないで**文字列のまま送る。値の解釈とチェックはサーバーが行う
（[data-model.md](../data-model.md)「入力（文字列）の解釈のルール」）。
ただし利用金額は、画面が送る前にカンマを取り除く（spec の FR-015）。そのため、カンマ付きの利用金額の形式の誤りは
画面からは起きない。API を直接呼ぶ場合のために、このルール（カンマ付きは形式の誤り）は残す（FR-017）。

項目に JSON の数値が来たとき（例：`"amount": 100000`）も、文字列として受け付け、同じルールで確かめる。

```json
{
  "amount": "100000",
  "paymentCount": "12",
  "annualRate": "15.0"
}
```

## レスポンス：成功（200 OK）

```text
Content-Type: application/json
```

金額はすべて円単位の整数（number）。

| 項目 | 型 | 内容 |
|---|---|---|
| `totalFee` | integer | 手数料の合計 |
| `totalPayment` | integer | 支払総額 |
| `firstPayment` | integer | 初回の支払額 |
| `subsequentPayment` | integer | 2回目以降の支払額 |
| `schedule` | array | 返済表。1回目から最終回まで、回の順に並ぶ。要素数 ＝ 支払回数 |
| `schedule[].number` | integer | 回（1〜支払回数） |
| `schedule[].payment` | integer | 支払額 |
| `schedule[].principal` | integer | 元金 |
| `schedule[].fee` | integer | 手数料 |
| `schedule[].balanceAfter` | integer | 支払後の残高 |

例（spec の User Story 2 の受け入れシナリオ5。途中の行は省略）：

```json
{
  "totalFee": 8309,
  "totalPayment": 108309,
  "firstPayment": 9034,
  "subsequentPayment": 9025,
  "schedule": [
    { "number": 1,  "payment": 9034, "principal": 7784, "fee": 1250, "balanceAfter": 92216 },
    { "number": 2,  "payment": 9025, "principal": 7873, "fee": 1152, "balanceAfter": 84343 },
    { "number": 12, "payment": 9025, "principal": 8909, "fee": 116,  "balanceAfter": 0 }
  ]
}
```

## レスポンス：入力エラー（400 Bad Request）

```text
Content-Type: application/problem+json
```

RFC 9457（Problem Details）の形。誤っている項目は、すべて `errors` に入る（FR-005）。
1つの項目につき、エラーは1つ（[data-model.md](../data-model.md) のチェックの順で、最初に当てはまったもの）。
`errors` の並びは `amount` → `paymentCount` → `annualRate` の順。

| 項目 | 型 | 内容 |
|---|---|---|
| `type` | string | `about:blank` |
| `title` | string | `入力に誤りがあります` |
| `status` | integer | `400` |
| `errors` | array | 項目ごとのエラー |
| `errors[].field` | string | `amount`・`paymentCount`・`annualRate` のいずれか |
| `errors[].message` | string | 画面にそのまま表示する、直し方が分かるメッセージ |

例（利用金額が未入力で、実質年率が 12.25 のとき）：

```json
{
  "type": "about:blank",
  "title": "入力に誤りがあります",
  "status": 400,
  "errors": [
    { "field": "amount", "message": "利用金額を入力してください" },
    { "field": "annualRate", "message": "実質年率は半角数字で、小数第1位までで入力してください（例：12.5）" }
  ]
}
```

### メッセージの一覧

| 項目 | 当てはまる場合 | メッセージ |
|---|---|---|
| `amount` | 未入力 | 利用金額を入力してください |
| `amount` | 形式の誤り（小数・負の数・数字以外） | 利用金額は半角数字（整数）で入力してください |
| `amount` | 範囲外 | 利用金額は1,000円以上10,000,000円以下で入力してください |
| `paymentCount` | 未入力 | 支払回数を選んでください |
| `paymentCount` | 形式の誤り・一覧にない回数 | 支払回数は3・5・6・10・12・15・18・20・24・30・36回から選んでください |
| `annualRate` | 未入力 | 実質年率を入力してください |
| `annualRate` | 形式の誤り（小数第2位以下・負の数・数字以外） | 実質年率は半角数字で、小数第1位までで入力してください（例：12.5） |
| `annualRate` | 範囲外 | 実質年率は0.0%以上15.0%以下で入力してください |

## レスポンス：リクエストの形が壊れているとき（400 Bad Request）

本文が JSON として読めないときは、`errors` を持たない Problem Details を返す。
画面からは起きない（画面はいつも正しい JSON を送る）ので、画面は共通のメッセージ（例：「計算できませんでした。もう一度お試しください」）を表示する。

```json
{
  "type": "about:blank",
  "title": "リクエストの形式が正しくありません",
  "status": 400
}
```

## この契約で確かめるテスト

- 200：spec の受け入れシナリオ（User Story 1 の1・3、User Story 2 の5・6）の値が、上の項目名でそのまま返る。
- 200：金額は指数表記（`1E+5` など）や小数点付き（`100000.0` など）にならず、整数のまま返る。
  年率0%・100,000円・3回のケース（`totalPayment` が `100000`）で、レスポンスの本文の文字列を確かめる。
- 400：メッセージの一覧の各行が、その条件で返る。複数の項目が誤っているとき、すべてが返る。
- 400 のとき、結果の項目（`totalFee` など）は含まれない（SC-004）。
