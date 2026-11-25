# データモデル（Phase 1）：カードの申込管理

**対象**: [spec.md](spec.md) ／ **計画**: [plan.md](plan.md) ／ **理由**: [research.md](research.md)

この文書は、[research.md](research.md) の R2〜R5 の案A（2026-11-25 のレビューで決定）で書いてある。

- Must の内容は 1〜6。**Should（キーワード検索・ページ分け）は 7 にまとめ**、そこだけ外せるようにした。

---

## 1. 表（Flyway の `V2__card_application.sql`）

`V1__init.sql` は変えない（ADR 0011）。2つの表と1つの連番を V2 で作る（R2 案A）。

### 1.1 `card_application`（申込）

| 列 | 型 | 制約 | 内容 |
|---|---|---|---|
| `application_number` | `varchar(9)` | 主キー。既定値 ＝ `'A' \|\| lpad(nextval('card_application_number_seq')::text, 8, '0')` | 申込番号（R7）。例：`A00000001` |
| `name_kanji` | `varchar(50)` | NOT NULL | 氏名（漢字） |
| `name_kana` | `varchar(50)` | NOT NULL | 氏名（カナ） |
| `birth_date` | `date` | NOT NULL | 生年月日 |
| `annual_income` | `numeric(4, 0)` | NOT NULL、`CHECK (annual_income BETWEEN 0 AND 9999)` | 年収（万円。R5 案A） |
| `employer_name` | `varchar(100)` | NOT NULL | 勤務先名 |
| `phone_number` | `varchar(11)` | NOT NULL、`CHECK (phone_number ~ '^0[0-9]{9,10}$')` | 電話番号 |
| `status` | `varchar(20)` | NOT NULL、`CHECK (status IN ('APPLIED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED'))` | 今の状態 |
| `applied_at` | `timestamptz` | NOT NULL | 申込日時 |
| `updated_at` | `timestamptz` | NOT NULL | 更新日時。登録のときは申込日時と同じ |
| `version` | `integer` | NOT NULL、既定値 0 | 楽観ロック（R4 案A）。状態を変えるたびに1増える |

- 連番 `card_application_number_seq` は `OWNED BY card_application.application_number` にする
  （テストの `TRUNCATE … RESTART IDENTITY` で1に戻すため。R6）。
- 文字の長さ・形式・年齢の範囲は、アプリの入力チェックが正（2）。DB の `CHECK` と `varchar(n)` は、コードの誤りで変な値が入るのを止める最後の守り（R3）。
  年齢は日付によって変わるので、DB の制約にはしない。
- カード番号の列は作らない（FR-021）。削除・内容の変更はしない（FR-006）ので、アプリから `DELETE` と、状態以外の `UPDATE` は出さない。

### 1.2 `card_application_status_history`（状態の変更履歴）

| 列 | 型 | 制約 | 内容 |
|---|---|---|---|
| `id` | `bigint` | `GENERATED ALWAYS AS IDENTITY`、主キー | 行の番号（並び順の決め手にも使う） |
| `application_number` | `varchar(9)` | NOT NULL、外部キー → `card_application` | どの申込の履歴か |
| `changed_at` | `timestamptz` | NOT NULL | 変更日時（＝そのときの申込の `updated_at`） |
| `from_status` | `varchar(20)` | NOT NULL、状態の4つのどれか | 変更前の状態 |
| `to_status` | `varchar(20)` | NOT NULL、状態の4つのどれか | 変更後の状態 |
| `staff_name` | `varchar(50)` | NOT NULL | 担当者名（自己申告。ADR 0010） |
| `rejection_reason` | `varchar(500)` | `CHECK ((to_status = 'REJECTED') = (rejection_reason IS NOT NULL))` | 否決の理由。否決の行だけにあり、ほかの行は NULL |

- 索引：`application_number`（詳細で履歴を読むため）。
- 足すだけで、直さない・消さない。1件の申込に0〜2行（spec の Key Entities）。
- 並び順：`changed_at` の古い順、同じなら `id` の小さい順（FR-018）。

---

## 2. 申込の登録の入力（`CardApplicationRequest` → `NewCardApplication`）

- リクエストは6項目とも**文字列**で受ける（R1）。`InputRules.strip` で前後の空白（全角スペースを含む）を取り除いてから確かめ、取り除いた値を保存する。
- 項目ごとに「必須 → 形式 → 範囲」の順で、最初に当てはまった1つを返す。誤っている項目は**すべて**返す（FR-003）。
  `errors` の並びは下の表の順。
- 文字数はコードポイントで数える（R9）。

| 項目（API） | 必須 | 形式 | 範囲 | チェック後の型 |
|---|---|---|---|---|
| `nameKanji` | ○ | なし | 1〜50文字 | `String` |
| `nameKana` | ○ | `ァ`〜`ヶ`・`ー`・全角スペースだけ（R9） | 1〜50文字 | `String` |
| `birthDate` | ○ | `uuuu-MM-dd` で、実在する日付（STRICT） | ① 未来の日付でない ② 申込日（日本時間）に満18歳以上・満100歳以下（R10） | `LocalDate` |
| `annualIncome` | ○ | 半角数字だけ（`InputRules.isInteger`） | 20文字以下で、0〜9999（`compareTo`） | `BigDecimal`（万円、小数なし） |
| `employerName` | ○ | なし | 1〜100文字 | `String` |
| `phoneNumber` | ○ | `0[0-9]{9,10}` | （形式に含む） | `String` |

- 生年月日の範囲：申込日（日本時間）を `today` として、`Period.between(birthDate, today).getYears()`（満年齢）が 18〜100 かで確かめる。
  - 例（申込日 2026-11-25）：
    - 下の境界：`2008-11-25`（この日に18歳になる）は登録できる、`2008-11-26`（17歳）はエラー。
    - 上の境界：`1925-11-26`（明日101歳になる。今日は100歳）は登録できる、`1925-11-25`（この日に101歳になる）はエラー。
    - `1926-11-25`（この日に100歳になる）も登録できる。
  - 100歳の側の境界は、2026-11-25 に spec のエッジケースを直して決めた（research.md R10）。
  - 申込日は日本時間の日付。日本時間の 0:00〜8:59 に申し込んでもずれないことを確かめるテストの表は research.md R10。
- 年収の端数処理（原則III）：万円単位の整数だけを受け付ける（小数は形式の誤り）。計算も丸めもしない。
- 登録のとき、システムが決める値：申込番号（DB の既定値）、`status = APPLIED`、`applied_at = updated_at = 今`（`Clock` から。マイクロ秒に切り捨て）、`version = 0`。
- 同じ内容を2回登録しても、別々の申込になる（重複のチェックはしない。spec のエッジケース）。

---

## 3. 状態（`ApplicationStatus`）と遷移（R3 案A）

| 値（API・DB） | 表示名 | 次に進める状態 | 最終の状態 |
|---|---|---|---|
| `APPLIED` | 申込 | `UNDER_REVIEW` | いいえ |
| `UNDER_REVIEW` | 審査中 | `APPROVED`・`REJECTED` | いいえ |
| `APPROVED` | 承認 | なし | はい |
| `REJECTED` | 否決 | なし | はい |

```text
  APPLIED（申込）──→ UNDER_REVIEW（審査中）──→ APPROVED（承認）
                                         └──→ REJECTED（否決）
```

許されない遷移は、`CardApplication.changeStatus` が `StatusTransitionException`（理由つき）を投げる。Web 層が理由をメッセージに変える（contracts）。

| 今の状態 | 頼まれた状態 | 理由 | メッセージの意味 |
|---|---|---|---|
| `APPLIED` | `APPROVED`・`REJECTED` | `REVIEW_REQUIRED_FIRST` | 先に「審査中」にする必要がある（US3-6） |
| `APPROVED`・`REJECTED` | どれでも | `ALREADY_DECIDED` | 審査が終わった申込の状態は変えられない（US3-7） |
| `APPLIED` | `APPLIED` | `NOT_ALLOWED` | この状態には変えられない |
| `UNDER_REVIEW` | `APPLIED`・`UNDER_REVIEW` | `NOT_ALLOWED` | 同上 |

4×4＝16 通りのうち、許されるのは3通り（FR-015）。16通りすべてを単体テストで確かめる。

---

## 4. 状態の変更の入力（`StatusChangeRequest`）と処理の順番

| 項目（API） | 必須 | 形式 | 範囲 | 備考 |
|---|---|---|---|---|
| `toStatus` | ○ | 状態の4つの値のどれか | — | 遷移の可否は 3（DB を読んだあと） |
| `staffName` | ○ | なし | 1〜50文字 | 自己申告（FR-016） |
| `rejectionReason` | `toStatus` が `REJECTED` のときだけ ○ | なし | 1〜500文字 | ほかの状態のときは確かめず、保存しない |
| `version` | ○ | 半角数字だけ | 20文字以下で、`int` に収まる | 画面が詳細で受け取った値をそのまま送る |

処理の順番（R4）。どこかで止まったら、その後は行わない。

1. 本文が JSON として読めない → 400（`errors` なし）。
2. 上の表の入力チェック（DB を読まない）→ 誤りがあれば 400 ＋ `errors[]`（並びは `toStatus` → `staffName` → `rejectionReason` → `version`）。
   `toStatus` が未入力、または4つの値のどれでもないときは、否決の理由が要るかどうかが決まらないので、`rejectionReason` は確かめない
   （`errors[]` に `rejectionReason` は入らない。`staffName`・`version` は確かめる）。
3. 申込を読む。ない → 404。
4. 読んだ `version` ≠ 送られた `version` → 409。
5. `CardApplication.changeStatus(toStatus, staffName, rejectionReason, now)` → 許されない遷移なら 400 ＋ `errors[]`（`field: "toStatus"`）。
6. 1つのトランザクションで：`UPDATE … WHERE application_number = ? AND version = ?`（`status`・`updated_at = now`・`version + 1`）→
   0行なら 409（トランザクションを戻す）→ 履歴に1行 `INSERT`（`changed_at = now`）。
7. 200 ＋ 変更後の詳細。

---

## 5. 一覧と詳細（読むだけ）

### 一覧の行（`CardApplicationSummary`）

| 項目（API） | 型 | 内容 |
|---|---|---|
| `applicationNumber` | string | 申込番号 |
| `nameKanji` | string | 氏名（漢字） |
| `status` | string | 状態の値 |
| `appliedAt` | string | 申込日時（ISO 8601、UTC） |

- 並び：`applied_at` の新しい順、同じなら `application_number` の大きい順（R10）。
- 絞り込み：`status` を省略（「すべて」）か、4つの値のどれか1つ（FR-008）。

### 詳細（`CardApplicationDetail`）

一覧の4項目に、`nameKana`・`birthDate`（`YYYY-MM-DD`）・`annualIncome`（整数、万円）・`employerName`・`phoneNumber`・
`updatedAt`・`version`・`history[]` を足したもの。

| `history[]` の項目 | 型 | 内容 |
|---|---|---|
| `changedAt` | string | 変更日時（ISO 8601、UTC） |
| `fromStatus` | string | 変更前の状態 |
| `toStatus` | string | 変更後の状態 |
| `staffName` | string | 担当者名 |
| `rejectionReason` | string または null | 否決の理由（否決の行だけ） |

---

## 6. Java の型（パッケージ `com.example.cardpayment.cardapplication`）

| 型 | 場所 | 役割 |
|---|---|---|
| `ApplicationStatus` | `domain` | 状態の enum。次に進める状態を持つ |
| `CardApplication` | `domain` | 申込（保存されたもの）。`changeStatus` で遷移のルールを確かめ、変更後の申込と履歴の行を返す（自分は変えない） |
| `NewCardApplication` | `domain` | 入力チェックを通った登録の内容（6項目） |
| `StatusHistoryEntry` | `domain` | 履歴の1行 |
| `StatusTransitionException` | `domain` | 許されない遷移。理由（`REVIEW_REQUIRED_FIRST`・`ALREADY_DECIDED`・`NOT_ALLOWED`）を持つ |
| `CardApplicationRepository` | `repository` | `JdbcClient` で読み書きする。`updateStatus` は更新した行の数を返す |
| `CardApplicationService` | `service` | `@Transactional`。登録・一覧・詳細・状態の変更の流れ（4 の 3〜7） |
| `ApplicationNotFoundException`・`StatusConflictException` | `service` | 404・409 のもと。`ApiExceptionHandler` が Problem Details に変える |
| `CardApplicationController` | `web` | 4つの API |
| `CardApplicationInputValidator`・`StatusChangeInputValidator` | `web` | 文字列 → ドメインの型。誤りは `InputValidationException` |
| `*Request`・`*Response` | `web` | API の形（文字列で受け、contracts の形で返す） |

- `Clock` の Bean は共通の設定（`common/config/ClockConfig.java`）に置き、`Clock.system(BusinessTime.JAPAN)` にする。
  日本時間の定数 `BusinessTime.JAPAN`（`ZoneId.of("Asia/Tokyo")`）は `common/time/BusinessTime.java` に置く。
  申込日は `LocalDate.ofInstant(clock.instant(), BusinessTime.JAPAN)`。サーバーの既定のタイムゾーンには頼らない（research.md R10）。
- 個人情報を持つ record（`NewCardApplication`・`CardApplication`・`StatusHistoryEntry`・`*Request`）は `toString` を上書きし、
  申込番号（ないものは型の名前）だけを返す（research.md R11-1）。

---

## 7. 【Should】キーワード検索とページ分け（FR-012・FR-013）

**ここに書いたものは、Must がすべて終わってから作る。遅れたら、この節ごと外す**（research.md R13）。

### 一覧の条件に足すもの

| パラメーター | 必須 | ルール | 誤りのとき |
|---|---|---|---|
| `keyword` | － | 前後の空白を取り除く。空なら絞り込まない。1〜50文字。`name_kanji`・`name_kana`・`application_number` のどれかに含む（部分一致、`ILIKE`、`%`・`_`・`\` はエスケープ） | 400（`field: "keyword"`） |
| `page` | － | 省略は1。半角数字で1以上。1ページ20件 | 400（`field: "page"`） |

- `status` と組み合わせたときは、両方に合うもの（AND）。並びは Must と同じ。
- 最後のページより後を頼んだときは、空の `items`（エラーにしない）。

### 一覧のレスポンスに足す項目

| 項目 | 型 | 内容 |
|---|---|---|
| `page` | integer | 今のページ（1から） |
| `totalPages` | integer | ページの数。0件のときは0 |
| `totalItems` | integer | 条件に合う申込の件数 |

- DB の変更はない（V2 のまま。索引も足さない）。
