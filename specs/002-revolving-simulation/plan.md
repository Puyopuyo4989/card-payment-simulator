# 実装計画：リボ払い（定額方式）の支払シミュレーション

**Branch**: `002-revolving-simulation` | **Date**: 2026-11-24 | **Spec**: [spec.md](spec.md)

**Input**: `specs/002-revolving-simulation/spec.md` の機能仕様

**この計画の位置づけ**: 技術の前提は `docs/adr/` のADRと、分割払いの計画（`specs/001-installment-simulation/plan.md`・research.md・
data-model.md・contracts）に従う。分割払いで決めたこと（バージョン・フォルダ構成・API とエラーの形・入力チェックの順番・テストの方法）は
引き継ぎ、**新しく決める必要があることだけ**を【提案】として書いた。【提案】はAIが下書きし、人がレビューして提案どおりに採用した（2026-11-24）。
理由と他の案は [research.md](research.md) にある。

## 概要

カードの利用者が、利用残高・毎月の支払額・実質年率を入力すると、完済までの支払回数・手数料の合計・支払総額・返済表を、
リボ払い（元利定額方式）にする前に確かめられるようにする。

- バックエンド（Spring Boot）が、入力チェックと計算をすべて行い、結果を JSON で返す。分割払いと同じ構成。
  金額は `BigDecimal` で扱い、丸めは各回の手数料の切り捨ての1か所だけ（[research.md](research.md) R2）。
- フロントエンド（React）は、入力を API に送り、結果とエラーを表示するだけにする。金額の計算はしない。
- 分割払いと同じものは、先に共通の場所に移し、2機能で使う（分割払いの tasks.md で「Day6 で行う」としたもの。R6）。
- 画面は1つのまま、タブで「分割払い」「リボ払い」を切り替える（R7）。この機能は DB を使わない。

## 技術の前提（Technical Context）

| 項目 | 内容 | 根拠 |
|---|---|---|
| 言語／バージョン | Java 25（LTS）、TypeScript 7.0.2。Node.js 24（`v24.22.3`） | 分割払いと同じ（[research.md](research.md) R1） |
| 主なフレームワーク | Spring Boot 4.1 系（Spring Web MVC）、React 19.3.1。スプリント中は 4.2 に乗り換えない | [ADR 0001](../../docs/adr/0001-技術スタック.md)、R1 |
| ビルド | Maven（Maven Wrapper）。frontend-maven-plugin でフロントをビルドし、jar に同梱 | [ADR 0002](../../docs/adr/0002-ビルドツール.md)・[0003](../../docs/adr/0003-リポジトリと成果物.md)、R1 |
| フロントのビルド／テスト | Vite、Vitest ＋ React Testing Library ＋ jsdom | [ADR 0012](../../docs/adr/0012-フロントのビルドとテスト.md)、R1 |
| 追加する依存ライブラリ | なし | R1 |
| 保存 | なし（DB を使わない。結果も保存しない）。アプリの起動には PostgreSQL が要る | spec の Assumptions、[ADR 0011](../../docs/adr/0011-DB.md) |
| テスト | JUnit 5 ＋ AssertJ（計算・入力チェック）、MockMvc（API）、Vitest（画面） | R1 |
| カバレッジ | 行・分岐とも90%以上。除外は ADR 0006 のものだけ | constitution 原則II、[ADR 0006](../../docs/adr/0006-カバレッジ基準.md) |
| 動く場所 | 開発：GitHub Codespaces。本番：EC2 上の docker compose（jar 1つ） | [ADR 0004](../../docs/adr/0004-開発環境.md)・[0009](../../docs/adr/0009-デプロイ構成.md) |
| 種類 | Web アプリ（API ＋ 1画面）。1リポジトリ・1 jar | constitution 原則V |
| 性能の目標 | 入力してから結果の表示まで1秒以内（360回のときも） | SC-005、R8 |
| 制約 | 金額に `double`・`float` を使わない。丸めは FR-007 の「各回の手数料」だけ | constitution 原則III、[ADR 0005](../../docs/adr/0005-金額計算と端数処理.md) |
| 規模 | API 1つを足す。画面はタブを1つ足す。返済表は最大360行 | spec FR-004 |

NEEDS CLARIFICATION は残っていない。spec と ADR と分割払いの計画で決まっていないことは、[research.md](research.md) の【提案】（R3〜R7）にした。

## Constitution Check

*Phase 0 の前に確認し、Phase 1 の設計のあとに再確認した。*

| 原則 | 確認すること | 結果（設計前） | 結果（設計後） |
|---|---|---|---|
| I. TDD必須 | テストを先に書く順で進められるか | OK：計算は Spring なしの単体テストにできる（分割払いと同じ構成） | OK：共通化は、既存のテストが通ったまま行う refactor。新しい処理は、テスト → 実装の順にする（tasks.md） |
| II. カバレッジ90% | 計測できる構成か | OK：ADR 0006 の設定がそのまま使える | OK：計算・入力チェック・例外の変換・API・画面・タブのすべてにテストの方法を決めた。新しい除外はない |
| III. BigDecimal | 金額に `double` を使わないか。端数処理は仕様にあるか | OK：端数処理は spec の FR-007・FR-008 にある | OK：丸めは `divide(12000, 0, RoundingMode.DOWN)` の1か所（R2）。フロントは金額を計算しない |
| IV. カード情報・個人情報はダミー | カード番号・個人情報を扱うか | 対象外：入力・表示・保存しない（spec の Assumptions） | 対象外 |
| V. APIでつなぐ・1リポジトリ・1 jar | 境界が API だけか。成果物が1つか | OK：画面と計算は `POST /api/revolving-simulations` だけでつながる | OK：同じ jar に入る。quickstart で確かめる |
| VI. PRを通す | | OK：この機能も PR で main に入れる。PR の前にスキル②（pre-pr-check）を使う | OK |
| VII. 秘密情報 | 秘密情報が要るか | OK：要らない | OK |
| VIII. シンプルさ | 範囲外のものを作らないか | OK：残高スライド方式・追加の利用・繰上げ返済・保存・比較表は作らない | OK：ルーター・ページ分けは作らない（R7・R8）。分割払いの FR-015〜FR-018（入力欄のカンマと `.0`）にあたるものは、2026-11-25 にユーザーの判断で spec の FR-016〜FR-019（Should）として足し、作る（分割払いと共通の `shared/inputFormat.ts`。tasks.md T064〜T068）。分割払いの FR-019〜FR-022（画面のデザイン）にあたるものは、共通の CSS（`index.css`・`tokens.css`）で当たっている。共通化は、2機能で今すでに同じものだけにした（R6） |
| IX. 日本語 | | OK | OK |

違反はない。「Complexity Tracking」は空。

## プロジェクトの構成

### 文書（この機能）

```text
specs/002-revolving-simulation/
├── spec.md              # 機能仕様（既存）
├── plan.md              # この文書
├── research.md          # Phase 0：引き継ぐことと【提案】
├── data-model.md        # Phase 1：項目と計算の手順
├── quickstart.md        # Phase 1：動作確認の手順
├── contracts/
│   └── revolving-simulation-api.md     # Phase 1：API契約
├── checklists/          # 既存
└── tasks.md             # Phase 2（/speckit-tasks で作る。この計画では作らない）
```

### ソースコード（リポジトリのルート）

分割払いと同じ構成（案A）に、`revolving/` を足す。共通化で移すもの・新しく作るものに印を付けた。
（移）は分割払いから移すもの、（新）は新しく作るもの、（変）は中身を変えるもの。

```text
src/main/java/com/example/cardpayment/
├── common/
│   ├── domain/
│   │   └── ScheduleRow.java                    #（移）installment/domain から
│   └── web/
│       ├── ApiExceptionHandler.java            #（変）InputValidationException を扱う
│       ├── InputValidationException.java       #（移）installment/web/InstallmentInputException から。FieldError を含む
│       ├── InputRules.java                     #（新）strip・isInteger・isTooLong・実質年率のチェック
│       └── ScheduleRowResponse.java            #（新）返済表の行の API の形（JSON は今と同じ）
├── installment/                                #（変）共通のものを使うように直すだけ。動きは変えない
│   ├── domain/  InstallmentCalculator・InstallmentCondition・InstallmentResult
│   └── web/     InstallmentSimulationController・Request・Response・InstallmentInputValidator
└── revolving/                                  #（新）
    ├── domain/                                 # 計算（Spring に依存しない）
    │   ├── RevolvingCalculator.java
    │   ├── RevolvingCondition.java
    │   ├── RevolvingResult.java
    │   └── RevolvingNotPayableException.java   # 理由：FEE_NOT_COVERED・TOO_MANY_PAYMENTS
    └── web/
        ├── RevolvingSimulationController.java
        ├── RevolvingSimulationRequest.java     # 文字列で受ける
        ├── RevolvingSimulationResponse.java
        └── RevolvingInputValidator.java        # 文字列 → RevolvingCondition。払い終わらない理由 → 入力エラー

src/test/java/com/example/cardpayment/
├── common/web/                                 #（新）InputRules の単体テスト
├── installment/                                #（変）import と型の名前だけを直す。確かめる内容は変えない
└── revolving/
    ├── domain/                                 # 計算の単体テスト（spec の表を期待値にする）・性質のテスト
    └── web/                                    # 入力チェックの単体テスト、MockMvc の API テスト

frontend/src/
├── App.tsx                                     #（変）見出しとタブ（R7）
├── shared/
│   ├── formatYen.ts                            # 既存。そのまま使う
│   ├── types.ts                                #（移）ScheduleRow・FieldError・SimulationOutcome
│   ├── postSimulation.ts                       #（移）installment/api.ts の中身
│   ├── fieldErrors.tsx                         #（移）入力欄のエラーの属性とメッセージ
│   ├── useSimulation.ts                        #（移）InstallmentPage の実行中・結果の扱い
│   ├── useReturnFocus.ts                       #（移）InstallmentForm のフォーカスを戻す処理
│   ├── ScheduleTable.tsx                       #（移）installment から
│   └── inputFormat.ts                          #（移・2026-11-25）入力欄のカンマと .0（FR-016〜FR-019）。分割払いの FR-015〜FR-018 と共通
└── features/
    ├── installment/                            #（変）共通のものを使うように直すだけ
    └── revolving/                              #（新）
        ├── api.ts                              # POST /api/revolving-simulations を呼ぶ
        ├── types.ts                            # API の型（contracts と同じ項目名）
        ├── RevolvingPage.tsx
        ├── RevolvingForm.tsx                   # 利用残高・毎月の支払額・実質年率（3つとも文字の入力欄。【Should】フォーカスが外れたら整える）
        ├── RevolvingSummary.tsx                # 完済までの支払回数・手数料の合計・支払総額（FR-013）
        └── *.test.tsx / *.test.ts              # テストは対象のファイルの隣に置く
```

**構成の決定**: 分割払いの案A のまま。ビルドの流れも変わらない。

## 設計の要点

### API

`POST /api/revolving-simulations`。入力は3項目（`balance`・`monthlyPayment`・`annualRate`）を文字列で受け、
結果の金額は円単位の整数で返す。入力エラーは 400 と Problem Details（`errors` に項目ごとのメッセージ）で、形は分割払いと同じ。
詳細は [contracts/revolving-simulation-api.md](contracts/revolving-simulation-api.md)、理由は [research.md](research.md) R3。

### 計算

- `RevolvingCalculator` は Spring に依存しない。入力チェック済みの `RevolvingCondition` を受け取り、`RevolvingResult` を返す。
- spec の FR-007 の表のとおりに、1回目から順に返済表を作る。式の変形は要らない（[research.md](research.md) R2、[data-model.md](data-model.md) 4）。
- 払い終わらないとき（1回目の手数料 ≧ 毎月の支払額、または360回を超える）は、`RevolvingNotPayableException` を投げる。
  361回目は計算しないので、どう呼ばれても終わらない計算にならない。
- テストの期待値は spec の表から作る。FR-009〜FR-011 の性質は `@ParameterizedTest` で多くの条件を確かめる（R2 で Python で確かめた範囲）。

### 入力チェック

- サーバー（`RevolvingInputValidator`）だけで行う。項目ごとに「必須 → 形式 → 範囲」の順で確かめ（分割払いと同じ）、
  毎月の支払額だけは、そのあとに「利用残高以下 → 1回目の手数料より大きい → 360回以内」を確かめる（【提案】[research.md](research.md) R4）。
- 「1回目の手数料」「360回」は計算が要る。Controller は次の流れにする。

  1. `validator.validate(request)` が、項目ごとのチェックと「利用残高以下」を行い、`RevolvingCondition` を返す（誤りがあれば `InputValidationException`）。
  2. `calculator.calculate(condition)` を呼ぶ。
  3. `RevolvingNotPayableException` が出たら、`validator` がその理由を `monthlyPayment` の入力エラー（`InputValidationException`）に変えて投げる。

- 利用残高・実質年率のチェックは、分割払いと同じ共通の `InputRules` を使う（R6）。

### 共通化（分割払いの tasks.md「Day6 で行う」）

- 移すものと移す先は [research.md](research.md) R6 の表（【提案】）。
- **リボ払いを作る前に**、分割払いだけで共通化を行い、分割払いのテストが確かめる内容を変えずに通ることを確かめる。そのあとでリボ払いを作る。
- 分割払いの文書（001 の plan.md の構成図、data-model.md の `ScheduleRow` の場所、tasks.md の「あと回しにすること」）を、移した結果に合わせて直す。

### 画面

- 分割払いの画面の動き（001 の plan.md「画面」）をそのまま使う：入力欄のすぐ下に `role="alert"` の赤字、実行中は「計算する」を押せない、
  終わったらフォーカスをボタンに戻す、API の例外は予期しないエラーにする、実行するたびに前回の結果とエラーを消す。共通化した部品で実現する。
- タブで「分割払い」「リボ払い」を切り替える。見出しは「カード支払シミュレーション」（【提案】[research.md](research.md) R7）。
- 3つの入力欄はどれも文字の入力欄（利用残高・毎月の支払額は `inputMode="numeric"`、実質年率は `inputMode="decimal"`）。
- 結果の要約は、完済までの支払回数（「11回」）・手数料の合計・支払総額（FR-013）。返済表は共通の `ScheduleTable`（FR-014）。
- 金額は共通の `formatYen` で3桁区切りにし、「円」を付ける（FR-015）。

## 実装の順番（tasks.md への引き継ぎ）

`docs/sprint-plan.md` の Day6（午前：計算ロジックとAPI、午後：画面と PR）に合わせる。各ステップは TDD（原則I）で進める。

1. **共通化（バックエンド）**: 例外・`InputRules`・`ScheduleRow`・`ScheduleRowResponse` を移し、分割払いのテストが通ることを確かめる。
2. **計算（P1・P2）**: `RevolvingCalculator`。spec の受け入れシナリオ、FR-008〜FR-011 の性質、360回ちょうど・払い終わらない条件をテストにする。
3. **入力チェック（P3）**: `RevolvingInputValidator`。境界値・形式の誤り・項目の関係（R4 の順番）をテストにする。
4. **API**: Controller と例外の変換。MockMvc で contracts と照らし合わせる。
5. **共通化（フロント）**: `shared/` に移し、分割払いの画面のテストが通ることを確かめる。
6. **画面**: タブ → フォーム → 要約 → 返済表 → エラーの表示 の順。
7. **動作確認**: [quickstart.md](quickstart.md) の手順を通す。PR の前にスキル②（pre-pr-check）を使い、結果を PR の説明文に貼る。

時間が足りないときは、`docs/sprint-plan.md`「遅れたときに削る順番」の2（リボ払いの画面）より先に、
フロントの共通化の一部（`useSimulation`・`useReturnFocus`・`fieldErrors`）を後回しにする（[research.md](research.md) R6）。

## 人が決めたこと

2026-11-24 のレビューで、7項目すべてを提案どおりに採用した。

| # | 決めたこと | 決定 | 日付 | 参照 |
|---|---|---|---|---|
| 1 | API の URL と項目名 | `POST /api/revolving-simulations`。`balance`・`monthlyPayment`・`annualRate` → `paymentCount`・`totalFee`・`totalPayment`・`schedule[]` | 2026-11-24 | R3、contracts |
| 2 | 毎月の支払額のチェックの順番と、ほかの項目に誤りがあるときの扱い | 必須 → 形式 → 範囲と単位 → 利用残高以下 → 1回目の手数料より大きい → 360回以内。後ろの3つは、関係する項目が正しいときだけ確かめる | 2026-11-24 | R4、data-model.md 1 |
| 3 | 計算が要るチェックの作り方 | 計算がドメインの例外（理由つき）を投げ、Web 層がメッセージに変える | 2026-11-24 | R4、data-model.md 5 |
| 4 | エラーのメッセージの文言 | contracts のメッセージの一覧 | 2026-11-24 | R5、contracts |
| 5 | 分割払いと共通にするもの・移す先・名前 | R6 の表（バックエンド4つ、フロント6つ） | 2026-11-24 | R6 |
| 6 | 共通化の範囲を、分割払いの既知の課題（`ScheduleRow` をレスポンスにそのまま使っている）まで広げるか | 広げる（`ScheduleRowResponse` を作り、2機能で使う。JSON の形は変えない） | 2026-11-24 | R6 |
| 7 | 2つの機能の切り替え方と見出し | タブ（ルーターなし）、最初は分割払い、見出しは「カード支払シミュレーション」 | 2026-11-24 | R7 |

必要に応じて ADR に記録する（例：共通化の方針、タブで切り替えること）。

## Complexity Tracking

constitution の違反はないので、記入なし。
