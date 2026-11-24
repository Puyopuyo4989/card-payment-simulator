---
description: "リボ払い（定額方式）の支払シミュレーションのタスク一覧"
---

# タスク：リボ払い（定額方式）の支払シミュレーション

**Input**: `specs/002-revolving-simulation/` の設計文書

**Prerequisites**: [plan.md](plan.md)、[spec.md](spec.md)、[research.md](research.md)、[data-model.md](data-model.md)、[contracts/revolving-simulation-api.md](contracts/revolving-simulation-api.md)、[quickstart.md](quickstart.md)

**Tests**: constitution 原則I（TDD必須）により、すべての本番コードの前に、失敗するテストを書く。
テストのタスクは、対応する実装のタスクより前に置いている。テストを書いたら、**実装の前に失敗すること（red）を確かめる**。
型（Java の record・TypeScript の型）も本番コードなので、それを使うテストを先に書き、コンパイルの失敗を red とする。
共通化（Phase 1・Phase 5）は振る舞いを変えない refactor なので、既存のテストが通ったまま（green のまま）進める。
進め方は Superpowers の test-driven-development に従う（[ADR 0007](../../docs/adr/0007-ツールの使い分け.md)）。

**Organization**: 2つのまとまりに分け、それぞれの中を spec のユーザーストーリーごとのフェーズに分ける（sprint-plan の Day6 の午前・午後）。

| まとまり | フェーズ | 内容 |
|---|---|---|
| **A. 共通化＋バックエンド**（Day6 午前） | Phase 1〜4 | 分割払いのバックエンドの共通化 → US1〜US3 の計算・入力チェック・API |
| **B. 画面（タブ・フロントの共通化）**（Day6 午後） | Phase 5〜8 | フロントの共通化とタブ → US1〜US3 の画面 |
| 仕上げ | Phase 9 | 確認と PR |

A が終われば、API だけで受け入れシナリオを確かめられる（`docs/sprint-plan.md`「遅れたときに削る順番」の2：画面を削っても API は残る）。

## 書き方：`[ID] [P?] [Story] 内容`

- **[P]**：並行して進められる（別のファイルで、終わっていないタスクに依存しない）
- **[Story]**：どのユーザーストーリーのタスクか（US1・US2・US3）
- 内容には、正確なファイルのパスを書く

## パスの決まり（分割払いと同じ案A。[plan.md](plan.md)）

- 以下、`{main}` ＝ `src/main/java/com/example/cardpayment`、`{test}` ＝ `src/test/java/com/example/cardpayment` と書く
- フロントエンド：`frontend/src/`。テストは対象のファイルの隣に `*.test.ts(x)` で置く

## 全タスクで守ること

- 金額は `BigDecimal`。`double`・`float` を使わない（原則III）。丸めは `divide(12000, 0, RoundingMode.DOWN)` で、spec の FR-007 の「各回の手数料」の箇所だけで行う。
- **テストの期待値は spec の受け入れシナリオの表・エッジケースから作る**。spec にない条件の期待値は、人が別途計算して一致を確かめたものだけを使う。実装のコードや、AI が計算した値（research.md R2 の Python の結果を含む）から逆算しない。
- **360回の境界値（949,000円・12,000円・15.0% → 360回、950,000円・12,000円・15.0% → 360回を超える）は spec にない条件なので、T001 で人が確かめてから期待値にする**。確かめるまで、その値を使うテストを書かない。
- 共通化で、分割払いの API の契約（JSON の形・メッセージ）と画面の動きを変えない。分割払いの既存のテストは、import・ファイルの場所・型の名前だけを直し、確かめる内容を変えない。
  - 例外：`frontend/src/App.test.tsx`・`frontend/src/main.test.tsx` の見出しの確認は、plan.md の決定7のとおり「カード支払シミュレーション」に変える（T037。2026-11-24、人が確認済み）
- MVP の範囲外（残高スライド方式・追加の利用・繰上げ返済・保存・比較表・ルーター・ページ分け・入力値の整形・MSW・新しい依存ライブラリ）は作らない（原則VIII）。
- バージョンは分割払いと同じ（Java 25、Spring Boot 4.1 系、Node.js 24 `v24.22.3`）。

---

## Phase 0: 人が行う確認（コードの作業の前）

- [x] T001 【人】360回の境界値を、spec FR-007 の定義どおりに別途計算して確かめる：(1) 949,000円・12,000円・15.0% の完済までの支払回数が360回であること、あわせて手数料の合計・支払総額・360回目の行（支払額・手数料・元金・残高0）、(2) 950,000円・12,000円・15.0% が360回を超えること。確かめた値と方法を、このタスクの下に記録する。**記録があるまで、T019・T027 は書かない**（ほかの条件は先に進めてよい）
  - 2026-11-24：人が確かめた。949,000円・12,000円・15.0% は360回で、手数料の合計 3,364,823円・支払総額 4,313,823円。950,000円・12,000円・15.0% は368回（360回を超える）。T019・T027 はこの値だけを期待値にする（360回目の行の値は確かめていないので、残高0だけを確かめる）

---

# A. 共通化＋バックエンド（Day6 午前）

## Phase 1: Foundational（バックエンドの共通化。分割払いの tasks.md「あと回しにすること」）

**Purpose**: 2機能で同じものを `{main}/common/` に移す（research.md R6、plan.md「人が決めたこと」5・6）。分割払いの動きは変えない。

**⚠️ CRITICAL**: このフェーズが終わるまで、リボ払いのバックエンドのタスクを始めない。各タスクのあとで `./mvnw test` が通ることを確かめる。

- [x] T002 入力エラーの例外を移す：`{main}/installment/web/InstallmentInputException.java` を `{main}/common/web/InputValidationException.java` に名前を変えて移す（中の record `FieldError(String field, String message)` もいっしょに。Javadoc の「amount → paymentCount → annualRate の順」は「項目の順は各機能の contracts のとおり」に直す）。`{main}/common/web/ApiExceptionHandler.java`・`{main}/installment/web/InstallmentInputValidator.java`・`{test}/installment/web/InstallmentInputValidatorTest.java` の参照を直す。`./mvnw test` が通る
- [x] T003 共通の入力の解釈のテストを先に書く：`{test}/common/web/InputRulesTest.java`。`strip`（null → `""`、`" 100 "` → `"100"`）、`isInteger`（`"0"`・`"01000"` → true、`""`・`"1.5"`・`"-1"`・`"１"`・`"1,000"` → false）、`isTooLong`（20文字 → false、21文字 → true）、`checkAnnualRate`（分割払いの `InstallmentInputValidatorTest` の実質年率の例と同じ値・同じメッセージ：未入力 → 「実質年率を入力してください」、`"12.25"`・`"15.00"`・`"-1"`・`".5"`・`"abc"` → 「実質年率は半角数字で、小数第1位までで入力してください（例：12.5）」、`"15.1"`・形式に合っていて21文字以上 → 「実質年率は0.0%以上15.0%以下で入力してください」、`"0"`・`"0.0"`・`"15.0"` → エラーなし。field は `annualRate`）。red を確かめる
- [x] T004 `{main}/common/web/InputRules.java` を作る（static のメソッドだけのクラス。コンストラクタは private）：`InstallmentInputValidator` の private の `strip`・`isTooLong`・`INTEGER`（`[0-9]+`）・`RATE`（`[0-9]+(\.[0-9])?`）・`MAX_LENGTH`（20）・`checkAnnualRate` を移し、`isInteger` を足す。`InstallmentInputValidator` は `InputRules` を使うように直す。T003 が green になり、`InstallmentInputValidatorTest` も確かめる内容を変えずに通る
- [x] T005 返済表の行を移す：`{main}/installment/domain/ScheduleRow.java` を `{main}/common/domain/ScheduleRow.java` に移す（record の項目は変えない：`int number`・`BigDecimal payment`・`BigDecimal principal`・`BigDecimal fee`・`BigDecimal balanceAfter`）。`InstallmentCalculator`・`InstallmentResult`・`InstallmentSimulationResponse`・`{test}/installment/domain/InstallmentCalculatorTest.java`・`InstallmentCalculatorPropertyTest.java` の import を直す。`./mvnw test` が通る
- [x] T006 返済表の行の API の形を分ける（plan.md「人が決めたこと」6）：`{test}/installment/web/InstallmentSimulationControllerTest.java` の `schedule[]` の項目名（`number`・`payment`・`principal`・`fee`・`balanceAfter`）を確かめるテストが、すでにあることを確かめる（なければ先に足す）。そのうえで `{main}/common/web/ScheduleRowResponse.java`（record。同じ5項目と、`static ScheduleRowResponse from(ScheduleRow row)`）を作り、`InstallmentSimulationResponse` の `schedule` を `List<ScheduleRowResponse>` に変える。JSON の形は変わらず、`./mvnw test` が通る
- [x] T007 [P] 分割払いの文書を、移した結果に合わせて直す：`specs/001-installment-simulation/plan.md` のソースコードの構成図（`common/` の中身、`installment/` から消えたファイル）、`specs/001-installment-simulation/data-model.md` 3（`ScheduleRow` の場所）、`specs/001-installment-simulation/tasks.md`「あと回しにすること」の2項目に「2026-11-24、002 の T002〜T006 で対応」と書く

**Checkpoint**: 分割払いのテストがすべて通り、`./mvnw verify` のカバレッジも90%以上のまま。共通のものがそろい、リボ払いを始められる。

---

## Phase 2: User Story 1 - 完済までの回数と手数料を事前に確かめる（バックエンド）(Priority: P1) 🎯 MVP

**Goal**: 利用残高・毎月の支払額・実質年率を API に送ると、完済までの支払回数・手数料の合計・支払総額が返る。

**Independent Test**: `POST /api/revolving-simulations` に 100,000円・10,000円・15.0% → `paymentCount` 11・`totalFee` 7497・`totalPayment` 107497。0.0% → 10・0・100000（spec US1 の1・2）。

**このフェーズの範囲**: 返済表（`schedule`）は計算の途中で作るので、US1 から `RevolvingResult` に入れて返す。返済表の行の値を確かめるテストは US2 で足す。入力は正しい値だけを扱い、エラーは US3 で作る。

### Tests for User Story 1（先に書き、red を確かめる）

- [x] T008 [P] [US1] 計算のテストを書く：`{test}/revolving/domain/RevolvingCalculatorTest.java`（Spring を起動しない）。期待値はすべて spec から：(1) 100,000円・10,000円・15.0% → `paymentCount` 11・`totalFee` 7497・`totalPayment` 107497（US1-1）、(2) 100,000円・10,000円・0.0% → 10・0・100000（US1-2）、(3) 1,000円・1,000円・15.0% → 2・12・1012（US2-7）、(4) 10,000,000円・127,000円・15.0% → `paymentCount` 335（US3-6）、(5) 1,000円・1,000円・0.0% → 1・0・1000（エッジケース「毎月の支払額が利用残高と同じ…0.0%なら1回で完済」）、(6) 支払総額 ＝ 利用残高 ＋ 手数料の合計、(7) すべての金額の `scale()` が 0。(8) 毎月の支払額を大きくすると、回数と手数料の合計が多くならない：100,000円・15.0% で 10,000円の (1) と、20,000円・50,000円・100,000円の結果を比べる（US1-3。値の大小だけを確かめ、20,000円などの具体的な回数は期待値にしない）
- [x] T009 [US1] T008 がコンパイルで失敗すること（red）を確かめてから、計算の入出力の型を作る：`{main}/revolving/domain/RevolvingCondition.java`（record。`BigDecimal balance`・`BigDecimal monthlyPayment`・`BigDecimal annualRate`。値のチェックはここでは行わない。data-model.md 1）、`{main}/revolving/domain/RevolvingResult.java`（record。`int paymentCount`・`BigDecimal totalFee`・`BigDecimal totalPayment`・`List<ScheduleRow> schedule`。`ScheduleRow` は `{main}/common/domain/ScheduleRow`。data-model.md 2）。T008 はまだ失敗のまま
- [x] T010 [P] [US1] 正しい入力の解釈のテストを書く：`{test}/revolving/web/RevolvingInputValidatorTest.java`。`"100000"`・`"10000"`・`"15.0"` → `RevolvingCondition(100000, 10000, 15.0)`、`"15"` → 年率 15、前後の空白は取り除く（`" 100000 "` → 100000）、先頭の0は受け付ける（`"01000"` → 1000）（data-model.md 1）
- [x] T011 [P] [US1] API のテストを書く：`{test}/revolving/web/RevolvingSimulationControllerTest.java`（`@WebMvcTest`）。`POST /api/revolving-simulations` に `{"balance":"100000","monthlyPayment":"10000","annualRate":"15.0"}` → 200・`Content-Type: application/json`・`paymentCount` 11・`totalFee` 7497・`totalPayment` 107497・`schedule` がある
- [x] T012 [US1] 金額が整数のまま返るテストを、T011 と同じファイルに足す：`{"balance":"100000","monthlyPayment":"10000","annualRate":"0.0"}` で、**レスポンスの本文の文字列**に `"totalPayment":100000` が含まれ、`1E+5`・`100000.0` が含まれない（contracts「この契約で確かめるテスト」）。JSON を読み込んでから比べない

### Implementation for User Story 1

- [x] T013 [US1] `{main}/revolving/domain/RevolvingCalculator.java` を作る（data-model.md 4 の 1・3・4）：k ＝ 実質年率 × 10（`intValueExact()`。整数でなければ例外）、残高を利用残高から始め、手数料 ＝ 切り捨て(残高 × k ÷ 12,000)、「残高 ＋ 手数料 ≦ 毎月の支払額」なら最終回（支払額 ＝ 残高 ＋ 手数料、元金 ＝ 残高、残高 ＝ 0）、そうでなければ 支払額 ＝ 毎月の支払額・元金 ＝ 支払額 − 手数料・残高 ＝ 残高 − 元金。手数料の合計 ＝ 各回の手数料の合計、支払総額 ＝ 利用残高 ＋ 手数料の合計、回数 ＝ 行数。整数の値の `BigDecimal` で計算し、割り算は `divide(D, 0, RoundingMode.DOWN)` の1か所だけ。払い終わらないときの扱いは US3（T030）で足す。**US3 までは、払い終わらない入力のときの動きを問わない**が、繰り返しは最大360回で止める（終わらない計算にしない）。T008 が green になる
  - 2026-11-24：360回で止める処理は、それを確かめるテスト（T022）より先に書かないよう、T030 で足すことにした（原則I）。US1・US2 のテストには払い終わらない条件がないので、それまで計算が終わらなくなることはない
- [x] T014 [US1] `{main}/revolving/web/RevolvingInputValidator.java` を作り、正しい入力の文字列を `RevolvingCondition` に変える（`InputRules.strip` で前後の空白を取り除き、`BigDecimal` に変える）。T010 が green になる。エラーの扱いは US3（T031）で足す
- [x] T015 [US1] `{main}/revolving/web/RevolvingSimulationRequest.java`（record。`String balance`・`String monthlyPayment`・`String annualRate`）と `{main}/revolving/web/RevolvingSimulationResponse.java`（record。`int paymentCount`・`BigDecimal totalFee`・`BigDecimal totalPayment`・`List<ScheduleRowResponse> schedule`。`static from(RevolvingResult)`）を作る
- [x] T016 [US1] `{main}/revolving/web/RevolvingSimulationController.java` を作る：`POST /api/revolving-simulations` で、入力の解釈 → 計算 → 200 で返す。T011・T012 が green になる

**Checkpoint**: API だけで、US1 の受け入れシナリオを curl で確かめられる。

---

## Phase 3: User Story 2 - 返済表で回ごとの内訳を見る（バックエンド）(Priority: P2)

**Goal**: API の `schedule` に、1回目から完済する回までの支払額・元金・手数料・支払後の残高が入る。

**Independent Test**: 100,000円・10,000円・15.0% で `schedule` が11行あり、spec US2-6 の表と全行が一致し、支払額の合計 ＝ 支払総額、元金の合計 ＝ 利用残高、最終回の残高 ＝ 0円が成り立つ。

### Tests for User Story 2（先に書き、red を確かめる）

- [x] T017 [P] [US2] 返済表のテストを `{test}/revolving/domain/RevolvingCalculatorTest.java` に足す：(1) 100,000円・10,000円・15.0% の11行が spec US2-6 の表（回・支払額・手数料・元金・支払後の残高）と全行一致、(2) 1,000円・1,000円・15.0% の1回目が支払額1000・手数料12・元金988・残高12、2回目が支払額12・手数料0・元金12・残高0（US2-7）、(3) 100,000円・10,000円・0.0% の10行は手数料がすべて0・支払額がすべて10000（FR-008）、(4) 最終回より前の行の支払額は毎月の支払額と同じ（US2-5）
- [x] T018 [P] [US2] 不変条件のテストを書く：`{test}/revolving/domain/RevolvingCalculatorPropertyTest.java`（`@ParameterizedTest`）。利用残高 1,000・1,001・9,999・12,345・100,000・9,999,999・10,000,000円 × 毎月の支払額 1,000・5,000・10,000・127,000・1,000,000・10,000,000円（利用残高以下のものだけ）× 実質年率 0.0・0.1・7.5・12.5・14.9・15.0% のうち、払い終わる（例外にならない）組み合わせで、「行数 ＝ `paymentCount`」「行数 ≦ 360」「支払額を足したもの ＝ 支払総額（FR-009）」「元金を足したもの ＝ 利用残高（FR-010）」「最終回の残高 ＝ 0（FR-011）」「各回の手数料を足したもの ＝ 手数料の合計」「最終回より前の支払額 ＝ 毎月の支払額、最終回の支払額 ≦ 毎月の支払額」「手数料は負にならず、元金は正、残高は負にならない」（SC-003、data-model.md 3）。払い終わらない組み合わせは、このテストでは除く（US3 の T027 で確かめる）
- [x] T019 [P] [US2] 360回ちょうどのテストを `{test}/revolving/domain/RevolvingCalculatorTest.java` に足す：949,000円・12,000円・15.0% → `paymentCount` 360・`schedule` が360行・360回目の残高0。手数料の合計などの値は T001 で人が確かめた値だけを使う（spec のエッジケース「完済までの回数がちょうど360回のとき：計算できる」）。**T001 の記録があるまで書かない**
- [x] T020 [P] [US2] API のテストを `{test}/revolving/web/RevolvingSimulationControllerTest.java` に足す：100,000円・10,000円・15.0% で `schedule` が11要素、1要素目が `{"number":1,"payment":10000,"principal":8750,"fee":1250,"balanceAfter":91250}`、11要素目が `{"number":11,"payment":7497,"principal":7405,"fee":92,"balanceAfter":0}`（contracts の例）

### Implementation for User Story 2

- [x] T021 [US2] T017〜T020 を green にする：返済表は T013 で作っているので、足りないところだけを `{main}/revolving/domain/RevolvingCalculator.java` に直す。T019 で 360回ちょうどが「360回を超える」扱いにならないこと（361回目を計算する前に止める条件が「360行を足しても最終回でない」であること）を確かめる
  - 2026-11-24：T017〜T020 は、返済表を T013 で作っていたため、書いた時点で通った（red にならなかった）。テストが誤りを見つけられることは、本番コードを一時的に壊して確かめた（mutation check）：①手数料の切り捨てを四捨五入に変える → T017・T019 を含む7件が失敗、②最終回の支払額を毎月の支払額のままにする → T018 の80件ほかが失敗。どちらも元に戻し、本番コードは変えていない。T018 の組み合わせは、360回を超えるもの（利用残高が上限に近く支払額が小さいもの）を除いた（US3 の T022 で確かめる）。360回ちょうどの「超える」扱いの確認は、止める処理を足す T030 で行う

**Checkpoint**: US1 と US2 が API で動く。レスポンスが contracts の 200 の形と一致する。

---

## Phase 4: User Story 3 - 誤った入力に気づいて直す（バックエンド）(Priority: P3)

**Goal**: 未入力・範囲外・形式の誤り・払い終わらない支払額で API を呼ぶと、計算されず、誤っている項目ごとに直し方が分かるメッセージが返る。

**Independent Test**: `{"balance":"","monthlyPayment":"1500","annualRate":"12.25"}` → 400 で `errors` が3件。10,000,000円・126,000円・15.0% → 400 で `monthlyPayment` の「360回を超えます」（spec US3-5・US3-7）。

### Tests for User Story 3（先に書き、red を確かめる）

- [x] T022 [P] [US3] 払い終わらないときのテストを `{test}/revolving/domain/RevolvingCalculatorTest.java` に足す：(1) 10,000,000円・125,000円・15.0%（1回目の手数料が125,000円で支払額と同じ。US3-4）→ `RevolvingNotPayableException`・理由 `FEE_NOT_COVERED`、(2) 10,000,000円・126,000円・15.0%（390回。US3-5）→ 理由 `TOO_MANY_PAYMENTS`、(3) 10,000,000円・127,000円・15.0% → 例外にならず335回（US3-6）
- [x] T023 [US3] T022 がコンパイルで失敗すること（red）を確かめてから、`{main}/revolving/domain/RevolvingNotPayableException.java` を作る（`RuntimeException`。`enum Reason { FEE_NOT_COVERED, TOO_MANY_PAYMENTS }` と `reason()` を持つ。Spring に依存しない。メッセージの文言は持たない。data-model.md 5）
- [x] T024 [P] [US3] 入力チェックのテストを `{test}/revolving/web/RevolvingInputValidatorTest.java` に足す。項目ごとに data-model.md 1 の表の左から順に確かめ、最初に当てはまった1つのエラーを返し、誤っている項目はすべて返す。メッセージは contracts の一覧の文言どおり。境界値と例：
  - 利用残高：未指定（null）・`""`・`"  "` → 「利用残高を入力してください」。`"1000.5"`・`"-1000"`・`"100,000"`・`"１０００"`・`"abc"` → 「利用残高は半角数字（整数）で入力してください」。`"999"`・`"10000001"`・`"1"` ＋ 0を20個 → 「利用残高は1,000円以上10,000,000円以下で入力してください」。`"1000"`・`"10000000"` → エラーなし（FR-002、US3-2）
  - 毎月の支払額：未指定・`""` → 「毎月の支払額を入力してください」。`"1000.5"`・`"-1000"`・`"abc"` → 「毎月の支払額は半角数字（整数）で入力してください」。`"999"`・`"1500"`（US3-3）・`"10000001"`・`"10001000"`・`"1"` ＋ 0を20個 → 「毎月の支払額は1,000円以上10,000,000円以下の、1,000円単位の額で入力してください」。利用残高 10,000円で `"11000"` → 「毎月の支払額は利用残高以下で入力してください」、`"10000"` → エラーなし（FR-004、エッジケース）。`"1000"` → エラーなし
  - 実質年率：分割払いと同じ例とメッセージ（T003 と同じ）。`"12.25"` → 形式のエラー（エッジケース）
  - 項目の関係：利用残高が誤り（`""`）で毎月の支払額 `"20000000"` → 毎月の支払額は範囲のエラーだけ（「利用残高以下」を出さない）。利用残高が誤り（`"999"`）で毎月の支払額 `"5000"` → 毎月の支払額のエラーなし（research.md R4）
  - 3項目とも誤り → 3件。並びは `balance` → `monthlyPayment` → `annualRate`（US3-7）
- [x] T025 [P] [US3] 払い終わらない理由をメッセージに変えるテストを `{test}/revolving/web/RevolvingInputValidatorTest.java` に足す：`FEE_NOT_COVERED` → `InputValidationException` の `errors` が1件で `monthlyPayment`・「毎月の支払額が1回目の手数料以下のため、残高が減りません。毎月の支払額を増やしてください」、`TOO_MANY_PAYMENTS` → `monthlyPayment`・「完済までの回数が360回を超えます。毎月の支払額を増やしてください」
- [x] T026 [P] [US3] API のテストを `{test}/revolving/web/RevolvingSimulationControllerTest.java` に足す：(1) `{"balance":"","monthlyPayment":"1500","annualRate":"12.25"}` → 400・`Content-Type: application/problem+json`・`type` `about:blank`・`title` 「入力に誤りがあります」・`status` 400・`instance` `/api/revolving-simulations`・`errors` が3件。結果の項目（`paymentCount`・`totalFee` など）を含まない（SC-004）。(2) 10,000,000円・125,000円・15.0% → 400・`errors` が `monthlyPayment` の1件で「1回目の手数料以下」のメッセージ（US3-4）。(3) 10,000,000円・126,000円・15.0% → 400・「360回を超えます」のメッセージ（US3-5）。(4) 10,000,000円・127,000円・15.0% → 200・`paymentCount` 335（US3-6）。(5) 本文が JSON として読めないとき → 400・`title` 「リクエストの形式が正しくありません」・`errors` を含まない。(6) 項目が JSON の数値のとき（`{"balance":100000,"monthlyPayment":10000,"annualRate":"15.0"}`）→ 文字列のときと同じ 200 の結果
- [x] T027 [US3] 360回の境界のテストを `{test}/revolving/web/RevolvingSimulationControllerTest.java` に足す：949,000円・12,000円・15.0% → 200・`paymentCount` 360、950,000円・12,000円・15.0% → 400・「360回を超えます」（contracts「この契約で確かめるテスト」）。**T001 の記録があるまで書かない**

### Implementation for User Story 3

- [x] T028 [US3] `{main}/revolving/web/RevolvingInputValidator.java` に入力チェックを足す（data-model.md 1）。利用残高は「必須 → 形式（`InputRules.isInteger`）→ 範囲（1,000以上10,000,000以下、20文字超も範囲の誤り。BigDecimal に変える前に `InputRules.isTooLong` で確かめる）」。毎月の支払額は「必須 → 形式 → 範囲と単位（1,000以上・10,000,000以下・1,000で割り切れる・20文字以内）→ 利用残高以下（利用残高が誤っていないときだけ）」。実質年率は `InputRules.checkAnnualRate`。誤りがあれば `InputValidationException` を投げる。T024 が green になる
- [x] T029 [US3] `{main}/revolving/web/RevolvingInputValidator.java` に、`RevolvingNotPayableException` を `monthlyPayment` の `InputValidationException` に変えるメソッドを足す（文言は contracts の一覧どおり）。T025 が green になる
- [x] T030 [US3] `{main}/revolving/domain/RevolvingCalculator.java` に、払い終わらないときの扱いを足す（data-model.md 4 の 2・3-4）：計算の最初に「1回目の手数料 ≧ 毎月の支払額」なら `FEE_NOT_COVERED`、360行を足しても最終回でなければ `TOO_MANY_PAYMENTS` を投げる（361回目は計算しない）。T022 が green になる
- [x] T031 [US3] `{main}/revolving/web/RevolvingSimulationController.java` で、計算が投げた `RevolvingNotPayableException` を T029 のメソッドで入力エラーに変えて投げる（plan.md「入力チェック」の 1〜3）。`ApiExceptionHandler` は `InputValidationException` を扱うので、変えなくてよい。T026（と T001 のあとは T027）が green になる
  - 2026-11-24：進め方の記録。
    - T022 の red：360回を超える2件は「例外が出ない」で失敗した。1回目の手数料以下の1件は、計算が終わらず返済表の行を足し続けたため、テストを動かす JVM ごと止められた（終了コード143）。テストには、別スレッドの5秒の時間制限を付けている
    - T024 の red：T029 のメソッドだけを先に作ってコンパイルを通し、入力チェックのテストが失敗することを確かめてから T028 を作った
    - T026・T027 の red：払い終わらない3件が、ドメインの例外がそのまま出て失敗することを確かめてから T031 を作った
    - T019（360回ちょうど）と T022（368回）が、T030 の止める処理のあとも通ることを確かめた

- 2026-11-24（まとまり A のあと、`./mvnw clean verify`）：BUILD SUCCESS。バックエンド 732件・フロント 44件がすべて通過。行・分岐カバレッジは、バックエンドが 207/207・78/78、フロントが 51/51・25/25 で、両方とも100%

**Checkpoint**: まとまり A が終わる。3つのストーリーが API で動き、contracts の 200・400 の形と一致する。quickstart.md 3 の curl がそのまま通る。分割払いのテストも通る。

---

# B. 画面（タブ・フロントの共通化）（Day6 午後）

## Phase 5: Foundational（フロントの共通化とタブ）

**Purpose**: 分割払いの画面の部品と動きを `frontend/src/shared/` に移し（research.md R6）、タブで2つの機能を切り替えられるようにする（research.md R7）。分割払いの画面の動きは変えない。

**⚠️ CRITICAL**: このフェーズが終わるまで、リボ払いの画面のタスクを始めない。各タスクのあとで `cd frontend && npm test` が通ることを確かめる。

- [x] T032 API の型の共通部分を移す：`frontend/src/shared/types.ts` を作り、`ScheduleRow`（`number`・`payment`・`principal`・`fee`・`balanceAfter`。すべて number）、`FieldError<F extends string>`（`{ field: F; message: string }`）、`SimulationOutcome<R, F extends string>`（`success`・`inputError`・`unexpectedError`）を置く。`frontend/src/features/installment/types.ts` は、これらを使って分割払いの型（`FieldError<'amount' | 'paymentCount' | 'annualRate'>` など）を定義するように直す。`npm test` が通る
- [x] T033 返済表の部品を移す：`frontend/src/features/installment/ScheduleTable.tsx` と `ScheduleTable.test.tsx` を `frontend/src/shared/` に移し、`shared/types.ts` の `ScheduleRow` を使う。テストの内容は変えない。`InstallmentPage.tsx` の import を直す。`npm test` が通る
- [x] T034 API の呼び出しを移す：`frontend/src/features/installment/api.ts` の中身を `frontend/src/shared/postSimulation.ts`（`postSimulation<Req, R, F>(url, request): Promise<SimulationOutcome<R, F>>`）に移し、`installment/api.ts` は `postSimulation('/api/installment-simulations', request)` を呼ぶだけにする。分割払いの `api.test.ts` は変えずに通す。あわせて `frontend/src/shared/postSimulation.test.ts` を書き、URL を引数のとおりに使うことを確かめる（200・errors のある400・errors のない400・500・通信の失敗の5通り。`api.test.ts` と同じ確かめ方）
- [x] T035 入力エラーの表示を移す：`InstallmentForm.tsx` の `messageOf`・`errorAttributes`・`errorMessage` を `frontend/src/shared/fieldErrors.tsx` に移す（エラーの一覧と項目名を受け取り、入力欄の属性 `aria-invalid`・`aria-describedby="{field}-error"` と、`<p id="{field}-error" className="field-error" role="alert">` を返す）。分割払いの `InstallmentForm.test.tsx` は変えずに通す
- [x] T036 実行中の扱いとフォーカスを移す：`InstallmentPage.tsx` の state と `handleSubmit`（前回の結果を消す・実行中は押せない・例外を予期しないエラーにする・finally で戻す）を `frontend/src/shared/useSimulation.ts`（API の関数を受け取り、`outcome`・`submitting`・`submit` を返す）に、`InstallmentForm.tsx` のフォーカスを戻す `useEffect` を `frontend/src/shared/useReturnFocus.ts`（`submitting` を受け取り、ボタンの ref を返す。戻すのは `document.activeElement` が `body` のときだけ）に移す。分割払いの `InstallmentPage.test.tsx`・`InstallmentForm.test.tsx` は変えずに通す
- [x] T037 タブのテストを先に書く：`frontend/src/App.test.tsx` を直す。(1) 見出し「カード支払シミュレーション」が表示される、(2) `role="tablist"` の中に `role="tab"` の「分割払い」「リボ払い」がある、(3) 最初は「分割払い」が `aria-selected="true"` で、分割払いのフォーム（「支払回数」の選択肢）が表示される、(4) 「リボ払い」を押すと `aria-selected` が入れ替わり、分割払いのフォームが表示されなくなる、(5) 表示中のフォームは `role="tabpanel"` の中にある。`frontend/src/main.test.tsx` の見出しの名前も直す。red を確かめる。（リボ払いのフォームが表示されることは US1 の T044 で確かめる）
- [x] T038 `frontend/src/App.tsx` にタブを作る：`useState` で選んでいるタブを持ち（最初は分割払い）、選んでいないほうは描画しない。WAI-ARIA の tabs の形（`tablist`・`tab`・`tabpanel`、`aria-selected`・`aria-controls`）。ルーターは使わない。リボ払いのタブの中身は、US1 で `RevolvingPage` を作るまで空でよい。T037 が green になる
  - 2026-11-24：進め方の記録。分割払いの `types.ts` は、共通の型を使いつつ同じ名前（`FieldError`・`SimulationOutcome` など）で出し続けるようにしたので、分割払いのテストの import は変えずに済んだ。分割払いのテストで変えたのは、`ScheduleTable.test.tsx` の場所（`shared/` へ。中身は同じ）と、`App.test.tsx`・`main.test.tsx` の見出しだけ。T034 は、analyze の C1 のとおり、`postSimulation.test.ts` を先に書いて red を確かめてから移した。Phase 5 のあと、フロント 54件がすべて通り、カバレッジは 62/62・27/27

**Checkpoint**: 分割払いの画面のテストがすべて確かめる内容を変えずに通り、カバレッジも90%以上のまま。タブで切り替えられる。

---

## Phase 6: User Story 1 - 完済までの回数と手数料を事前に確かめる（画面）(Priority: P1) 🎯 MVP

**Goal**: 「リボ払い」のタブで3項目を入力して実行すると、完済までの支払回数・手数料の合計・支払総額が表示される。

**Independent Test**: 100,000円・10,000円・15.0% で「11回」「7,497円」「107,497円」が表示される。0.0% で「10回」「0円」「100,000円」（spec US1 の1・2）。

### Tests for User Story 1（先に書き、red を確かめる）

- [x] T039 [P] [US1] API の呼び出しのテストを書く：`frontend/src/features/revolving/api.test.ts`。`fetch` を差し替え、`POST /api/revolving-simulations` に JSON の本文（`balance`・`monthlyPayment`・`annualRate`。3項目とも文字列のまま）を送ること、200 のときに結果を返すこと
- [x] T040 [US1] T039 がコンパイルで失敗すること（red）を確かめてから、`frontend/src/features/revolving/types.ts` を作る：リクエスト `{ balance: string; monthlyPayment: string; annualRate: string }`、成功のレスポンス（`paymentCount`・`totalFee`・`totalPayment`・`schedule: ScheduleRow[]`。すべて number）、`FieldError<'balance' | 'monthlyPayment' | 'annualRate'>`。項目名は [contracts](contracts/revolving-simulation-api.md) と同じ
- [x] T041 [P] [US1] 結果の要約のテストを書く：`frontend/src/features/revolving/RevolvingSummary.test.tsx`。完済までの支払回数（「11回」）・手数料の合計（「7,497円」）・支払総額（「107,497円」）を表示する。金額は3桁区切り（FR-013・FR-015）
- [x] T042 [P] [US1] 入力フォームのテストを書く：`frontend/src/features/revolving/RevolvingForm.test.tsx`。「利用残高（円）」「毎月の支払額（円）」「実質年率（%）」の3つとも文字の入力欄（`type="text"`。利用残高・毎月の支払額は `inputMode="numeric"`、実質年率は `inputMode="decimal"`）。実行すると、入力されたままの文字列で送信の処理が呼ばれる。実行中は「計算する」を押せない
- [x] T043 [US1] 画面全体のテストを書く：`frontend/src/features/revolving/RevolvingPage.test.tsx`（`vi.mock` で `./api` を差し替え）。100,000円・10,000円・15.0% を入力して実行すると、11回・7,497円・107,497円が表示される。入力を変えて再実行すると、前回の結果が残らず新しい結果だけが表示される
- [x] T044 [US1] `frontend/src/App.test.tsx` に足す：「リボ払い」のタブを押すと、リボ払いのフォーム（「毎月の支払額（円）」の入力欄）が表示される。red を確かめる

### Implementation for User Story 1

- [x] T045 [P] [US1] `frontend/src/features/revolving/api.ts` を作る：`simulate(request)` が `postSimulation('/api/revolving-simulations', request)` を呼ぶ。T039 が green になる
- [x] T046 [P] [US1] `frontend/src/features/revolving/RevolvingSummary.tsx` を作る（`formatYen` を使う。金額の計算はしない）。T041 が green になる
- [x] T047 [P] [US1] `frontend/src/features/revolving/RevolvingForm.tsx` を作る（`useReturnFocus`・`fieldErrors` を使う）。T042 が green になる
  - 2026-11-24：`fieldErrors`（入力エラーの表示）は、US1 のテストでは確かめないので、ここでは足さず T054 に回した（テストより先に書かないため）。返済表も同じ理由で T050 に回した
- [x] T048 [US1] `frontend/src/features/revolving/RevolvingPage.tsx` を作り（`useSimulation` を使う）、`frontend/src/App.tsx` の「リボ払い」のタブの中に表示する。T043・T044 が green になる

**Checkpoint**: 画面で US1 が動く（MVP）。

---

## Phase 7: User Story 2 - 返済表で回ごとの内訳を見る（画面）(Priority: P2)

**Goal**: 要約の下に、1回目から完済する回までの返済表が表示される。

**Independent Test**: 100,000円・10,000円・15.0% で、返済表が11行表示され、最終行が「11・7,497円・92円・7,405円・0円」。

### Tests for User Story 2（先に書き、red を確かめる）

- [x] T049 [US2] 画面全体のテストを `frontend/src/features/revolving/RevolvingPage.test.tsx` に足す：実行すると、要約の下に返済表（共通の `ScheduleTable`）が表示され、行数が `schedule` の要素数と同じ（API の差し替えで返す11行）。再実行で前回の返済表が残らない（FR-014、US2-1）

### Implementation for User Story 2

- [x] T050 [US2] `frontend/src/features/revolving/RevolvingPage.tsx` で、要約の下に `frontend/src/shared/ScheduleTable.tsx` を表示する。T049 が green になる

**Checkpoint**: 画面で US1 と US2 が動く。

---

## Phase 8: User Story 3 - 誤った入力に気づいて直す（画面）(Priority: P3)

**Goal**: 入力エラーのとき、結果は表示されず、誤っている項目の入力欄のすぐ下にメッセージが赤字で表示される。

**Independent Test**: 利用残高を空、毎月の支払額を 1500、実質年率を 12.25 で実行すると、3つの入力欄のすぐ下に、それぞれのメッセージが表示される（US3-7）。

### Tests for User Story 3（先に書き、red を確かめる）

- [x] T051 [P] [US3] API の呼び出しのテストを `frontend/src/features/revolving/api.test.ts` に足す：400 で `errors` があるときは項目ごとのエラーを返す。`errors` がない 400・500・通信の失敗のときは「予期しないエラー」を返す
- [x] T052 [P] [US3] 入力フォームのテストを `frontend/src/features/revolving/RevolvingForm.test.tsx` に足す：`balance`・`monthlyPayment`・`annualRate` のエラーを渡すと、その項目の入力欄のすぐ下にメッセージを表示する。`role="alert"`・`className="field-error"`、入力欄と `aria-describedby` で結び付き、`aria-invalid` が付く。ポップアップは使わない
- [x] T053 [US3] 画面全体のテストを `frontend/src/features/revolving/RevolvingPage.test.tsx` に足す：入力エラーのとき結果（要約・返済表）は表示されない。正しい結果のあとにエラーの入力で再実行すると、前回の結果が消えてエラーだけが表示される。エラーのあとに正しい入力で再実行すると、エラーが消える。予期しないエラーのときは「計算できませんでした。もう一度お試しください」を表示する。実行が終わったら、フォーカスが外れていれば「計算する」に戻る

### Implementation for User Story 3

- [x] T054 [US3] T051〜T053 を green にする：共通の `postSimulation`・`fieldErrors`・`useSimulation`・`useReturnFocus` で実現しているので、足りないところだけを `frontend/src/features/revolving/RevolvingForm.tsx`・`RevolvingPage.tsx` に足す
  - 2026-11-24：進め方の記録。T051 の4件は、最初から通った（共通の `postSimulation` の動きで、Phase 5 の `postSimulation.test.ts` で red から確かめ済み）。T052 の「ポップアップで出さない」「エラーのない項目には付けない」も、「出さない」ことを確かめるテストなので最初から通った。それ以外の9件は red を確かめてから、`RevolvingForm` に `fieldErrors` を、`RevolvingPage` にエラーの受け渡しと予期しないエラーのメッセージを足した。まとまり B のあと、フロント 83件がすべて通り、カバレッジは 79/79・37/37

**Checkpoint**: まとまり B が終わる。3つのストーリーが画面で動き、分割払いの画面も変わらず動く。

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 仕上げと、PR を出す前の確認（原則VI）。

- [ ] T055 [P] 金額に `double`・`float`・`Math.pow` を使っていないことを確かめる：`grep -rnE '\b(double|float|Double|Float)\b|Math\.pow' src/main/java` が何も出さない。あわせて、`{main}/revolving/domain/RevolvingCalculator.java` を ADR 0005 と spec の FR-006〜FR-008・FR-012 に照らして読む（割り算は `divide(…, 0, RoundingMode.DOWN)` の1か所だけ）。自作スキル①があれば、それで確かめる（原則III）
- [ ] T056 [P] フロントで金額を計算していないことを確かめる：`frontend/src/` で、API の結果の金額に `+`・`-`・`*`・`/` をしていない（表示だけ）
- [ ] T057 `./mvnw verify` で、全テストが通り、行・分岐カバレッジがバックエンド・フロントエンドとも90%以上であることを確かめる（原則II）。結果（件数・カバレッジ）をこのタスクの下に記録する
- [ ] T058 [quickstart.md](quickstart.md) の 1〜4 を上から順に行う（分割払いが変わっていないことの確認と、画面の #1〜#11 を含む）。SC-001・SC-005 は分割払いと同じく人が確かめる（開発者ツールの Network で `POST /api/revolving-simulations` が1秒未満、特に 10,000,000円・127,000円・15.0% の335行のとき。画面を開いてから結果まで30秒以内）
- [ ] T059 PR を出す前に、スキル②（pre-pr-check）を使い、結果の一覧を PR の説明文に貼る（CLAUDE.md）。AI の出力を人が確かめた結果は、人が `docs/ai-review-log.md` に記録する
  - 2026-11-24：人の指示。PR の説明文に「verify の失敗に備えた手順も追加」と書く（コミット `a7d49d6`。CLAUDE.md の「./mvnw verify でテスト用の JVM が止まったとき」と、`.gitignore` の `logs/`）
- [ ] T060 main に入ったあと、EC2 の画面で quickstart.md 5 を確かめる（sprint-plan の Day6 のゴール）

---

## Dependencies & Execution Order

### フェーズの依存

- **Phase 0（T001）**：人の作業。ほかのフェーズと並行してよい。T019・T027 だけが T001 を待つ
- **まとまり A**
  - **Phase 1（バックエンドの共通化）**：すぐ始められる。A のストーリーすべての前提
  - **US1（Phase 2）**：Phase 1 のあと
  - **US2（Phase 3）**：US1 のあと（`RevolvingCalculatorTest.java`・`RevolvingCalculator.java` に足すため）
  - **US3（Phase 4）**：US1 のあと。US2 とは `RevolvingCalculatorTest.java`・`RevolvingSimulationControllerTest.java` が重なるので、同時に触らない
- **まとまり B**
  - **Phase 5（フロントの共通化とタブ）**：Phase 1 に依存しない（フロントだけ）。A と並行してもよいが、sprint-plan どおり A のあとに進めるのを基本にする
  - **US1〜US3（Phase 6〜8）**：Phase 5 のあと。画面のテストは API を `vi.mock` で差し替えるので、A が終わっていなくても進められる。ただし T058（quickstart）は A と B の両方のあと
- **Polish（Phase 9）**：すべてのあと

### ストーリーの中の順番

- テスト（red を確かめる）→ 型（コンパイルの red を確かめてから）→ 実装（green）→ 整える（refactor）
- バックエンド：計算（domain）→ 入力の解釈（web）→ Controller
- フロント：API の呼び出し・部品 → 画面全体

### 並行して進められるもの

- Phase 1：T007（文書）は T002〜T006 と並行できる。T002〜T006 は同じファイルを触るので順番に
- US1（A）：テストの T008・T010・T011 は並行できる（T009 は T008 のあと、T012 は T011 のあと）
- US2（A）：T017・T018・T019・T020 は並行できる（T017 と T019 は同じファイルなので、片方ずつ書く）
- US3（A）：T022・T024・T025・T026 は並行できる（T024 と T025 は同じファイル）
- US1（B）：テストの T039・T041・T042 は並行できる。実装の T045・T046・T047 は並行できる

---

## Parallel Example: User Story 1（まとまり A）

```bash
# US1 のテストを並行して書く（それぞれ red を確かめる）：
Task: "計算のテストを {test}/revolving/domain/RevolvingCalculatorTest.java に書く"
Task: "正しい入力の解釈のテストを {test}/revolving/web/RevolvingInputValidatorTest.java に書く"
Task: "API のテストを {test}/revolving/web/RevolvingSimulationControllerTest.java に書く"
```

## Parallel Example: User Story 1（まとまり B）

```bash
# フロントの部品を並行して作る：
Task: "frontend/src/features/revolving/api.ts を作る"
Task: "frontend/src/features/revolving/RevolvingSummary.tsx を作る"
Task: "frontend/src/features/revolving/RevolvingForm.tsx を作る"
```

---

## Implementation Strategy

### MVP を先に（US1 だけ）

1. Phase 1：バックエンドの共通化（分割払いのテストが通ることを確かめる）
2. Phase 2：US1 のバックエンド
3. **止めて確かめる**：100,000円・10,000円・15.0% と 0.0% を curl で確かめる
4. Phase 5・6：フロントの共通化・タブ・US1 の画面

### 少しずつ届ける（sprint-plan の Day6）

1. 午前：まとまり A（Phase 1〜4）→ curl で quickstart.md 3 を確かめる
2. 午後：まとまり B（Phase 5〜8）→ 画面で quickstart.md 4 を確かめる
3. Polish → スキル② → PR → EC2 で確かめる

### 遅れたとき（research.md R6、sprint-plan「遅れたときに削る順番」）

1. T035・T036（`fieldErrors`・`useSimulation`・`useReturnFocus` の共通化）を後回しにし、リボ払いの画面には分割払いの処理を写す。写したことを、このファイルの「あと回しにすること」に記録する
2. それでも足りなければ、まとまり B（リボ払いの画面）を削り、API だけを残す

---

## Notes

- [P] のタスクは、別のファイルで、依存がない
- [Story] のラベルで、どのストーリーのタスクかを追える
- テストは、実装の前に失敗することを確かめる（原則I）。バグの修正も、再現するテストを先に書く
- 共通化のタスク（T002〜T006・T032〜T036）は、それぞれ1コミットにし、コミットの前に既存のテストが通ることを確かめる
- コミットと push は、git hooks（lefthook・Claude Code の hooks）の確認を通す（[ADR 0008](../../docs/adr/0008-品質の多層防御.md)）
