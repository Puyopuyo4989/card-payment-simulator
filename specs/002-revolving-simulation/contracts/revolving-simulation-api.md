# API契約：リボ払い（定額方式）の支払シミュレーション

**状態**: 採用（2026-11-24）。URL・項目名・メッセージの文言は、人がレビューして提案どおりに採用した。選んだ理由は [research.md](../research.md) R3〜R5 を参照。
**項目の意味と計算**: [data-model.md](../data-model.md) を参照。
**分割払いとそろえたこと**: エラーの形、リクエストを文字列で受けること、金額を整数で返すことは、
`specs/001-installment-simulation/contracts/installment-simulation-api.md` と同じ。

## エンドポイント

```text
POST /api/revolving-simulations
Content-Type: application/json
```

- 計算だけを行い、何も保存しない。同じ入力には、いつも同じ結果を返す。
- 認証はない（[ADR 0010](../../../docs/adr/0010-認証を作らない.md)）。

## リクエスト

| 項目 | 型 | 必須 | 内容 |
|---|---|---|---|
| `balance` | string | ○ | 利用残高（円）。画面に入力されたままの文字列 |
| `monthlyPayment` | string | ○ | 毎月の支払額（円） |
| `annualRate` | string | ○ | 実質年率（%）。例：`"15.0"`・`"15"` |

3項目とも、画面に入力された値を**変換しないで**文字列のまま送る。値の解釈とチェックはサーバーが行う
（[data-model.md](../data-model.md)「入力（文字列）の解釈のルール」）。
項目に JSON の数値が来たとき（例：`"balance": 100000`）も、文字列として受け付け、同じルールで確かめる（分割払いと同じ）。

```json
{
  "balance": "100000",
  "monthlyPayment": "10000",
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
| `paymentCount` | integer | 完済までの支払回数（1〜360） |
| `totalFee` | integer | 手数料の合計 |
| `totalPayment` | integer | 支払総額 |
| `schedule` | array | 返済表。1回目から完済する回まで、回の順に並ぶ。要素数 ＝ `paymentCount` |
| `schedule[].number` | integer | 回（1〜`paymentCount`） |
| `schedule[].payment` | integer | 支払額 |
| `schedule[].principal` | integer | 元金 |
| `schedule[].fee` | integer | 手数料 |
| `schedule[].balanceAfter` | integer | 支払後の残高 |

`schedule[]` の項目は、分割払いの API と同じ。

例（spec の User Story 2 の受け入れシナリオ6。途中の行は省略）：

```json
{
  "paymentCount": 11,
  "totalFee": 7497,
  "totalPayment": 107497,
  "schedule": [
    { "number": 1,  "payment": 10000, "principal": 8750, "fee": 1250, "balanceAfter": 91250 },
    { "number": 2,  "payment": 10000, "principal": 8860, "fee": 1140, "balanceAfter": 82390 },
    { "number": 11, "payment": 7497,  "principal": 7405, "fee": 92,   "balanceAfter": 0 }
  ]
}
```

## レスポンス：入力エラー（400 Bad Request）

```text
Content-Type: application/problem+json
```

RFC 9457（Problem Details）の形。分割払いと同じ形で、誤っている項目は、すべて `errors` に入る（FR-005）。
1つの項目につき、エラーは1つ（[data-model.md](../data-model.md) のチェックの順で、最初に当てはまったもの）。
`errors` の並びは `balance` → `monthlyPayment` → `annualRate` の順。

| 項目 | 型 | 内容 |
|---|---|---|
| `type` | string | `about:blank` |
| `title` | string | `入力に誤りがあります` |
| `status` | integer | `400` |
| `instance` | string | 呼ばれたパス（`/api/revolving-simulations`）。Spring が付ける |
| `errors` | array | 項目ごとのエラー |
| `errors[].field` | string | `balance`・`monthlyPayment`・`annualRate` のいずれか |
| `errors[].message` | string | 画面にそのまま表示する、直し方が分かるメッセージ |

例（利用残高が未入力で、毎月の支払額が 1500 のとき）：

```json
{
  "type": "about:blank",
  "title": "入力に誤りがあります",
  "status": 400,
  "instance": "/api/revolving-simulations",
  "errors": [
    { "field": "balance", "message": "利用残高を入力してください" },
    { "field": "monthlyPayment", "message": "毎月の支払額は1,000円以上10,000,000円以下の、1,000円単位の額で入力してください" }
  ]
}
```

例（10,000,000円・126,000円・15.0%。完済までに390回かかる。spec US3-5）：

```json
{
  "type": "about:blank",
  "title": "入力に誤りがあります",
  "status": 400,
  "instance": "/api/revolving-simulations",
  "errors": [
    { "field": "monthlyPayment", "message": "完済までの回数が360回を超えます。毎月の支払額を増やしてください" }
  ]
}
```

### メッセージの一覧【提案】

| 項目 | 当てはまる場合 | メッセージ |
|---|---|---|
| `balance` | 未入力 | 利用残高を入力してください |
| `balance` | 形式の誤り（小数・負の数・数字以外） | 利用残高は半角数字（整数）で入力してください |
| `balance` | 範囲外。半角数字だけで、20文字を超えるものも含む | 利用残高は1,000円以上10,000,000円以下で入力してください |
| `monthlyPayment` | 未入力 | 毎月の支払額を入力してください |
| `monthlyPayment` | 形式の誤り（小数・負の数・数字以外） | 毎月の支払額は半角数字（整数）で入力してください |
| `monthlyPayment` | 範囲外・1,000円単位でない。20文字を超えるものも含む | 毎月の支払額は1,000円以上10,000,000円以下の、1,000円単位の額で入力してください |
| `monthlyPayment` | 利用残高を超える（利用残高が正しいときだけ） | 毎月の支払額は利用残高以下で入力してください |
| `monthlyPayment` | 1回目の手数料以下（3項目とも、ここまでのチェックを通ったときだけ） | 毎月の支払額が1回目の手数料以下のため、残高が減りません。毎月の支払額を増やしてください |
| `monthlyPayment` | 完済までの回数が360回を超える（同上） | 完済までの回数が360回を超えます。毎月の支払額を増やしてください |
| `annualRate` | 未入力 | 実質年率を入力してください |
| `annualRate` | 形式の誤り（小数第2位以下・負の数・数字以外） | 実質年率は半角数字で、小数第1位までで入力してください（例：12.5） |
| `annualRate` | 範囲外。形式に合っていて、20文字を超えるものも含む | 実質年率は0.0%以上15.0%以下で入力してください |

- 実質年率のメッセージは、分割払いとまったく同じ（共通の処理で返す）。
- 「1回目の手数料以下」「360回を超える」は、ほかの項目に誤りがあるときは確かめない（[research.md](../research.md) R4）。

## レスポンス：リクエストの形が壊れているとき（400 Bad Request）

分割払いと同じ。本文が JSON として読めないときは、`errors` を持たない Problem Details を返す。
画面は共通のメッセージ（「計算できませんでした。もう一度お試しください」）を表示する。

```json
{
  "type": "about:blank",
  "title": "リクエストの形式が正しくありません",
  "status": 400,
  "instance": "/api/revolving-simulations"
}
```

## この契約で確かめるテスト

- 200：spec の受け入れシナリオ（User Story 1 の1・2、User Story 2 の6・7、User Story 3 の6）の値が、上の項目名でそのまま返る。
- 200：完済までちょうど360回の条件（949,000円・12,000円・15.0%）で、`paymentCount` が 360、`schedule` が360行。
- 200：金額は指数表記（`1E+5` など）や小数点付きにならず、整数のまま返る。年率0%・100,000円・10,000円のケース（`totalPayment` が `100000`）で、本文の文字列を確かめる。
- 400：メッセージの一覧の各行が、その条件で返る（spec US3-1〜US3-5。US3-4 は 10,000,000円・125,000円・15.0%）。複数の項目が誤っているとき、すべてが返る（US3-7）。
- 400：利用残高が誤っているときは「利用残高以下」を、どれかの項目が誤っているときは「1回目の手数料以下」「360回を超える」を返さない。
- 400 のとき、結果の項目（`paymentCount`・`totalFee` など）は含まれない（SC-004）。
