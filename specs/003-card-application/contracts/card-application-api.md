# API契約：カードの申込管理

**状態**: 採用（2026-11-25）。[research.md](../research.md) の R2〜R5 の案A と R6〜R14 の提案を、人がレビューして採用した。
**項目の意味とチェックのルール**: [data-model.md](../data-model.md) を参照。
**これまでの機能とそろえたこと**: エラーの形（RFC 9457 ＋ `errors[]`）、リクエストを文字列で受けること、
金額（ここでは年収）を整数の number で返すことは、`specs/001-…`・`specs/002-…` の contracts と同じ。

- 認証はない（[ADR 0010](../../../docs/adr/0010-認証を作らない.md)）。担当者名は、リクエストに書かれた文字をそのまま保存する。
- カード番号は、どの API の項目にもない（spec FR-021）。
- 例の値は、すべて架空のもの（research.md R11）。
- **Must は 1〜5。Should（キーワード検索・ページ分け）は 6 にまとめた。** 6 を外しても、1〜5 は変わらない。

---

## 共通

### 状態の値

| 値 | 画面の表示名 |
|---|---|
| `APPLIED` | 申込 |
| `UNDER_REVIEW` | 審査中 |
| `APPROVED` | 承認 |
| `REJECTED` | 否決 |

### 日時・日付

- 日時（`appliedAt`・`updatedAt`・`changedAt`）：ISO 8601 の UTC。例：`"2026-11-25T01:30:00Z"`。画面が日本時間にして表示する。
- 生年月日（`birthDate`）：`"YYYY-MM-DD"`。

### エラーの形（Problem Details）

`Content-Type: application/problem+json`。`type` は `about:blank`、`instance` は呼ばれたパス（Spring が付ける）。

| 状況 | status | title | ほかの項目 |
|---|---|---|---|
| 入力の誤り（許されない遷移を含む） | 400 | `入力に誤りがあります` | `errors[]`（`field`・`message`） |
| 本文が JSON として読めない | 400 | `リクエストの形式が正しくありません` | なし |
| 申込が見つからない | 404 | `申込が見つかりません` | `detail`：`申込番号を確かめてください` |
| 楽観ロックでぶつかった | 409 | `ほかの担当者が先に状態を変えました` | `detail`：`最新の状態を確かめてから、もう一度操作してください` |

`errors[]` は、1つの項目につき1つ（data-model.md のチェックの順で最初に当てはまったもの）。並びは各 API に書いた項目の順。

エラーの本文（`title`・`detail`・`errors[].message`）は、この文書に書いた決まった文だけで、**入力された値を埋め込まない**（個人情報を返さない。research.md R11-1）。
予期しないエラー（500）の本文も、Spring の既定のまま（例外のメッセージを返さない）。

---

## 1. 申込を登録する（US1）

```text
POST /api/card-applications
Content-Type: application/json
```

### リクエスト

6項目とも必須で、**文字列**（画面に入力されたまま）。JSON の数値が来ても、文字列として受け付けて同じルールで確かめる。

```json
{
  "nameKanji": "テスト 太郎",
  "nameKana": "テスト　タロウ",
  "birthDate": "1990-04-01",
  "annualIncome": "450",
  "employerName": "テスト株式会社",
  "phoneNumber": "0000000000"
}
```

### レスポンス：成功（201 Created）

- `Location: /api/card-applications/A00000001`
- 本文は、3 の詳細と同じ形（`status` は `APPLIED`、`history` は空、`version` は 0、`appliedAt` と `updatedAt` は同じ）。

### レスポンス：入力エラー（400）

`errors[].field` は `nameKanji` → `nameKana` → `birthDate` → `annualIncome` → `employerName` → `phoneNumber` の順。

```json
{
  "type": "about:blank",
  "title": "入力に誤りがあります",
  "status": 400,
  "instance": "/api/card-applications",
  "errors": [
    { "field": "nameKana", "message": "氏名（カナ）は全角カタカナで入力してください（姓と名の間は全角スペース）" },
    { "field": "annualIncome", "message": "年収は0万円以上9,999万円以下で入力してください" }
  ]
}
```

### メッセージの一覧（登録）

| 項目 | 当てはまる場合 | メッセージ |
|---|---|---|
| `nameKanji` | 未入力・空白だけ | 氏名（漢字）を入力してください |
| `nameKanji` | 50文字を超える | 氏名（漢字）は50文字以内で入力してください |
| `nameKana` | 未入力・空白だけ | 氏名（カナ）を入力してください |
| `nameKana` | 全角カタカナ・長音符・全角スペース以外を含む | 氏名（カナ）は全角カタカナで入力してください（姓と名の間は全角スペース） |
| `nameKana` | 50文字を超える | 氏名（カナ）は50文字以内で入力してください |
| `birthDate` | 未入力 | 生年月日を入力してください |
| `birthDate` | 形の誤り・存在しない日付 | 生年月日は実在する日付を、年-月-日の形で入力してください（例：1990-04-01） |
| `birthDate` | 未来の日付 | 生年月日に未来の日付は入力できません |
| `birthDate` | 申込日に18歳未満・100歳を超える | 申込日の時点で18歳以上100歳以下の方だけが申し込めます。生年月日を確かめてください |
| `annualIncome` | 未入力 | 年収を入力してください |
| `annualIncome` | 小数・負の数・数字以外・カンマ | 年収は半角数字（万円単位の整数）で入力してください |
| `annualIncome` | 9,999を超える（20文字を超えるものも含む） | 年収は0万円以上9,999万円以下で入力してください |
| `employerName` | 未入力・空白だけ | 勤務先名を入力してください |
| `employerName` | 100文字を超える | 勤務先名は100文字以内で入力してください |
| `phoneNumber` | 未入力 | 電話番号を入力してください |
| `phoneNumber` | 0から始まる10桁・11桁の半角数字でない（ハイフン・全角数字を含む） | 電話番号は、0から始まる10桁か11桁の半角数字を、ハイフンなしで入力してください |

---

## 2. 申込の一覧（US2）

```text
GET /api/card-applications
GET /api/card-applications?status=UNDER_REVIEW
```

| パラメーター | 必須 | 内容 |
|---|---|---|
| `status` | － | 省略か空なら「すべて」。4つの状態の値のどれか1つ |

### レスポンス：成功（200 OK）

申込日時の新しい順（同じなら申込番号の大きい順）。0件のときは `"items": []`（エラーにしない。画面がメッセージを出す）。

```json
{
  "items": [
    { "applicationNumber": "A00000003", "nameKanji": "見本 花子", "status": "APPLIED", "appliedAt": "2026-11-25T02:00:00Z" },
    { "applicationNumber": "A00000002", "nameKanji": "テスト 次郎", "status": "UNDER_REVIEW", "appliedAt": "2026-11-25T01:45:00Z" }
  ]
}
```

### レスポンス：入力エラー（400）

| 項目 | 当てはまる場合 | メッセージ |
|---|---|---|
| `status` | 4つの値のどれでもない | 状態は APPLIED・UNDER_REVIEW・APPROVED・REJECTED のどれかを指定してください |

---

## 3. 申込の詳細（US2）

```text
GET /api/card-applications/{applicationNumber}
```

### レスポンス：成功（200 OK）

`history` は変更日時の古い順。否決でない行の `rejectionReason` は `null`。

```json
{
  "applicationNumber": "A00000001",
  "nameKanji": "テスト 太郎",
  "nameKana": "テスト　タロウ",
  "birthDate": "1990-04-01",
  "annualIncome": 450,
  "employerName": "テスト株式会社",
  "phoneNumber": "0000000000",
  "status": "REJECTED",
  "appliedAt": "2026-11-25T01:30:00Z",
  "updatedAt": "2026-11-25T03:10:00Z",
  "version": 2,
  "history": [
    { "changedAt": "2026-11-25T02:00:00Z", "fromStatus": "APPLIED", "toStatus": "UNDER_REVIEW", "staffName": "テスト 担当A", "rejectionReason": null },
    { "changedAt": "2026-11-25T03:10:00Z", "fromStatus": "UNDER_REVIEW", "toStatus": "REJECTED", "staffName": "テスト 担当B", "rejectionReason": "テスト用の否決理由" }
  ]
}
```

### レスポンス：見つからない（404）

形式の誤った申込番号（例：`abc`）も、ないものとして 404 にする。

```json
{
  "type": "about:blank",
  "title": "申込が見つかりません",
  "status": 404,
  "detail": "申込番号を確かめてください",
  "instance": "/api/card-applications/A99999999"
}
```

---

## 4. 状態を変える（US3）

```text
POST /api/card-applications/{applicationNumber}/status-changes
Content-Type: application/json
```

### リクエスト

| 項目 | 型 | 必須 | 内容 |
|---|---|---|---|
| `toStatus` | string | ○ | 変更後の状態 |
| `staffName` | string | ○ | 担当者名（自己申告） |
| `rejectionReason` | string | `toStatus` が `REJECTED` のときだけ | 否決の理由。ほかの状態のときは送られても保存しない |
| `version` | string | ○ | 詳細で受け取った `version`。JSON の数値でもよい |

```json
{
  "toStatus": "REJECTED",
  "staffName": "テスト 担当B",
  "rejectionReason": "テスト用の否決理由",
  "version": "1"
}
```

### 確かめる順番

data-model.md 4 のとおり：JSON → 入力（400）→ 申込があるか（404）→ version（409）→ 遷移（400）→ 保存（0行なら 409）。

入力（400）では、`toStatus` が未入力、または4つの値のどれでもないとき、否決の理由が要るかどうかが決まらないので、
`rejectionReason` は確かめない（`errors[]` に `rejectionReason` は入らない。`staffName`・`version` は確かめる）。

### レスポンス：成功（200 OK）

変更後の詳細（3 と同じ形）。`status`・`updatedAt` が変わり、`version` が1増え、`history` の最後に1行増える。

### レスポンス：入力エラー（400）

`errors[].field` は `toStatus` → `staffName` → `rejectionReason` → `version` の順。

```json
{
  "type": "about:blank",
  "title": "入力に誤りがあります",
  "status": 400,
  "instance": "/api/card-applications/A00000001/status-changes",
  "errors": [
    { "field": "rejectionReason", "message": "否決の理由を入力してください" }
  ]
}
```

### メッセージの一覧（状態の変更）

| 項目 | 当てはまる場合 | メッセージ |
|---|---|---|
| `toStatus` | 未入力 | 変更後の状態を指定してください |
| `toStatus` | 4つの値のどれでもない | 変更後の状態は APPLIED・UNDER_REVIEW・APPROVED・REJECTED のどれかを指定してください |
| `toStatus` | 申込 → 承認・否決（`REVIEW_REQUIRED_FIRST`） | 承認・否決の前に、先に「審査中」にしてください |
| `toStatus` | 承認・否決の申込を変えようとした（`ALREADY_DECIDED`） | 審査が終わった申込の状態は変えられません |
| `toStatus` | そのほかの許されない変更（`NOT_ALLOWED`） | この状態には変えられません。変えられるのは「申込→審査中」「審査中→承認」「審査中→否決」だけです |
| `staffName` | 未入力・空白だけ | 担当者名を入力してください |
| `staffName` | 50文字を超える | 担当者名は50文字以内で入力してください |
| `rejectionReason` | 否決のときに、未入力・空白だけ | 否決の理由を入力してください |
| `rejectionReason` | 否決のときに、500文字を超える | 否決の理由は500文字以内で入力してください |
| `version` | 未入力・半角数字でない・大きすぎる | 画面を開き直してから、もう一度操作してください |

遷移の誤り（`toStatus` の下の3行）は、入力の誤りがなく、申込があり、`version` が合っているときだけ返る。

### レスポンス：見つからない（404）

3 と同じ。

### レスポンス：ほかの担当者とぶつかった（409）

```json
{
  "type": "about:blank",
  "title": "ほかの担当者が先に状態を変えました",
  "status": 409,
  "detail": "最新の状態を確かめてから、もう一度操作してください",
  "instance": "/api/card-applications/A00000001/status-changes"
}
```

- 状態も履歴も変わらない（何も保存しない）。
- 画面は `detail` を表示し、3 の詳細を読み直す。

---

## 5. 登録・変更しないもの（Must の範囲）

- 申込の内容の変更（`PUT`・`PATCH`）と削除（`DELETE`）の API は作らない（FR-006）。これらのメソッドは Spring の既定の 405 になる。
- 一覧の並べ替えのパラメーターは作らない（spec の Assumptions）。

---

## 6. 【Should】キーワード検索とページ分け（FR-012・FR-013）

**Must がすべて終わってから作る。遅れたら、この節ごと外す**（research.md R13）。2 の一覧に、パラメーターと項目を足すだけ。

```text
GET /api/card-applications?keyword=タロウ&status=APPLIED&page=2
```

| パラメーター | 必須 | 内容 |
|---|---|---|
| `keyword` | － | 氏名（漢字）・氏名（カナ）・申込番号のどれかに含む（部分一致）。前後の空白は取り除き、空なら絞り込まない。50文字まで |
| `page` | － | 1から始まる。省略は1。1ページ20件 |

### レスポンスに足す項目

```json
{
  "items": [ "…（最大20件）" ],
  "page": 2,
  "totalPages": 2,
  "totalItems": 25
}
```

### メッセージに足すもの

| 項目 | 当てはまる場合 | メッセージ |
|---|---|---|
| `keyword` | 50文字を超える | キーワードは50文字以内で入力してください |
| `page` | 半角数字でない・0以下 | ページは1以上の半角数字で指定してください |
