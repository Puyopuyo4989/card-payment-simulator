# 実装計画：カードの申込管理

**Branch**: `003-card-application` | **Date**: 2026-11-25 | **Spec**: [spec.md](spec.md)

**Input**: `specs/003-card-application/spec.md` の機能仕様

**この計画の位置づけ**: 技術の前提は `docs/adr/` のADRと、分割払い・リボ払いの計画に従う。これまでに決めたこと
（バージョン・フォルダ構成・API とエラーの形・入力チェックの順番・テストの方法・タブの画面）は引き継ぎ、
**新しく決める必要があることだけ**を [research.md](research.md) に書いた。

- **【決めてほしい】**（R2〜R5）：依頼で「案と理由を書いて、人が選ぶ」とされた4つ。**2026-11-25 に、すべて推奨の案（案A）に決まった。**
- **【提案】**（R6〜R14）：spec が plan に任せたことなど。AIの案をそのまま採用した。
- レビューのときにユーザーの指示で足したもの：タイムゾーンを `Asia/Tokyo` に固定すること（R10）、個人情報をログ・例外に出さない決まり（R11-1）、
  テストが並列で動かないことの確認（R6）。

## 概要

カード会社の担当者が、申込（6項目）を登録し、一覧で状態ごとに確かめ、状態を「申込 → 審査中 → 承認／否決」と進められるようにする。
状態を変えるたびに、担当者名（自己申告）と、否決のときは理由を、変更履歴に残す。

- この機能で初めて DB を使う。Flyway の `V2__card_application.sql` で、申込と変更履歴の2つの表を作る（R2）。`V1` は変えない。
- 状態の遷移のルールは、Spring に依存しないドメイン（`ApplicationStatus`・`CardApplication`）に置く。DB は値の制約だけで守る（R3）。
- 同時の変更は、申込の表の `version` の列で止め、ぶつかったら 409 と Problem Details を返す（R4）。
- 年収は、constitution 原則III に当たるとみなし、`BigDecimal`（万円）と `numeric(4, 0)` で扱う（R5）。
- 画面は、分割払い・リボ払いと同じタブの並びに「申込管理」を足し、`tokens.css` と `index.css` の部品で作る（R12）。
- Should（キーワード検索・ページ分け）は、Must のあとに分けて書いた（R13、data-model.md 7、contracts 6、quickstart.md 7）。

## 技術の前提（Technical Context）

| 項目 | 内容 | 根拠 |
|---|---|---|
| 言語／バージョン | Java 25（LTS）、TypeScript 7.0.2。Node.js 24 | これまでと同じ（[research.md](research.md) R1） |
| 主なフレームワーク | Spring Boot 4.1 系（Spring Web MVC・Spring JDBC の `JdbcClient`）、React 19.3.1 | [ADR 0001](../../docs/adr/0001-技術スタック.md)、R1 |
| 追加する依存ライブラリ | なし（JPA は入れない） | R1、原則VIII |
| 保存 | PostgreSQL 18.6。Flyway の `V2__card_application.sql` で表を作る | [ADR 0011](../../docs/adr/0011-DB.md)、R2 |
| テスト | JUnit 5 ＋ AssertJ（ドメイン・入力チェック）、`@SpringBootTest` ＋ MockMvc ＋ Testcontainers（リポジトリ・API）、Vitest（画面） | R1・R6 |
| テストの DB の片付け | DB を使うテストのクラスは、各テストの前に `TRUNCATE … RESTART IDENTITY` で申込の2つの表を空にする。テストは並列で動いていない（確かめた） | R6 |
| タイムゾーン | `Asia/Tokyo` をコードで明示する（`BusinessTime.JAPAN`、`Clock.system(BusinessTime.JAPAN)`）。サーバー（EC2 のコンテナ）の既定に頼らない | R10 |
| カバレッジ | 行・分岐とも90%以上。新しい除外はない | 原則II、[ADR 0006](../../docs/adr/0006-カバレッジ基準.md) |
| 動く場所 | 開発：GitHub Codespaces。本番：EC2 上の docker compose（jar 1つ＋ PostgreSQL） | [ADR 0004](../../docs/adr/0004-開発環境.md)・[0009](../../docs/adr/0009-デプロイ構成.md) |
| 種類 | Web アプリ（API 4つ＋画面のタブ1つ）。1リポジトリ・1 jar | 原則V |
| 性能の目標 | 申込100件で、一覧（絞り込みを含む）を1秒以内に表示 | SC-006、R14 |
| 制約 | 認証なし（担当者名は自己申告）。カード番号は扱わない。個人情報はダミーだけ。年収に `double`・`float`・`int` を使わない | [ADR 0010](../../docs/adr/0010-認証を作らない.md)、原則III・IV |
| 規模 | 表2つ、API 4つ（Should で一覧にパラメーター2つ）、画面は一覧・登録・詳細の3つの表示 | spec |

NEEDS CLARIFICATION は残っていない。spec の書き方で見つけた食い違い2つ（下の「人が決めたこと」の 5・6）は、
2026-11-25 にユーザーの判断で spec を直した。

## Constitution Check

*Phase 0 の前に確認し、Phase 1 の設計のあとに再確認した。*

| 原則 | 確認すること | 結果（設計前） | 結果（設計後） |
|---|---|---|---|
| I. TDD必須 | テストを先に書く順で進められるか | OK：遷移のルールと入力チェックは Spring なしの単体テストにできる | OK：ドメイン・入力チェックは単体テスト、SQL と楽観ロックは Testcontainers のテストで、どれもテスト → 実装の順にできる（R6） |
| II. カバレッジ90% | 計測できる構成か | OK：ADR 0006 の設定がそのまま使える | OK：遷移16通り、入力の境界、404・409、同時の変更、画面の各表示にテストの方法を決めた。新しい除外はない |
| III. BigDecimal | 金額に `double` を使わないか。端数処理は仕様にあるか | 要確認：年収が「金額」に当たるか（R5） | OK：年収は金額に当たるとし、`BigDecimal`／`numeric(4, 0)` で扱う（R5 案A に決定）。万円単位の整数だけを受け付け、丸めない（data-model.md 2） |
| IV. カード情報・個人情報はダミー | カード番号・個人情報を扱うか | 対象：個人情報の形のデータを扱う。カード番号は扱わない | OK：カード番号の項目は API・DB・画面のどこにもない。テスト・例は架空の値だけ（R11）。登録の画面に注意書き（FR-020）。個人情報をログ・例外のメッセージ・エラーの本文に出さない決まりを作り、`toString` と DB のエラーの詳細はテストで確かめる（R11-1） |
| V. APIでつなぐ・1リポジトリ・1 jar | 境界が API だけか。成果物が1つか | OK：画面は4つの API だけを使う | OK：同じ jar に入る。DB は jar の外（compose）だが、アプリの成果物は1つのまま |
| VI. PRを通す | | OK：PR で main に入れる。PR の前にスキル②（pre-pr-check）を使い、結果を PR の説明文に貼る（CLAUDE.md） | OK |
| VII. 秘密情報 | 秘密情報が要るか | OK：DB の接続情報は今までどおり環境変数（`.env` はコミットしない） | OK |
| VIII. シンプルさ | 範囲外のものを作らないか | OK：認証・自動審査・外部連携・通知・削除・内容の変更・並べ替え・CSV は作らない | OK：依存ライブラリは足さない。索引は主キーと外部キーだけ（R14）。Should は分けて書き、外せるようにした（R13） |
| IX. 日本語 | | OK | OK |

違反はない（R5 は案A に決まった）。「Complexity Tracking」は空。

## プロジェクトの構成

### 文書（この機能）

```text
specs/003-card-application/
├── spec.md              # 機能仕様（既存）
├── plan.md              # この文書
├── research.md          # Phase 0：引き継ぐこと、【決めてほしい】、【提案】
├── data-model.md        # Phase 1：表・入力のルール・状態の遷移
├── quickstart.md        # Phase 1：動作確認の手順
├── contracts/
│   └── card-application-api.md   # Phase 1：API契約
├── checklists/          # 既存
└── tasks.md             # Phase 2（/speckit-tasks で作る。この計画では作らない）
```

### ソースコード（リポジトリのルート）

（新）は新しく作るもの、（変）は中身を変えるもの。【S】は Should のときだけ変える・作るもの。

```text
src/main/resources/db/migration/
├── V1__init.sql                              # 変えない
└── V2__card_application.sql                  #（新）2つの表・連番・制約（data-model.md 1）

src/main/java/com/example/cardpayment/
├── common/
│   ├── config/ClockConfig.java               #（新）Clock.system(BusinessTime.JAPAN) の Bean（R10）
│   ├── time/BusinessTime.java                #（新）JAPAN = ZoneId.of("Asia/Tokyo")（R10）
│   └── web/ApiExceptionHandler.java          #（変）404・409 を足す

src/main/resources/application.properties     #（変）logServerErrorDetail=false（R11-1 の 5）
└── cardapplication/                          #（新）
    ├── domain/                               # Spring に依存しない
    │   ├── ApplicationStatus.java            # 状態と、次に進める状態
    │   ├── CardApplication.java              # changeStatus（遷移のルール）
    │   ├── NewCardApplication.java
    │   ├── StatusHistoryEntry.java
    │   └── StatusTransitionException.java    # 理由：REVIEW_REQUIRED_FIRST・ALREADY_DECIDED・NOT_ALLOWED
    ├── repository/
    │   └── CardApplicationRepository.java    # JdbcClient。updateStatus は WHERE version = ?。【S】検索とページ
    ├── service/
    │   ├── CardApplicationService.java       # @Transactional。data-model.md 4 の 3〜7
    │   ├── ApplicationNotFoundException.java
    │   └── StatusConflictException.java
    └── web/
        ├── CardApplicationController.java    # 4つの API。【S】keyword・page
        ├── CardApplicationInputValidator.java
        ├── StatusChangeInputValidator.java
        └── *Request.java・*Response.java

src/test/java/com/example/cardpayment/
└── cardapplication/
    ├── CardApplicationTables.java            #（新）各テストの前に表を空にする部品（R6）
    ├── domain/                               # 遷移16通りの単体テスト
    ├── web/                                  # 入力チェックの単体テスト（Clock 固定）、API のテスト（Testcontainers）
    └── repository/                           # SQL・並び順・絞り込み・楽観ロック・同時の変更（Testcontainers）

frontend/src/
├── App.tsx                                   #（変）タブ「申込管理」を足す
├── index.css                                 #（変）足りない部品だけを足す（注意書き・状態の札など。色は tokens.css の変数だけ）
├── shared/
│   └── formatDateTime.ts                     #（新）日本時間で表示（FR-011）
└── features/
    └── application/                          #（新）
        ├── api.ts                            # 4つの API を呼ぶ。400・404・409 を区別して返す
        ├── types.ts                          # API の型（contracts と同じ項目名）
        ├── statusLabels.ts                   # 状態の表示名と、出すボタン
        ├── formatManYen.ts                   # 年収を「1,234万円」に（表示だけ）
        ├── ApplicationPage.tsx               # 一覧・登録・詳細の切り替え（useState）
        ├── ApplicationList.tsx               # 状態の絞り込み・表・0件のメッセージ。【S】キーワード・ページ
        ├── ApplicationForm.tsx               # 6項目と注意書き
        ├── ApplicationDetail.tsx             # 全項目・変更履歴・404
        ├── StatusChangeForm.tsx              # 担当者名・否決の理由・許されるボタンだけ・409
        └── *.test.tsx / *.test.ts            # テストは対象のファイルの隣に置く
```

**構成の決定**: これまでの案A のまま。機能のパッケージに、DB を使うための `repository` と、トランザクションをまとめる `service` を足す。
パッケージ名は、Spring の「application」や層の名前と紛れないように `cardapplication` にする。フロントのフォルダは `features/application`。

## 設計の要点

### DB（R2・R7、data-model.md 1）

- `card_application`（申込。今の状態と `version` を持つ）と `card_application_status_history`（履歴。足すだけ）。
- 申込番号は `A` ＋ 8桁の連番で、主キー。DB の連番から `INSERT` のときに作る。
- `CHECK` で、状態の値・年収の範囲・電話番号の形・否決の行だけ理由があること、を守る。遷移は DB に書かない。

### 遷移のルール（R3、data-model.md 3）

- `ApplicationStatus` が次に進める状態を持ち、`CardApplication.changeStatus` が確かめる。許されないときは理由つきの例外。
- サービスは「読む → ドメインで確かめる → 保存」の順に呼ぶだけ。画面は許されるボタンだけを出すが、正しさは API が守る。

### 楽観ロックと 409（R4、data-model.md 4、contracts 4）

- 画面が詳細の `version` を送る。読んだ `version` と違えば 409。`UPDATE … WHERE version = ?` が0行でも 409。
- 409 の本文は Problem Details（`title`・`detail`）。許されない遷移は 409 ではなく 400 ＋ `errors[]`。
- 同時の変更は、2つのスレッドが同じ `version` で変えるテストで、片方だけが成功することを確かめる。

### 入力チェック（R9・R10、data-model.md 2・4、contracts のメッセージの一覧）

- サーバーだけで行う。項目ごとに「必須 → 形式 → 範囲」、誤っている項目はすべて返す（これまでと同じ）。
- 年齢は `Clock` の日本時間の日付で、満年齢を計算する。テストは `Clock` を固定して境界を確かめる。

### タイムゾーン（R10）

- 日本時間 `Asia/Tokyo` をコードで明示する。`Clock` の Bean は `Clock.system(BusinessTime.JAPAN)`、申込日は `LocalDate.ofInstant(clock.instant(), BusinessTime.JAPAN)`。
  EC2 のコンテナの既定（ふつう UTC）にも、`Clock` 自身のタイムゾーンにも頼らない。
- 本番のコードで `LocalDate.now()`・`LocalDateTime.now()`・`ZoneId.systemDefault()`・`TimeZone.getDefault()`・引数なしの `Instant.now()` を使わない。PR の前に `grep` で確かめる。
- テスト：UTC の `Clock` を渡して、日本時間の 23:59:59・0:00・8:59:59・9:00 に申し込んだときの申込日と、18歳・101歳の境界の判定を確かめる
  （日本時間の 0:00〜8:59 は、UTC では前の日。research.md R10 の表）。`Clock` の Bean が `Asia/Tokyo` であることも確かめる。
- 画面は `Intl.DateTimeFormat` に `timeZone: 'Asia/Tokyo'` を渡し、ブラウザのタイムゾーンに頼らない。

### 個人情報をログ・例外に出さない（R11-1）

対象：氏名（漢字・カナ）・生年月日・年収・勤務先名・電話番号・担当者名・否決の理由。申込番号・状態・項目名・件数は出してよい。

1. アプリのログに個人情報を書かない。この機能では、ログを書く処理を作らない。
2. 例外のメッセージに入力の値を入れない（決まった文と、申込番号・状態・項目名だけ）。
3. API のエラーの本文は contracts の決まった文だけで、入力の値を埋め込まない。
4. 個人情報を持つ record は `toString` を上書きし、申込番号（ないものは型の名前）だけを返す（テストで確かめる）。
5. PostgreSQL の JDBC ドライバーの `logServerErrorDetail=false` で、制約の違反のときに行の中身（`Failing row contains (…)`）が例外のメッセージに入らないようにする（テストで確かめる）。
6. リクエストの中身をログに出す設定（`spring.mvc.log-request-details`・web の DEBUG・アクセスログ）は入れない。
7. フロントは `console.log` などで入力や API の結果を出さない。

1・2・3・6・7 は PR の前のレビューで `grep` して確かめる。

### 画面（R12）

- タブ「申込管理」の中で、一覧・登録・詳細を切り替える（ルーターなし）。
- 部品は `index.css` のもの。足りないものだけを足し、色は `tokens.css` の変数だけを使う。
- 年収・日時は表示のための整形だけ（`Intl`）。年齢や遷移の判定はフロントでしない。

### テストの DB（R6）

- DB を使うテストのクラスは、`@BeforeEach` で `CardApplicationTables.clear(…)` を呼ぶ（申込の2つの表を `TRUNCATE … RESTART IDENTITY`）。
- `@Transactional` で戻すやり方は使わない（同時の変更のテストが書けない、連番が戻らない）。
- **並列の確認（2026-11-25）**: surefire 3.5.6 は既定（JVM 1つ・順に実行）で、JUnit の並列の設定も、Testcontainers の使い回しもない。
  1回の verify の中では DB を使うテストは1つずつ動き、`./mvnw` を2つ同時に動かしても、それぞれ別のコンテナを使う。
  TRUNCATE がほかのテストのデータを消すことはない。並列や使い回しを有効にするときは、先にこの方法を見直す（R6）。

## 実装の順番（tasks.md への引き継ぎ）

`docs/sprint-plan.md` の Day7（午前：バックエンド・DB、午後：フロント → PR・デプロイ）に合わせる。各ステップは TDD（原則I）で進める。

**Must**

1. **DB と共通の部品**: `V2__card_application.sql`、`CardApplicationTables`（テストの片付け）、`BusinessTime`・`ClockConfig`、
   `logServerErrorDetail=false`。Flyway が V2 を当てることを確かめる。
2. **ドメイン（US3 のルール）**: `ApplicationStatus`・`CardApplication.changeStatus`。遷移16通り、否決の理由。
3. **登録（US1）**: 入力チェック（境界・形式・年齢。`Clock` 固定）→ リポジトリの `INSERT` → `POST /api/card-applications`。
4. **一覧と詳細（US2）**: リポジトリ（並び順・絞り込み・履歴）→ `GET` の2つ → 404。
5. **状態の変更（US3）**: 入力チェック → サービス（version・遷移・`UPDATE`・履歴）→ `POST …/status-changes` → 409・同時の変更。
6. **画面**: タブ → 一覧 → 登録 → 詳細 → 状態の変更 → 409・404 の表示。
7. **確認と PR**: [quickstart.md](quickstart.md) の 1〜6。タイムゾーンと個人情報の `grep`（上の2つの節）。
   PR の前にスキル②（pre-pr-check）を使い、結果を PR の説明文に貼る。デプロイ → **判断③**。

**Should（Must が終わってから。遅れたら、ここだけ外す）**

8. **キーワード検索とページ分け（US4）**: リポジトリ → API のパラメーター → 画面。quickstart.md の 7。

時間が足りないときは、`docs/sprint-plan.md`「遅れたときに削る順番」に従う（1. Should の 8 を外す → 2. 画面を一覧と状態の変更だけにする）。

## 人が決めたこと

2026-11-25 のレビューで、1〜6 をすべて推奨の案（A）に決めた。R6〜R14 の【提案】も、そのまま採用した。

| # | 決めたこと | 決定 | 日付 | 参照 |
|---|---|---|---|---|
| 1 | 表の作り：申込と履歴を分けるか | 2つの表（申込・変更履歴）。履歴は足すだけ | 2026-11-25 | R2、data-model.md 1 |
| 2 | 状態の遷移のルールをどこに書くか | ドメイン（`ApplicationStatus`・`CardApplication`）。DB は値の制約だけ | 2026-11-25 | R3、data-model.md 3 |
| 3 | 楽観ロックのやり方と、ぶつかったときの応答 | `version` の列。ぶつかったら 409 ＋ Problem Details（`title`・`detail`）。許されない遷移は 400 ＋ `errors[]` | 2026-11-25 | R4、contracts 4 |
| 4 | 年収の型と、原則IIIに当たるか | 当たる。`BigDecimal`（万円）／`numeric(4, 0)` | 2026-11-25 | R5 |
| 5 | spec US4-1 の「テスト タロウ」（半角スペース）と FR-002 の食い違い | FR-002 を正とし、spec の例を全角スペースに直した | 2026-11-25 | R9、spec US4-1 |
| 6 | spec のエッジケース「ちょうど100歳になる日」の読み方 | 満年齢が100以下を正とし、101歳になる日でエラー。spec のエッジケースを直した | 2026-11-25 | R10、spec のエッジケース |

あわせて、ユーザーの指示で次を足した（2026-11-25）。

| # | 足したこと | 内容 | 参照 |
|---|---|---|---|
| 7 | タイムゾーン | `Asia/Tokyo` をコードで明示し、サーバーの既定に頼らない。日本時間の 0:00〜8:59 に申し込んでも申込日がずれないことをテストで確かめる | R10、上の「タイムゾーン」 |
| 8 | 個人情報をログ・例外に出さない | 7つの決まり。`toString` と DB のエラーの詳細はテスト、ほかは PR の前の `grep` で確かめる | R11-1、上の「個人情報をログ・例外に出さない」 |
| 9 | テストが並列で動かないことの確認 | 並列は無効、Testcontainers の使い回しもない。TRUNCATE がほかのテストのデータを消すことはない | R6 |

## Complexity Tracking

constitution の違反はないので、記入なし。
