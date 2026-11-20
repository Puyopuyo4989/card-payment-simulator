---
description: "分割払いの支払シミュレーションのタスク一覧"
---

# タスク：分割払いの支払シミュレーション

**Input**: `specs/001-installment-simulation/` の設計文書

**Prerequisites**: [plan.md](plan.md)、[spec.md](spec.md)、[research.md](research.md)、[data-model.md](data-model.md)、[contracts/installment-simulation-api.md](contracts/installment-simulation-api.md)、[quickstart.md](quickstart.md)

**Tests**: constitution 原則I（TDD必須）により、すべての本番コードの前に、失敗するテストを書く。
テストのタスクは、対応する実装のタスクより前に置いている。テストを書いたら、**実装の前に失敗すること（red）を確かめる**。
型（Java の record・TypeScript の型）も本番コードなので、それを使うテストを先に書き、コンパイルの失敗を red とする。
進め方は Superpowers の test-driven-development に従う（[ADR 0007](../../docs/adr/0007-ツールの使い分け.md)）。

**Organization**: spec のユーザーストーリーごとにフェーズを分け、ストーリーごとに確かめられるようにする。

## 書き方：`[ID] [P?] [Story] 内容`

- **[P]**：並行して進められる（別のファイルで、終わっていないタスクに依存しない）
- **[Story]**：どのユーザーストーリーのタスクか（US1・US2・US3）
- 内容には、正確なファイルのパスを書く

## パスの決まり（[plan.md](plan.md) の案A）

- バックエンド：`src/main/java/com/example/cardpayment/`、テストは `src/test/java/com/example/cardpayment/`
- フロントエンド：`frontend/src/`。テストは対象のファイルの隣に `*.test.ts(x)` で置く
- 以下、`{main}` ＝ `src/main/java/com/example/cardpayment`、`{test}` ＝ `src/test/java/com/example/cardpayment` と書く

## 全タスクで守ること

- 金額は `BigDecimal`。`double`・`float` を使わない（原則III）。丸めは `divide(除数, 0, RoundingMode.DOWN)` で、spec の FR-006・FR-007 の箇所だけで行う。
- テストの期待値は spec の受け入れシナリオの表から作る。spec にない条件の期待値は、人が別途計算して一致を確かめたものだけを使う。実装の式（research.md R7 の変形）から逆算しない。
- MVP の範囲外（保存・比較表・繰上げ返済・DB のライブラリ・MSW・ルーター）は作らない（原則VIII）。
- バージョン：Java 25、Spring Boot 4.1 系（4.2 に乗り換えない）、Node.js 24（`v24.22.3`）（plan.md「人が決めたこと」）。

---

## Phase 1: Setup（土台。sprint-plan の Day3）

**Purpose**: Spring Boot とフロントの雛形を作り、`./mvnw package` 1回でフロントを同梱した jar が1つできる状態にする。

- [x] T001 ルートに Maven のプロジェクトを作る：`pom.xml`（親は `spring-boot-starter-parent` 4.1 系の最新パッチ、`java.version` は 25、groupId `com.example`、artifactId `card-payment-simulator`、依存は `spring-boot-starter-webmvc` と、テスト用に `spring-boot-starter-test`・`spring-boot-starter-webmvc-test`。名前は Spring Boot 4.1 のものを公式のドキュメントで確かめる）。DB のライブラリは入れない（research.md R10）
- [x] T002 Maven Wrapper を入れる：`mvnw`・`mvnw.cmd`・`.mvn/wrapper/maven-wrapper.properties`（Maven 3.10.0 に固定）
- [x] T003 `.gitignore` に `target/`・`frontend/node_modules/`・`frontend/node/`・`frontend/dist/`・`frontend/coverage/` を足す
- [x] T004 [P] 起動のテストを先に書く：`{test}/CardPaymentSimulatorApplicationTests.java`（`@SpringBootTest` でコンテキストが起動する）。red を確かめる
- [x] T005 起動クラスを作る：`{main}/CardPaymentSimulatorApplication.java` と `src/main/resources/application.properties`（`spring.application.name=card-payment-simulator` だけ）。T004 が green になる
- [x] T006 [P] フロントの雛形を作る：`frontend/package.json`（React・TypeScript・Vite・Vitest・@vitest/coverage-v8・@testing-library/react・@testing-library/user-event・@testing-library/jest-dom・jsdom。版は作成時点の最新の安定版、`package-lock.json` で固定）、`frontend/index.html`、`frontend/tsconfig.json`。scripts は `dev`・`build`・`test`（`vitest run`）・`coverage`
- [x] T007 `frontend/vite.config.ts` を作る：出力先は `dist/`（Vite の標準）、開発サーバーは `/api` を `http://localhost:8080` に転送（proxy）、Vitest は `environment: 'jsdom'`・`setupFiles: ['./src/setupTests.ts']`。`frontend/src/setupTests.ts` で `@testing-library/jest-dom/vitest` を読み込む
- [x] T008 [P] 画面のテストを先に書く：`frontend/src/App.test.tsx`（見出し「分割払いシミュレーション」が表示される）と `frontend/src/main.test.tsx`（`<div id="root">` を用意して `main.tsx` を読み込むと、その中に見出しが表示される）。`cd frontend && npm test` で red を確かめる
- [x] T009 `frontend/src/App.tsx` を作って見出しを表示し、`frontend/src/main.tsx` で `#root` に `App` を表示する。T008 が green になる
- [x] T010 `pom.xml` に frontend-maven-plugin 2.0.2 を足す：`workingDirectory` は `frontend`、`nodeVersion` は `v24.22.3`（あとで作る devcontainer.json も同じ版にする。research.md R3）。実行は `install-node-and-npm`・`npm ci`・`npm run build` を `generate-resources`、`npm test` を `test` のフェーズで行う
- [x] T011 `pom.xml` に maven-resources-plugin の `copy-resources` を足し、`process-resources` のフェーズで `frontend/dist/` を `target/classes/static/` にコピーする
- [x] T012 `./mvnw package` を実行し、確かめる：バックエンドとフロントのテストがどちらも動く、`target/` に jar が1つだけできる、`unzip -l target/*.jar | grep static/index.html` で1行出る、`java -jar target/*.jar` で `http://localhost:8080/` に見出しが表示される（[quickstart.md](quickstart.md) 2）
- [x] T013 カバレッジの閾値を設定する：[ADR 0006](../../docs/adr/0006-カバレッジ基準.md) で人が計測ツールと除外対象を決めて Accepted にしたあと、その内容で、行・分岐とも90%未満なら `./mvnw verify` が失敗するようにする（バックエンドは `pom.xml`、フロントは `frontend/vite.config.ts` の `coverage.thresholds`）。**ADR 0006 は Day3 に別のPRで決定予定。決まるまで、このタスクは始めない。ADR にない除外をしない**

**Checkpoint**: 1つの jar で画面が開く。ここで一度 PR を出してよい。

---

## Phase 2: Foundational（全ストーリーが使うもの）

**Purpose**: 金額の表示の部品を用意する。計算とAPIの型は、それを使うテストのあと（US1）で作る（原則I）。

**⚠️ CRITICAL**: このフェーズが終わるまで、ユーザーストーリーのタスクを始めない。

- [x] T014 [P] 金額の表示のテストを先に書く：`frontend/src/shared/formatYen.test.ts`。`100000` → `100,000円`、`0` → `0円`、`9034` → `9,034円`、`12479518` → `12,479,518円`（FR-014）。red を確かめる
- [x] T015 `frontend/src/shared/formatYen.ts` を作る：`Intl.NumberFormat('ja-JP')` で3桁区切りにし「円」を付ける。金額の計算はしない。T014 が green になる

**Checkpoint**: 表示の部品がそろい、ストーリーの実装を始められる。

---

## Phase 3: User Story 1 - 毎月の支払額と手数料を事前に確かめる (Priority: P1) 🎯 MVP

**Goal**: 利用金額・支払回数・実質年率を入力すると、手数料の合計・支払総額・初回と2回目以降の支払額が表示される。

**Independent Test**: 100,000円・12回・15.0% で、手数料の合計 8,309円・支払総額 108,309円・初回 9,034円・2回目以降 9,025円が表示される。100,000円・3回・0.0% で、0円・100,000円・33,334円・33,333円が表示される（spec US1 の1・3）。

**このフェーズの範囲**: 返済表（`schedule`）は US2 で作る。US1 の間、計算は `schedule` に空のリストを返し、API もそれをそのまま返す。入力は正しい値だけを扱い、エラーのメッセージは US3 で作る。

### Tests for User Story 1（先に書き、red を確かめる）

- [x] T016 [P] [US1] 計算のテストを書く：`{test}/installment/domain/InstallmentCalculatorTest.java`（Spring を起動しない）。(1) 100,000円・12回・15.0% → `totalFee` 8309・`totalPayment` 108309・`firstPayment` 9034・`subsequentPayment` 9025、(2) 100,000円・3回・0.0% → 0・100000・33334・33333、(3) 1,000円・36回・15.0% → 247・1247・57・34、(4) 支払総額 ＝ 利用金額 ＋ 手数料の合計、(5) すべての金額の `scale()` が 0、(6) 100,000円・12回・14.9% → `totalFee` 8253・`totalPayment` 108253・`firstPayment` 9022・`subsequentPayment` 9021（月利が割り切れない条件）。上限・下限：(7) 10,000,000円・36回・15.0% → 2479518・12479518・346663・346653、(8) 100,000円・3回・15.0% → 2510・102510・34170・34170（年率が0%でないのに初回と2回目以降が同じ）、(9) 10,000,000円・3回・0.1% → 1666・10001666・3333890・3333888（年率が0%でない下限）。(6)〜(9) の期待値は、仕様の定義どおりに人が別途計算して一致を確かめた値
- [x] T017 [US1] T016 がコンパイルで失敗すること（red）を確かめてから、計算の入出力の型を作る：`{main}/installment/domain/InstallmentCondition.java`（record。`BigDecimal amount`・`int paymentCount`・`BigDecimal annualRate`。値のチェックはここでは行わない。data-model.md 1）、`{main}/installment/domain/ScheduleRow.java`（record。`int number`・`BigDecimal payment`・`BigDecimal principal`・`BigDecimal fee`・`BigDecimal balanceAfter`）、`{main}/installment/domain/InstallmentResult.java`（record。`BigDecimal totalFee`・`BigDecimal totalPayment`・`BigDecimal firstPayment`・`BigDecimal subsequentPayment`・`List<ScheduleRow> schedule`）。項目は data-model.md 2・3 のとおり。T016 はまだ失敗のまま（計算がないため）
- [x] T018 [P] [US1] 正しい入力の解釈のテストを書く：`{test}/installment/web/InstallmentInputValidatorTest.java`。`"100000"`・`"12"`・`"15.0"` → `InstallmentCondition(100000, 12, 15.0)`、`"15"` → 年率 15、前後の空白は取り除く（`" 100000 "` → 100000）、先頭の0は受け付ける（`"01000"` → 1000。人が確認済み）（data-model.md 1）
- [x] T019 [P] [US1] API のテストを書く：`{test}/installment/web/InstallmentSimulationControllerTest.java`（`@WebMvcTest`）。`POST /api/installment-simulations` に `{"amount":"100000","paymentCount":"12","annualRate":"15.0"}` → 200・`Content-Type: application/json`・`totalFee` 8309・`totalPayment` 108309・`firstPayment` 9034・`subsequentPayment` 9025・`schedule` がある
- [x] T020 [US1] 金額が整数のまま返るテストを、T019 と同じファイルに足す：`{"amount":"100000","paymentCount":"3","annualRate":"0.0"}` で、**レスポンスの本文の文字列**に `"totalPayment":100000` が含まれ、`1E+5`・`100000.0` が含まれない（contracts「この契約で確かめるテスト」）。JSON を読み込んでから比べない
- [x] T021 [P] [US1] API の呼び出しのテストを書く：`frontend/src/features/installment/api.test.ts`。`fetch` を差し替え、`POST /api/installment-simulations` に JSON の本文（3項目とも文字列のまま）を送ること、200 のときに結果を返すこと
- [x] T022 [US1] T021 がコンパイルで失敗すること（red）を確かめてから、フロントの API の型を作る：`frontend/src/features/installment/types.ts`。リクエスト `{ amount: string; paymentCount: string; annualRate: string }`、成功のレスポンス（`totalFee`・`totalPayment`・`firstPayment`・`subsequentPayment`・`schedule[]`（`number`・`payment`・`principal`・`fee`・`balanceAfter`）。すべて number）、エラーのレスポンス（`errors: { field: 'amount' | 'paymentCount' | 'annualRate'; message: string }[]`）。項目名は [contracts](contracts/installment-simulation-api.md) と同じにする
- [x] T023 [P] [US1] 結果の要約のテストを書く：`frontend/src/features/installment/InstallmentSummary.test.tsx`。手数料の合計・支払総額を3桁区切りで表示する。初回と2回目以降が異なるとき（9,034円と9,025円）は分けて表示し、同じとき（100,000円・3回・15.0% の 34,170円）は「毎月の支払額」として1つだけ表示する（FR-012）
- [ ] T024 [P] [US1] 入力フォームのテストを書く：`frontend/src/features/installment/InstallmentForm.test.tsx`。利用金額・実質年率は文字列の入力欄（`type="text"`）、支払回数は選択肢「選んでください（空）・3・5・6・10・12・15・18・20・24・30・36回」から選ぶ。実行すると、入力されたままの文字列で送信の処理が呼ばれる（research.md R8）
- [ ] T025 [US1] 画面全体のテストを書く：`frontend/src/features/installment/InstallmentPage.test.tsx`（`vi.mock` で `api.ts` を差し替え）。100,000円・12回・15.0% を入力して実行すると、8,309円・108,309円・9,034円・9,025円が表示される。入力を変えて再実行すると、前回の結果が残らず新しい結果だけが表示される（spec のエッジケース）

### Implementation for User Story 1

- [x] T026 [US1] `{main}/installment/domain/InstallmentCalculator.java` を作り、要約の4項目を計算する（`schedule` は空のリスト）。k ＝ 実質年率 × 10、D ＝ 12,000。k ＝ 0 なら FR-007（手数料0、2回目以降 ＝ 切り捨て(利用金額 ÷ n)、初回 ＝ 利用金額 − 2回目以降 ×（n − 1））。k ＞ 0 なら「手数料の合計 ＝ 切り捨て( (n × P × k × A − P × D × (A − B)) ÷ (D × (A − B)) )、A ＝ (D ＋ k)^n、B ＝ D^n」（research.md R7）、支払総額 ＝ P ＋ 手数料の合計、2回目以降 ＝ 切り捨て(支払総額 ÷ n)、初回 ＝ 支払総額 − 2回目以降 ×（n − 1）。整数の値の `BigDecimal` で計算し、割り算は `divide(…, 0, RoundingMode.DOWN)` だけ。T016 が green になる
- [x] T027 [US1] `{main}/installment/web/InstallmentInputValidator.java` を作り、正しい入力の文字列を `InstallmentCondition` に変える（前後の空白を取り除く。利用金額・支払回数は整数、実質年率は `BigDecimal`）。T018 が green になる。エラーの扱いは US3（T048）で足す。**US3 までは、正しくない入力のときの動きを問わない**（例外で 500 になってもよい）
- [x] T028 [US1] `{main}/installment/web/InstallmentSimulationRequest.java`（record。`String amount`・`String paymentCount`・`String annualRate`）と `{main}/installment/web/InstallmentSimulationResponse.java`（record。`totalFee`・`totalPayment`・`firstPayment`・`subsequentPayment`・`schedule`。`InstallmentResult` から作り、`schedule` も最初から返す。US1 の間は空のリスト）を作る
- [x] T029 [US1] `{main}/installment/web/InstallmentSimulationController.java` を作る：`POST /api/installment-simulations` で、入力の解釈 → 計算 → 200 で返す。T019 が green になる
- [x] T030 [US1] T020 を green にする：金額が `1E+5` などにならないよう、計算の結果の `scale()` を 0 に保つ（`stripTrailingZeros()` を使わない）。それでも指数表記になる場合だけ、Jackson の設定で BigDecimal をそのまま（plain で）書き出す
- [x] T031 [P] [US1] `frontend/src/features/installment/api.ts` を作る：`simulate(request)` が `POST /api/installment-simulations` を呼び、200 なら結果を返す。T021 が green になる
- [x] T032 [P] [US1] `frontend/src/features/installment/InstallmentSummary.tsx` を作る（`formatYen` を使う）。T023 が green になる
- [ ] T033 [P] [US1] `frontend/src/features/installment/InstallmentForm.tsx` を作る。T024 が green になる
- [ ] T034 [US1] `frontend/src/features/installment/InstallmentPage.tsx` を作り、`frontend/src/App.tsx` から表示する。実行するたびに前回の結果を消してから API を呼ぶ。T025 と T008 が green になる

**Checkpoint**: US1 だけで、利用者は分割払いにするかどうかを判断できる（MVP）。

---

## Phase 4: User Story 2 - 返済表で回ごとの内訳を見る (Priority: P2)

**Goal**: 1回目から最終回までの、支払額・手数料・元金・支払後の残高を返済表で確かめられる。

**Independent Test**: 100,000円・12回・15.0% で、返済表が12行あり、spec US2 の受け入れシナリオ5の表と全行が一致し、支払額の合計 ＝ 支払総額、元金の合計 ＝ 利用金額、最終回の残高 ＝ 0円が成り立つ。

### Tests for User Story 2（先に書き、red を確かめる）

- [ ] T035 [P] [US2] 返済表のテストを `{test}/installment/domain/InstallmentCalculatorTest.java` に足す：(1) 100,000円・12回・15.0% の12行が spec US2 の受け入れシナリオ5の表（回・支払額・手数料・元金・支払後の残高）と全行一致、(2) 1,000円・36回・15.0% の36回目が手数料30・元金4・残高0（US2 の6）、(3) 100,000円・3回・0.0% は手数料がすべて0、(4) 100,000円・12回・14.9% の1回目が手数料1241・元金7781・残高92219、12回目が手数料116・元金8905・残高0。上限・下限（人が別途計算して一致を確かめた値）：(5) 10,000,000円・36回・15.0% の1回目が手数料125000・元金221663・残高9778337、36回目が手数料4304・元金342349・残高0、(6) 100,000円・3回・15.0% の1回目が手数料1250・元金32920・残高67080、3回目が手数料422・元金33748・残高0、(7) 10,000,000円・3回・0.1% の1回目が手数料833・元金3333057・残高6666943、3回目が手数料278・元金3333610・残高0
- [ ] T036 [P] [US2] 不変条件のテストを書く：`{test}/installment/domain/InstallmentCalculatorPropertyTest.java`（`@ParameterizedTest`）。利用金額 1,000・1,001・9,999・12,345・100,000・9,999,999・10,000,000円 × 全11種類の支払回数 × 実質年率 0.0・0.1・7.5・12.5・14.9・15.0% の組み合わせで、「行数 ＝ 支払回数」「支払額を足したもの ＝ 支払総額（FR-008）」「元金を足したもの ＝ 利用金額（FR-009）」「最終回の残高 ＝ 0（FR-010）」「各回の手数料を足したもの ＝ 手数料の合計」「元金・残高が負にならない」「初回の支払額 ≧ 2回目以降の支払額」（SC-003、data-model.md 2・3）
- [ ] T037 [P] [US2] API のテストを `{test}/installment/web/InstallmentSimulationControllerTest.java` に足す：100,000円・12回・15.0% で `schedule` が12要素、1要素目が `{"number":1,"payment":9034,"principal":7784,"fee":1250,"balanceAfter":92216}`、12要素目の `balanceAfter` が 0
- [ ] T038 [P] [US2] 返済表の画面のテストを書く：`frontend/src/features/installment/ScheduleTable.test.tsx`。列は左から「回・支払額・手数料・元金・支払後の残高」、行数は `schedule` の要素数、金額は3桁区切り（FR-013・FR-014）
- [ ] T039 [US2] 画面全体のテストを `frontend/src/features/installment/InstallmentPage.test.tsx` に足す：実行すると、要約の下に返済表が表示される。再実行で前回の返済表が残らない

### Implementation for User Story 2

- [ ] T040 [US2] `{main}/installment/domain/InstallmentCalculator.java` に返済表を足す：残高は利用金額から始め、手数料は「最終回の1つ前まで：切り捨て(前回の残高 × k ÷ 12,000)。最終回：手数料の合計 − それまでの各回の手数料を足したもの。年率0%はすべて0円」、元金 ＝ 支払額 − その回の手数料、支払後の残高 ＝ 前回の残高 − その回の元金（FR-006・FR-007）。T035・T036 が green になる。API は T028 で `schedule` を返しているので、T037 もここで green になる
- [ ] T041 [P] [US2] `frontend/src/features/installment/ScheduleTable.tsx` を作る。T038 が green になる
- [ ] T042 [US2] `frontend/src/features/installment/InstallmentPage.tsx` で、要約の下に `ScheduleTable` を表示する。T039 が green になる

**Checkpoint**: US1 と US2 が両方動く。API のレスポンスが contracts の 200 の形と一致する。

---

## Phase 5: User Story 3 - 誤った入力に気づいて直す (Priority: P3)

**Goal**: 未入力や範囲外の値で実行すると、計算されず、誤っている項目ごとに直し方が分かるメッセージが出る。

**Independent Test**: `{"amount":"","paymentCount":"4","annualRate":"12.25"}` で実行すると、結果が表示されず、3つの入力欄のすぐ下に、それぞれのメッセージが赤字で表示される（spec US3 の3）。

### Tests for User Story 3（先に書き、red を確かめる）

- [ ] T043 [P] [US3] 入力チェックのテストを `{test}/installment/web/InstallmentInputValidatorTest.java` に足す。項目ごとに「必須 → 形式 → 範囲」の順で、最初に当てはまった1つのエラーを返し、誤っている項目はすべて返す（data-model.md 1）。境界値と例：
  - 利用金額：未指定（null）・`""`・`"  "` → 「利用金額を入力してください」。`"1000.5"`・`"-1000"`・`"100,000"`（カンマ付き。人が確認済み）・`"１０００"`（全角）・`"abc"` → 「利用金額は半角数字（整数）で入力してください」。`"999"`・`"10000001"` → 「利用金額は1,000円以上10,000,000円以下で入力してください」。`"1000"`・`"10000000"` → エラーなし
  - 支払回数：未指定・`""` → 「支払回数を選んでください」。`"4"`・`"0"`・`"abc"`・`"12.0"` → 「支払回数は3・5・6・10・12・15・18・20・24・30・36回から選んでください」。11種類それぞれ → エラーなし
  - 実質年率：未指定・`""` → 「実質年率を入力してください」。`"12.25"`・`"15.00"`（人が確認済み）・`"-1"`・`"-0.1"`・`".5"`・`"abc"` → 「実質年率は半角数字で、小数第1位までで入力してください（例：12.5）」。`"15.1"`・`"16"` → 「実質年率は0.0%以上15.0%以下で入力してください」。`"0.0"`・`"0"`・`"15.0"` → エラーなし
  - 3項目とも誤り → 3件。並びは `amount` → `paymentCount` → `annualRate`
- [ ] T044 [P] [US3] API のテストを `{test}/installment/web/InstallmentSimulationControllerTest.java` に足す：(1) `{"amount":"","paymentCount":"4","annualRate":"12.25"}` → 400・`Content-Type: application/problem+json`・`type` `about:blank`・`title` 「入力に誤りがあります」・`status` 400・`errors` が3件。結果の項目（`totalFee` など）を含まない（SC-004）。(2) 本文が JSON として読めないとき → 400・`title` 「リクエストの形式が正しくありません」・`errors` を含まない。(3) 項目が JSON の数値のとき（`{"amount":100000,"paymentCount":12,"annualRate":"15.0"}`）→ 文字列のときと同じ 200 の結果（contracts のリクエスト）
- [ ] T045 [P] [US3] API の呼び出しのテストを `frontend/src/features/installment/api.test.ts` に足す：400 で `errors` があるときは項目ごとのエラーを返す。`errors` がない 400、そのほかの失敗（500・通信の失敗）のときは「予期しないエラー」を返す
- [ ] T046 [P] [US3] 入力フォームのテストを `frontend/src/features/installment/InstallmentForm.test.tsx` に足す：エラーを渡すと、その項目の入力欄のすぐ下にメッセージを表示する。メッセージは赤字（色のスタイルまたは CSS のクラスで確かめる）で、入力欄と `aria-describedby` で結び付ける。ポップアップ（`alert`・ダイアログ）は使わない（plan.md「画面」）
- [ ] T047 [US3] 画面全体のテストを `frontend/src/features/installment/InstallmentPage.test.tsx` に足す：エラーのとき結果（要約・返済表）は表示されない。正しい結果のあとにエラーの入力で再実行すると、前回の結果が消えてエラーだけが表示される。エラーのあとに正しい入力で再実行すると、エラーが消える。予期しないエラーのときは「計算できませんでした。もう一度お試しください」を表示する（spec US3、エッジケース、contracts）

### Implementation for User Story 3

- [ ] T048 [US3] `{main}/installment/web/InstallmentInputValidator.java` に入力チェックを足す。形式は利用金額・支払回数が `^[0-9]+$`、実質年率が `^[0-9]+(\.[0-9])?$`。範囲は「利用金額：1,000以上10,000,000以下」「支払回数：3・5・6・10・12・15・18・20・24・30・36 のいずれか」「実質年率：0.0以上15.0以下。小数第2位以下があればエラー（丸めない）」（data-model.md 1）。メッセージは contracts の一覧の文言どおり。エラーがあれば、項目ごとのエラーの一覧を持つ例外 `{main}/installment/web/InstallmentInputException.java` を投げる。T043 が green になる
- [ ] T049 [US3] `{main}/common/web/ApiExceptionHandler.java`（`@RestControllerAdvice`）を作る：入力エラーの例外 → `ProblemDetail`（`title` 「入力に誤りがあります」、`errors` の配列）、JSON が読めない（`HttpMessageNotReadableException`）→ `ProblemDetail`（`title` 「リクエストの形式が正しくありません」）。どちらも 400。JSON の数値を文字列の項目に受け付けるよう、必要なら Jackson の設定を足す。T044 が green になる
- [ ] T050 [US3] `frontend/src/features/installment/api.ts` に 400・予期しないエラーの扱いを足す。T045 が green になる
- [ ] T051 [US3] `frontend/src/features/installment/InstallmentForm.tsx` に、項目ごとのエラーの表示を足す（入力欄のすぐ下、赤字、`aria-describedby`）。T046 が green になる
- [ ] T052 [US3] `frontend/src/features/installment/InstallmentPage.tsx` で、実行するたびに前回の結果とエラーを両方消し、新しい結果かエラーのどちらかだけを表示する。T047 が green になる

**Checkpoint**: 3つのストーリーがすべて動く。API が contracts の 200・400 の形と一致する。

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 仕上げと、PR を出す前の確認（原則VI）。

- [ ] T053 [P] 金額に `double`・`float`・`Math.pow` を使っていないことを確かめる：`grep -rnE '\b(double|float|Double|Float)\b|Math\.pow' src/main/java` が何も出さない。あわせて、自作スキル①（金額計算のレビュー）で `{main}/installment/` を確かめる（原則III、[ADR 0005](../../docs/adr/0005-金額計算と端数処理.md)）
- [ ] T054 [P] フロントで金額を計算していないことを確かめる：`frontend/src/` で、API の結果の金額に `+`・`-`・`*`・`/` をしていない（表示だけ。research.md R6）
- [ ] T055 `./mvnw verify` で、全テストが通り、行・分岐カバレッジがバックエンド・フロントエンドとも90%以上であることを確かめる（原則II。T013 の閾値）
- [ ] T056 [quickstart.md](quickstart.md) の 1〜4 を上から順に行い、すべて期待どおりであることを確かめる（画面の確認 #1〜#8 を含む）。SC-001・SC-005 は自動のテストにせず、次のとおり人が確かめる：Codespaces で `java -jar` で起動し、ブラウザの開発者ツールの Network で `POST /api/installment-simulations` の時間が1秒未満であることを見る。画面を開いてから結果が出るまでを、人がストップウォッチで測り、30秒以内であることを確かめる
- [ ] T057 Superpowers の requesting-code-review と verification-before-completion を通してから PR を出す（[ADR 0007](../../docs/adr/0007-ツールの使い分け.md)）。AI の出力を人が確かめた結果は、人が `docs/ai-review-log.md` に記録する（constitution「開発ワークフローと人とAIの分担」）

---

## Dependencies & Execution Order

### フェーズの依存

- **Setup（Phase 1）**：すぐ始められる。T013 だけは ADR 0006 の決定（Day3 に別のPRで決定予定）を待つ。ほかのフェーズは T013 を待たずに進めてよいが、T055 の前には終える
- **Foundational（Phase 2）**：Setup の T001〜T012 のあと。すべてのストーリーの前提
- **US1（Phase 3）**：Foundational のあと。ほかのストーリーに依存しない
- **US2（Phase 4）**：Foundational のあと。`InstallmentCalculator.java`・`InstallmentPage.tsx` など US1 と同じファイルに足すので、US1 のあとに進める
- **US3（Phase 5）**：Foundational のあと。`InstallmentInputValidator.java`・`InstallmentForm.tsx` など US1 と同じファイルに足すので、US1 のあとに進める。US2 とは別のファイルが多く、US2 と並行してもよい（`InstallmentPage.tsx`・`InstallmentSimulationControllerTest.java`・`InstallmentPage.test.tsx` は同時に触らない）
- **Polish（Phase 6）**：すべてのストーリーのあと

### ストーリーの中の順番

- テスト（red を確かめる）→ 型（コンパイルの red を確かめてから）→ 実装（green）→ 整える（refactor）
- バックエンド：計算（domain）→ 入力の解釈（web）→ Controller
- フロント：API の呼び出し・部品 → 画面全体

### 並行して進められるもの

- Setup：T004 と T006、T008 は T007 のあと
- US1：テストの T016・T018・T019・T021・T023・T024 は並行できる（T017 は T016 のあと、T022 は T021 のあと）。実装の T031・T032・T033 は並行できる
- US2：テストの T035・T036・T037・T038 は並行できる
- US3：テストの T043・T044・T045・T046 は並行できる
- バックエンドの列（domain → web）とフロントの列（api・部品 → 画面）は、ストーリーの中で並行できる

---

## Parallel Example: User Story 1

```bash
# US1 のテストを並行して書く（それぞれ red を確かめる）：
Task: "計算のテストを {test}/installment/domain/InstallmentCalculatorTest.java に書く"
Task: "正しい入力の解釈のテストを {test}/installment/web/InstallmentInputValidatorTest.java に書く"
Task: "API のテストを {test}/installment/web/InstallmentSimulationControllerTest.java に書く"
Task: "API の呼び出しのテストを frontend/src/features/installment/api.test.ts に書く"
Task: "結果の要約のテストを frontend/src/features/installment/InstallmentSummary.test.tsx に書く"
Task: "入力フォームのテストを frontend/src/features/installment/InstallmentForm.test.tsx に書く"

# フロントの部品を並行して作る：
Task: "frontend/src/features/installment/api.ts を作る"
Task: "frontend/src/features/installment/InstallmentSummary.tsx を作る"
Task: "frontend/src/features/installment/InstallmentForm.tsx を作る"
```

---

## Implementation Strategy

### MVP を先に（US1 だけ）

1. Phase 1：Setup（Day3）
2. Phase 2：Foundational
3. Phase 3：US1
4. **止めて確かめる**：100,000円・12回・15.0% と 0.0% のケースを、画面と curl で確かめる
5. 必要なら、ここで PR を出してデモする

### 少しずつ届ける（sprint-plan の Day5）

1. Setup ＋ Foundational → 土台ができる
2. US1 → 確かめる → MVP
3. US2 → 確かめる（返済表が spec の表と一致）
4. US3 → 確かめる（エラーの表示）
5. Polish → PR（EC2 への反映は Day4 に作る CD に従う）

---

## Notes

- [P] のタスクは、別のファイルで、依存がない
- [Story] のラベルで、どのストーリーのタスクかを追える
- テストは、実装の前に失敗することを確かめる（原則I）。バグの修正も、再現するテストを先に書く
- タスクごと、またはまとまりごとにコミットする。コミットと push は、git hooks が入るまで（Day3 まで）人が行う（[ADR 0008](../../docs/adr/0008-品質の多層防御.md)）
- CI（GitHub Actions）・lefthook・Claude Code の hooks は、この機能のタスクには含めない（sprint-plan の Day3・Day5 の別作業）
