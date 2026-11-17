# 実装計画：分割払いの支払シミュレーション

**Branch**: `001-installment-simulation` | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md)

**Input**: `specs/001-installment-simulation/spec.md` の機能仕様

**この計画の位置づけ**: 技術の前提は `docs/adr/` のADRに従う。ADRで決まっていなかった項目
（バージョン・フォルダ構成・APIの形など）は、AIが提案し、人がレビューして提案どおりに採用した（2026-11-17）。
採用した項目の理由と他の案は [research.md](research.md) にある。

## 概要

カードの利用者が、利用金額・支払回数・実質年率を入力すると、毎月の支払額・手数料の合計・
支払総額・返済表を、分割払いにする前に確かめられるようにする。

- バックエンド（Spring Boot）が、入力チェックと計算をすべて行い、結果を JSON で返す。
  金額は `BigDecimal` で扱い、割り算を最後の1回にまとめて誤差なく計算する（[research.md](research.md) R7）。
- フロントエンド（React）は、入力を API に送り、結果とエラーを表示するだけにする。金額の計算はしない。
- フロントのビルド結果を Spring Boot の jar に同梱し、1つの jar で配信する。DB は使わない。

## 技術の前提（Technical Context）

| 項目 | 内容 | 根拠 |
|---|---|---|
| 言語／バージョン | Java 25（LTS）、TypeScript。Node.js 24（LTS） | [research.md](research.md) R1・R3 |
| 主なフレームワーク | Spring Boot 4.1 系に固定（Spring Web MVC）、React。4.2 は 11/19 リリース予定だが、スプリント中は乗り換えない | [ADR 0001](../../docs/adr/0001-技術スタック.md)、R2 |
| ビルド | Maven（Maven Wrapper で版を固定）。frontend-maven-plugin でフロントをビルドし、jar に同梱 | [ADR 0002](../../docs/adr/0002-ビルドツール.md)・[0003](../../docs/adr/0003-リポジトリと成果物.md)、R4・R5 |
| フロントのビルド／テスト | Vite、Vitest ＋ React Testing Library ＋ jsdom | [ADR 0012](../../docs/adr/0012-フロントのビルドとテスト.md)、R9 |
| 保存 | なし（DB は使わない。結果も保存しない） | 前提、spec の Assumptions、R10 |
| テスト | JUnit 5 ＋ AssertJ（計算の単体テスト）、MockMvc（API）、Vitest（画面） | R9 |
| カバレッジ | 行・分岐とも90%以上。計測ツールと除外対象は Day3 に ADR 0006 で決める（候補：JaCoCo、@vitest/coverage-v8） | constitution 原則II、[ADR 0006](../../docs/adr/0006-カバレッジ基準.md) |
| 動く場所 | 開発：GitHub Codespaces。本番：EC2 上の docker compose（jar 1つ） | [ADR 0004](../../docs/adr/0004-開発環境.md)・[0009](../../docs/adr/0009-デプロイ構成.md) |
| 種類 | Web アプリ（API ＋ 1画面）。1リポジトリ・1 jar | constitution 原則V |
| 性能の目標 | 入力してから結果の表示まで1秒以内 | SC-005 |
| 制約 | 金額に `double`・`float` を使わない。丸めは FR-006・FR-007 の箇所だけ | constitution 原則III、[ADR 0005](../../docs/adr/0005-金額計算と端数処理.md) |
| 規模 | 画面1つ、API 1つ。返済表は最大36行 | spec FR-003 |

NEEDS CLARIFICATION は残っていない。ADR で決まっていなかった項目は、[research.md](research.md) の提案どおりに採用した。

## Constitution Check

*Phase 0 の前に確認し、Phase 1 の設計のあとに再確認した。*

| 原則 | 確認すること | 結果（設計前） | 結果（設計後） |
|---|---|---|---|
| I. TDD必須 | テストを先に書く順で進められるか | OK：計算は Spring なしの単体テストで red → green を速く回せる構成にした（R5 のパッケージ構成） | OK：tasks.md で、各タスクをテスト → 実装の順にする |
| II. カバレッジ90% | 計測できる構成か | OK（条件付き）：計測ツールと除外対象は ADR 0006 で Day3 に決める。この計画では除外を前提にしない | OK：計算・入力チェック・API・画面のすべてに、テストの方法を決めた（R9） |
| III. BigDecimal | 金額に `double` を使わないか。端数処理は仕様にあるか | OK：端数処理は spec の FR-006・FR-007 にある | OK：計算は整数の値の `BigDecimal` と最後の `divide(…, 0, RoundingMode.DOWN)` だけ（R7）。フロントは金額を計算しない（R6） |
| IV. カード情報はダミー | カード番号を扱うか | 対象外：カード番号を入力・表示・保存しない（spec の Assumptions） | 対象外 |
| V. APIでつなぐ・1リポジトリ・1 jar | 境界が API だけか。成果物が1つか | OK：画面と計算は `POST /api/installment-simulations` だけでつながる | OK：フロントの成果物を jar に同梱する（R5）。quickstart で jar が1つであることを確かめる |
| VI. PRを通す | | OK：この機能も PR で main に入れる | OK |
| VII. 秘密情報 | 秘密情報が要るか | OK：要らない（DB も外部サービスも使わない） | OK |
| VIII. シンプルさ | 範囲外のものを作らないか | OK：保存・比較表・繰上げ返済は作らない | OK：DB のライブラリ、MSW、画面の遷移（ルーター）、フロントでの二重の入力チェックは入れない（R8〜R10） |
| IX. 日本語 | | OK：文書はすべて日本語 | OK |

違反はない。「Complexity Tracking」は空。

## プロジェクトの構成

### 文書（この機能）

```text
specs/001-installment-simulation/
├── spec.md              # 機能仕様（既存）
├── plan.md              # この文書
├── research.md          # Phase 0：提案と調査結果
├── data-model.md        # Phase 1：項目と計算の手順
├── quickstart.md        # Phase 1：動作確認の手順
├── contracts/
│   └── installment-simulation-api.md   # Phase 1：API契約
├── checklists/          # 既存
└── tasks.md             # Phase 2（/speckit-tasks で作る。この計画では作らない）
```

### ソースコード（リポジトリのルート）：案A

理由と他の案（マルチモジュールなど）は [research.md](research.md) R5。
Java のパッケージ名のルートは `com.example.cardpayment`。

```text
pom.xml                                  # 唯一の pom。フロントのビルドとテストもここから呼ぶ
mvnw, mvnw.cmd, .mvn/                    # Maven Wrapper
src/
├── main/
│   ├── java/com/example/cardpayment/
│   │   ├── CardPaymentSimulatorApplication.java
│   │   ├── common/
│   │   │   └── web/                     # 入力エラーを Problem Details に変える共通の処理
│   │   └── installment/
│   │       ├── domain/                  # 計算（Spring に依存しない）
│   │       │   ├── InstallmentCalculator.java
│   │       │   ├── InstallmentCondition.java
│   │       │   ├── InstallmentResult.java
│   │       │   └── ScheduleRow.java
│   │       └── web/                     # API
│   │           ├── InstallmentSimulationController.java
│   │           ├── InstallmentSimulationRequest.java    # 文字列で受ける
│   │           ├── InstallmentSimulationResponse.java
│   │           └── InstallmentInputValidator.java       # 文字列 → InstallmentCondition、項目ごとのエラー
│   └── resources/
│       └── application.properties
└── test/java/com/example/cardpayment/
    └── installment/
        ├── domain/                      # 計算の単体テスト（spec の表を期待値にする）
        └── web/                         # 入力チェックの単体テスト、MockMvc の API テスト

frontend/
├── package.json, package-lock.json
├── vite.config.ts                       # /api を localhost:8080 に転送（開発中だけ）
├── index.html
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── shared/
    │   └── formatYen.ts                 # 3桁区切り（FR-014）
    └── features/installment/
        ├── api.ts                       # POST /api/installment-simulations を呼ぶ
        ├── types.ts                     # API の型（contracts と同じ項目名）
        ├── InstallmentPage.tsx          # 入力 → 結果・エラーの表示
        ├── InstallmentForm.tsx
        ├── InstallmentSummary.tsx       # 毎月の支払額・手数料の合計・支払総額（FR-012）
        ├── ScheduleTable.tsx            # 返済表（FR-013）
        └── *.test.tsx / *.test.ts       # テストは対象のファイルの隣に置く
```

**構成の決定**: ルートを Spring Boot のプロジェクトにし、フロントは `frontend/` に置く（案A）。
ビルドの流れは次のとおり（具体的な pom の設定は Day3 のタスク）。

1. `./mvnw package` を実行する。
2. frontend-maven-plugin が、Node.js（R3 の版）を入れて `npm ci` → `npm test` → `npm run build` を実行する。Vite は `frontend/dist/` に出力する。
3. Maven が `frontend/dist/` を `target/classes/static/` にコピーする。
4. Spring Boot の jar ができる。`/` で画面、`/api/...` で API を返す。

## 設計の要点

### API

`POST /api/installment-simulations`。入力は3項目を文字列で受け、結果の金額は円単位の整数で返す。
入力エラーは 400 と Problem Details（`errors` に項目ごとのメッセージ）。
詳細は [contracts/installment-simulation-api.md](contracts/installment-simulation-api.md)、理由は [research.md](research.md) R6。

### 計算

- `InstallmentCalculator` は Spring に依存しない。入力チェック済みの `InstallmentCondition` を受け取り、`InstallmentResult` を返す。
- 計算は spec の FR-006・FR-007 のとおり。誤差を出さないため、割り算は丸めの箇所ごとに1回だけにまとめる（[research.md](research.md) R7、[data-model.md](data-model.md) 4）。
- R7 は仕様の式を変形している。変形は人がレビューして採用した。テストの期待値は、変形後の式ではなく spec の表から作る。

### 入力チェック

- サーバー（`InstallmentInputValidator`）だけで行う。項目ごとに「必須 → 形式 → 範囲」の順で確かめ、誤っている項目はすべて返す（[data-model.md](data-model.md) 1）。
- フロントは、支払回数を選択肢から選ぶ形にするだけで、ほかのチェックはしない（[research.md](research.md) R8）。

### 画面

- 1画面。入力フォーム、結果の要約、返済表を縦に並べる。
- 入力エラーは、各入力欄のすぐ下に、その項目のエラーメッセージ（API の `errors[].message`）を赤字で表示する。ポップアップは使わない。
- 実行するたびに前回の結果とエラーを消し、新しい結果かエラーのどちらかだけを表示する（spec のエッジケース）。
- 初回と2回目以降の支払額が異なるときだけ、分けて表示する（FR-012）。
- 金額は `Intl.NumberFormat('ja-JP')` で3桁区切りにし、「円」を付ける（FR-014）。

## 実装の順番（tasks.md への引き継ぎ）

`docs/sprint-plan.md` の予定（Day3：土台、Day5：分割払い）に合わせる。各ステップは TDD（原則I）で進める。

1. **土台（Day3）**: Spring Boot と `frontend/` の雛形、frontend-maven-plugin による jar への同梱、カバレッジの閾値。Hello を返すだけの最小構成で、1つの jar が端から端まで動くことを確かめる。
2. **計算（P1・P2）**: `InstallmentCalculator`。spec の受け入れシナリオと FR-008〜FR-010 の性質をテストにする。
3. **入力チェック（P3）**: `InstallmentInputValidator`。境界値と形式の誤りをテストにする。
4. **API**: Controller とエラーの共通処理。MockMvc で contracts と照らし合わせる。
5. **画面**: フォーム → 要約 → 返済表 → エラーの表示 の順。
6. **動作確認**: [quickstart.md](quickstart.md) の手順を通す。

## 人が決めたこと

2026-11-17 のレビューで、10項目すべてを提案どおりに採用した。

| # | 決めたこと | 決定 | 参照 |
|---|---|---|---|
| 1 | Java のバージョン | 25（LTS） | R1 |
| 2 | Spring Boot のバージョン | 4.1 系に固定。4.2（11/19 リリース予定）には、スプリント中は乗り換えない | R2 |
| 3 | Node.js のバージョン | 24（LTS） | R3 |
| 4 | フォルダ構成 | 案A（ルートが Spring Boot、フロントは `frontend/`） | R5 |
| 5 | Java のパッケージ名のルート | `com.example.cardpayment` | R5 |
| 6 | API の URL とメソッド | `POST /api/installment-simulations` | R6 |
| 7 | リクエストを文字列で受けること | 文字列で受ける | R6 |
| 8 | エラーの形とメッセージの文言 | Problem Details ＋ `errors`。文言は contracts の一覧 | R6、contracts |
| 9 | 計算の式の変形（R7） | 割り算を最後の1回にまとめる | R7 |
| 10 | 入力チェックをサーバーだけで行うこと | サーバーだけ | R8 |

必要に応じて ADR に記録する（例：バージョン・フォルダ構成は ADR 0001〜0003 に追記するか、新しい ADR を作る）。

## Complexity Tracking

constitution の違反はないので、記入なし。
