---
description: "カードの申込管理のタスク一覧"
---

# タスク：カードの申込管理

**Input**: `specs/003-card-application/` の設計文書

**Prerequisites**: [plan.md](plan.md)、[spec.md](spec.md)、[research.md](research.md)、[data-model.md](data-model.md)、[contracts/card-application-api.md](contracts/card-application-api.md)、[quickstart.md](quickstart.md)

**Tests**: constitution 原則I（TDD必須）により、すべての本番コードの前に、失敗するテストを書く。
テストのタスクは、対応する実装のタスクより前に置いている。テストを書いたら、**実装の前に失敗すること（red）を確かめる**。
型（Java の record・enum、TypeScript の型）・SQL（Flyway のマイグレーション）・設定（`application.properties`）も本番のものなので、
それを使うテストを先に書き、コンパイルの失敗やテストの失敗を red とする。
進め方は Superpowers の test-driven-development に従う（[ADR 0007](../../docs/adr/0007-ツールの使い分け.md)）。

**Organization**: 3つのまとまりに分け、それぞれの中を spec のユーザーストーリーごとのフェーズに分ける（sprint-plan の Day7 の午前・午後）。

| まとまり | フェーズ | 内容 |
|---|---|---|
| **A. バックエンド・DB**（Day7 午前） | Phase 1〜4 | DB と共通の部品 → US1〜US3 のドメイン・入力チェック・リポジトリ・API |
| **B. 画面**（Day7 午後） | Phase 5〜8 | タブとフロントの部品 → US1〜US3 の画面 |
| **C. Should**（Must がすべて終わってから） | Phase 9 | US4 キーワード検索とページ分け。**遅れたら、このフェーズごと外す** |
| 仕上げ | Phase 10 | 確認と PR |

A が終われば、API だけで受け入れシナリオを確かめられる（`docs/sprint-plan.md`「遅れたときに削る順番」の2：画面を簡単にしても API は残る）。

## 書き方：`[ID] [P?] [Story] 内容`

- **[P]**：並行して進められる（別のファイルで、終わっていないタスクに依存しない）
- **[Story]**：どのユーザーストーリーのタスクか（US1〜US4）
- 内容には、正確なファイルのパスを書く

## パスの決まり（案A。[plan.md](plan.md)）

- `{main}` ＝ `src/main/java/com/example/cardpayment`、`{test}` ＝ `src/test/java/com/example/cardpayment`
- `{ca}` ＝ `cardapplication`（例：`{main}/{ca}/domain/` ＝ `src/main/java/com/example/cardpayment/cardapplication/domain/`）
- `{fe}` ＝ `frontend/src/features/application`。フロントのテストは対象のファイルの隣に `*.test.ts(x)` で置く

## 全タスクで守ること

- **個人情報はダミーだけ**（原則IV）。テスト・画面の例は research.md R11 の値だけを使う：氏名「テスト 太郎」／「テスト　タロウ」（カナの姓と名の間は**全角**スペース）・「見本 花子」／「ミホン　ハナコ」、
  勤務先「テスト株式会社」「見本商事」、電話番号 `0000000000`・`00000000000`、担当者名「テスト 担当A」「テスト 担当B」、否決の理由「テスト用の否決理由」。
- **カード番号は作らない**（FR-021）。入力欄・API の項目・DB の列のどこにも置かない。
- **個人情報をログ・例外に出さない**（research.md R11-1 の7つの決まり）：ログを書く処理を作らない、例外のメッセージに入力の値を入れない、
  エラーの本文は contracts の決まった文だけ、個人情報を持つ record は `toString` を上書きする、`console.log` を使わない。
- **「今」は `Clock` から取る**。本番のコードで `LocalDate.now()`・`LocalDateTime.now()`・`ZoneId.systemDefault()`・`TimeZone.getDefault()`・引数なしの `Instant.now()` を使わない。
  申込日は `BusinessTime.today(clock)`（＝ `LocalDate.ofInstant(clock.instant(), BusinessTime.JAPAN)`）。保存する時刻はマイクロ秒に切り捨てる（research.md R10）。
- **年収は `BigDecimal`**（万円、小数なし）。`double`・`float`・`int` にしない（原則III、R5）。比べるときは `compareTo`。
- **DB を使うテストのクラス**は、`@BeforeEach` で `CardApplicationTables.clear(jdbcClient)` を呼ぶ。テストを `@Transactional` にしない（R6）。
  テストは並列にしない（R6 で確かめた設定のまま。並列を有効にしない）。
- **`V1__init.sql` は変えない**。DB の変更は `V2__card_application.sql` だけ（ADR 0011）。V2 を一度コミット・デプロイしたら書き換えず、直すときは V3 を足す。
- **エラーのメッセージは contracts の「メッセージの一覧」の文を、一字一句そのまま使う**。テストの期待値も contracts から写す。
- MVP の範囲外（認証・自動審査・外部連携・通知・申込の削除・内容の変更・並べ替え・CSV・カード番号の発行・ルーター・JPA・新しい依存ライブラリ）は作らない（原則VIII）。
- **Should（Phase 9）は、Phase 1〜8 がすべて終わるまで始めない**。Must のコードに、Should のための引数や分岐を先に入れない（一覧の `{ "items": [...] }` の形だけは Must で作る。R8）。

---

# A. バックエンド・DB（Day7 午前）

## Phase 1: Foundational（DB と共通の部品）

**Purpose**: どのストーリーにも要る、表・テストの片付け・時刻・DB のエラーの扱い・状態の値。**ここが終わるまで、ストーリーは始めない。**

- [x] T001 テストの片付けの部品を作る：`{test}/{ca}/CardApplicationTables.java`（`public static void clear(JdbcClient jdbcClient)` が `TRUNCATE card_application_status_history, card_application RESTART IDENTITY` を実行する。`flyway_schema_history` は消さない。research.md R6）。テストのコードなので、T002 と一緒に書いてよい
- [x] T002 マイグレーションのテストを書く：`{test}/{ca}/repository/SchemaMigrationTest.java`（`@SpringBootTest` ＋ `@Import(TestcontainersConfiguration.class)`、`@BeforeEach` で `CardApplicationTables.clear`）。`JdbcClient` で SQL を直接実行して確かめる：(1) `flyway_schema_history` に version `2`・`success = true` の行がある、(2) 申込の表に必要な列だけを指定して `INSERT … RETURNING application_number` すると `A00000001`、続けて `A00000002`（`RESTART IDENTITY` で連番が1に戻ることも、2つ目のテストで `A00000001` から始まることで確かめる）、(3) 次の `INSERT` が制約で失敗する（`DataIntegrityViolationException`）：`annual_income` が `10000`・`-1`、`status` が `'UNKNOWN'`、`phone_number` が `'03-0000-0000'`・`'1000000000'`、`name_kanji` が51文字、履歴の `to_status = 'REJECTED'` で `rejection_reason` が NULL、`to_status = 'UNDER_REVIEW'` で `rejection_reason` がある、履歴の `application_number` が申込の表にない、(4) `name_kanji` が50文字（「𠮷」のような4バイトの文字を含む）は入る。red（V2 がないので失敗する）を確かめる
- [x] T003 `src/main/resources/db/migration/V2__card_application.sql` を作る。data-model.md 1 のとおり：連番 `card_application_number_seq`、表 `card_application`（`application_number varchar(9)` 主キー・既定値 `'A' || lpad(nextval('card_application_number_seq')::text, 8, '0')`、`name_kanji varchar(50) NOT NULL`、`name_kana varchar(50) NOT NULL`、`birth_date date NOT NULL`、`annual_income numeric(4, 0) NOT NULL CHECK (annual_income BETWEEN 0 AND 9999)`、`employer_name varchar(100) NOT NULL`、`phone_number varchar(11) NOT NULL CHECK (phone_number ~ '^0[0-9]{9,10}$')`、`status varchar(20) NOT NULL CHECK (status IN ('APPLIED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED'))`、`applied_at timestamptz NOT NULL`、`updated_at timestamptz NOT NULL`、`version integer NOT NULL DEFAULT 0`）、`ALTER SEQUENCE card_application_number_seq OWNED BY card_application.application_number`、表 `card_application_status_history`（`id bigint GENERATED ALWAYS AS IDENTITY` 主キー、`application_number varchar(9) NOT NULL REFERENCES card_application`、`changed_at timestamptz NOT NULL`、`from_status`・`to_status varchar(20) NOT NULL` と状態の4つの値の `CHECK`、`staff_name varchar(50) NOT NULL`、`rejection_reason varchar(500)`、`CHECK ((to_status = 'REJECTED') = (rejection_reason IS NOT NULL))`）、索引 `card_application_status_history(application_number)`。各文に日本語のコメントを付ける。`V1__init.sql` は変えない。T002 が green になる
- [x] T004 [P] 時刻のテストを書く：`{test}/common/time/BusinessTimeTest.java` と `{test}/common/config/ClockConfigTest.java`（どちらも Spring を起動しない）。(1) `BusinessTime.JAPAN` が `ZoneId.of("Asia/Tokyo")`、(2) `new ClockConfig().clock().getZone()` が `Asia/Tokyo`、(3) `BusinessTime.today(Clock.fixed(instant, ZoneOffset.UTC))` が research.md R10 の表のとおり：`2026-11-24T14:59:59Z` → `2026-11-24`、`2026-11-24T15:00:00Z` → `2026-11-25`、`2026-11-24T23:59:59Z` → `2026-11-25`、`2026-11-25T00:00:00Z` → `2026-11-25`、(4) `BusinessTime.now(clock)` がマイクロ秒に切り捨てた `Instant` を返す（`…T01:30:00.123456789Z` → `…T01:30:00.123456Z`）、(5) JVM の既定のタイムゾーンを一時的に `UTC` にして（テストの最後に元に戻す）も (3) が同じ結果になる。red を確かめる
- [x] T005 `{main}/common/time/BusinessTime.java`（`public static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo")`、`static LocalDate today(Clock clock)` ＝ `LocalDate.ofInstant(clock.instant(), JAPAN)`、`static Instant now(Clock clock)` ＝ `clock.instant().truncatedTo(ChronoUnit.MICROS)`。インスタンスを作らせない）と `{main}/common/config/ClockConfig.java`（`@Configuration`、`@Bean Clock clock()` ＝ `Clock.system(BusinessTime.JAPAN)`）を作る。T004 が green になる
- [x] T006 [P] テスト用の時計を作る：`{test}/common/config/MutableTestClock.java`（`Clock` を継承し、`set(Instant)`・`advance(Duration)` で時刻を変えられる。タイムゾーンは `ZoneOffset.UTC` にし、本番のコードが `Clock` のタイムゾーンに頼っていないことも一緒に確かめる）と `{test}/common/config/TestClockConfiguration.java`（`@TestConfiguration`、`@Bean @Primary MutableTestClock`。最初の時刻は `2026-11-25T01:30:00Z`）。API のテストは `@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})` で使う
- [x] T007 [P] DB のエラーに行の中身が入らないことのテストを書く：`{test}/{ca}/repository/DatabaseErrorDetailTest.java`（`@SpringBootTest` ＋ Testcontainers、`@BeforeEach` で片付け）。`JdbcClient` で、`name_kanji` が「テスト 漏えい確認」で `annual_income` が `10000`（制約の違反）の行を `INSERT` し、出た例外とその原因（`getCause()` をたどったすべて）の `getMessage()` に「テスト 漏えい確認」が含まれないことを確かめる（research.md R11-1 の5）。今は PostgreSQL の `Failing row contains (…)` が入るので、red になることを確かめる
  - 2026-11-25：年収 `10000` では red にならなかった。`numeric(4, 0)` の桁あふれ（`numeric field overflow`）で CHECK より前に止まり、そのエラーには行の中身が入らないため。CHECK の違反になる年収 `-1` に変え、メッセージに `Failing row contains (A00000001, テスト 漏えい確認, …)` が入って red になることを確かめた
- [x] T008 `src/main/resources/application.properties` に `spring.datasource.hikari.data-source-properties.logServerErrorDetail=false` を足す（理由のコメント：制約の違反のとき、行の中身＝個人情報が例外のメッセージに入らないようにする。research.md R11-1）。T007 が green になる。green にならないとき（テストの DB に設定が効かないなど）は、別の書き方で回避せず、作業を止めて人に報告する
- [x] T009 [P] 状態の値のテストを書く：`{test}/{ca}/domain/ApplicationStatusTest.java`。`ApplicationStatus` に `APPLIED`・`UNDER_REVIEW`・`APPROVED`・`REJECTED` の4つがあり、`ApplicationStatus.parse(String)` が4つの値の文字列を enum に変え、それ以外（`""`・`"applied"`・`"UNKNOWN"`・`null`）は `Optional.empty()` を返す。次に進める状態は US3（T028・T035）で足す
- [x] T010 `{main}/{ca}/domain/ApplicationStatus.java` を作る（4つの値と `parse`）。T009 が green になる

**Checkpoint**: `./mvnw verify` が通る（既存の分割払い・リボ払いのテストも通る）。Flyway が V2 を当て、テストの DB の片付けと時計が使える。

---

## Phase 2: User Story 1 - 申込を登録する（バックエンド）(Priority: P1) 🎯 MVP

**Goal**: 6項目を API に送ると、申込番号が割り当てられ、状態「申込」で保存される。誤りがあれば、すべての項目のエラーが返り、何も保存されない。

**Independent Test**: `POST /api/card-applications` に contracts 1 の例を送る → 201・`Location: /api/card-applications/A00000001`・`status` `APPLIED`。DB を読み直しても同じ内容（US1-1・US1-2）。誤りのある入力 → 400・誤った項目すべての `errors`・件数が増えない（US1-3）。

### Tests for User Story 1（先に書き、red を確かめる）

- [x] T011 [P] [US1] 登録の入力チェックのテストを書く：`{test}/{ca}/web/CardApplicationInputValidatorTest.java`（Spring を起動しない。`Clock.fixed(Instant.parse("2026-11-25T03:00:00Z"), ZoneOffset.UTC)`、申込日は 2026-11-25）。メッセージは contracts 1「メッセージの一覧（登録）」の文をそのまま期待値にする。(1) 正しい6項目 → `NewCardApplication`（前後の空白・全角スペースを取り除いた値、`annualIncome` は `compareTo` で `450` と等しい `BigDecimal`、`birthDate` は `LocalDate`）。(2) `nameKanji`：未入力・`"　"`（全角スペースだけ）→ 未入力、50文字は通る・51文字は「50文字以内」、「𠮷」を含む50文字は通る（コードポイントで数える）。(3) `nameKana`：「テスト　タロウ」「ヴァーチャル」は通る、「テスト タロウ」（半角スペース）・「てすと」・「ﾃｽﾄ」・「テスト・タロウ」は形式の誤り、51文字は長さの誤り。(4) `birthDate`：未入力、`"2026-02-30"`・`"1990/04/01"`・`"19900401"` は形式の誤り、`"2026-11-26"` は「未来の日付」、`"2008-11-25"` は通る・`"2008-11-26"` は年齢の誤り、`"1925-11-26"` は通る・`"1925-11-25"` は年齢の誤り、`"1926-11-25"` は通る（data-model.md 2）。(5) `annualIncome`：`"0"`・`"9999"`・`"0450"` は通る、`"10000"` は範囲の誤り、`"1.5"`・`"-1"`・`"１００"`・`"1,000"`・`"abc"` は形式の誤り、半角数字21文字は範囲の誤り。(6) `employerName`：100文字は通る・101文字は長さの誤り。(7) `phoneNumber`：`"0000000000"`・`"00000000000"` は通る、9桁・12桁・`"1000000000"`・`"03-0000-0000"`・`"０００００００００００"` は形式の誤り。(8) 6項目すべてが誤り → `errors` が6件で、`nameKanji`→`nameKana`→`birthDate`→`annualIncome`→`employerName`→`phoneNumber` の順。(9) 例外のメッセージ（`getMessage()`）と `errors[].message` に、入力した値が含まれない
- [x] T012 [P] [US1] 申込日がずれないことのテストを書く：`{test}/{ca}/web/CardApplicationInputValidatorTimeZoneTest.java`。research.md R10 の表の4行を `@ParameterizedTest` にする：`Clock.fixed(…, ZoneOffset.UTC)` を `2026-11-24T14:59:59Z`・`2026-11-24T15:00:00Z`・`2026-11-24T23:59:59Z`・`2026-11-25T00:00:00Z` にして、生年月日 `2008-11-25` が「エラー・通る・通る・通る」、`1925-11-25` が「通る・エラー・エラー・エラー」になる（日本時間の 0:00〜8:59 は UTC では前の日なので、UTC の日付で判定していれば2・3行目が逆になる）
- [x] T013 [P] [US1] 個人情報を出さない `toString` のテストを書く：`{test}/{ca}/PersonalDataToStringTest.java`。`NewCardApplication`・`CardApplication`・`CardApplicationRequest` の `toString()` に、入れた氏名（漢字・カナ）・生年月日・年収・勤務先名・電話番号の文字列がどれも含まれず、`CardApplication` は申込番号を含む。`StatusHistoryEntry` の `toString()` に担当者名・否決の理由が含まれない（research.md R11-1 の4）。`StatusChangeRequest` は US3 の T030 で確かめる
- [x] T014 [US1] 登録のリポジトリのテストを書く：`{test}/{ca}/repository/CardApplicationRepositoryTest.java`（`@SpringBootTest` ＋ Testcontainers、`@BeforeEach` で片付け）。`insert(NewCardApplication, Instant now)` が `A00000001`、次が `A00000002` を返す。`findByNumber("A00000001")` が、入れた6項目・`status` `APPLIED`・`appliedAt` と `updatedAt` が `now`（マイクロ秒に切り捨て済みの値）・`version` 0 の `CardApplication` を返す（年収は `compareTo`、日時は `Instant` で比べる）。ない番号・`"abc"` は `Optional.empty()`。同じ内容を2回入れると、別の番号の2件になる（spec のエッジケース）
- [x] T015 [US1] 登録の API のテストを書く：`{test}/{ca}/web/CardApplicationApiTest.java`（`@SpringBootTest` ＋ `@AutoConfigureMockMvc` ＋ `@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})`、`MockMvcTester`、`@BeforeEach` で片付けと時計を `2026-11-25T01:30:00Z` に戻す）。contracts 1 と照らし合わせる：(1) 例のリクエスト → 201・`Location: /api/card-applications/A00000001`・`Content-Type: application/json`・`applicationNumber` `A00000001`・6項目・`status` `APPLIED`・`version` 0・`history` が空・`appliedAt` と `updatedAt` が `"2026-11-25T01:30:00Z"`。(2) **本文の文字列**に `"annualIncome":450` が含まれ、`450.0`・`4.5E+2` が含まれない。(3) 項目に JSON の数値（`"annualIncome": 450`）が来ても 201。(4) 未入力・範囲外を混ぜた入力 → 400・`application/problem+json`・`title` `入力に誤りがあります`・`errors` の項目と順番が contracts のとおり・`errors[].message` に入力の値が含まれない、そのあと `SELECT count(*) FROM card_application` が 0（US1-3、SC-003）。(5) 本文が JSON として読めない → 400・`title` `リクエストの形式が正しくありません`・`errors` なし。(6) 登録したあと `CardApplicationRepository` で読み直すと同じ内容（US1-2。再起動しても残ることは quickstart で人が確かめる）

### Implementation for User Story 1

- [x] T016 [US1] T011・T013・T014 がコンパイルで失敗すること（red）を確かめてから、ドメインの型を作る：`{main}/{ca}/domain/NewCardApplication.java`（record。`String nameKanji`・`String nameKana`・`LocalDate birthDate`・`BigDecimal annualIncome`（万円）・`String employerName`・`String phoneNumber`。値のチェックはしない。`toString` は `"NewCardApplication[個人情報は表示しない]"` のような決まった文を返す）、`{main}/{ca}/domain/CardApplication.java`（record。`String applicationNumber`・上の6項目・`ApplicationStatus status`・`Instant appliedAt`・`Instant updatedAt`・`int version`。`toString` は申込番号と状態だけ）。テストはまだ失敗のまま
- [x] T017 [US1] `{main}/{ca}/web/CardApplicationRequest.java`（record。6項目とも `String`。`toString` は決まった文）と `{main}/{ca}/web/CardApplicationInputValidator.java`（`@Component`、`Clock` を受け取る）を作る。data-model.md 2 の表のとおり、`InputRules.strip` で前後の空白を取り除き、項目ごとに「必須 → 形式 → 範囲」で最初の1つを `FieldError` にし、誤りがあれば `InputValidationException` を投げる。文字数は `codePointCount`、カナは `[ァ-ヶー　]+`（U+30A1〜U+30F6・U+30FC・U+3000）、生年月日は `DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)`・申込日は `BusinessTime.today(clock)`・年齢は `Period.between(birthDate, today).getYears()` が 18〜100、年収は `InputRules.isInteger`・`InputRules.isTooLong`・`new BigDecimal(String)`・`compareTo(new BigDecimal("9999")) > 0`、電話番号は `0[0-9]{9,10}`。メッセージは contracts の文のまま。T011・T012 と、T013 の `NewCardApplication`・`CardApplication`・`CardApplicationRequest` の部分が green になる（`StatusHistoryEntry` の部分は T020 で green になる）
- [x] T018 [US1] `{main}/{ca}/repository/CardApplicationRepository.java` を作る（`@Repository`、`JdbcClient`）：`String insert(NewCardApplication, Instant now)`（`status = 'APPLIED'`・`applied_at = updated_at = now`・`version = 0` で `INSERT … RETURNING application_number`）、`Optional<CardApplication> findByNumber(String)`。`Instant` は `OffsetDateTime.ofInstant(now, ZoneOffset.UTC)` にして渡し、読むときは `OffsetDateTime` → `Instant`（research.md R10）。T014 が green になる
- [x] T019 [US1] `{main}/{ca}/service/CardApplicationService.java` を作る（`@Service`、`Clock` を受け取る）：`CardApplication register(NewCardApplication)`（`@Transactional`。`BusinessTime.now(clock)` で時刻を取り、`insert` して `findByNumber` で読み直す）
- [x] T020 [US1] `{main}/{ca}/web/CardApplicationDetailResponse.java`（record。contracts 3 の項目。`annualIncome` は `BigDecimal`（scale 0 のまま返す）、日時は `Instant`、`history` は `List<StatusHistoryResponse>`。`static from(CardApplication, List<StatusHistoryEntry>)`。US1 では空のリストを渡す）
  - 2026-11-25：US1 では履歴がいつも空で、`StatusHistoryEntry` → `StatusHistoryResponse` の変換を確かめるテストがないので、`from(CardApplication)`（`history` は空のリスト）だけを作った。履歴を付ける `from` は、T023 のテストを先に書いてから T027 で足す。レスポンスの型の `toString` の上書きも、タスクにないので作らなかった（`StatusHistoryEntry` の `toString` は T013 で確かめ、T016 と一緒に作った）と `{main}/{ca}/web/StatusHistoryResponse.java`（record。`changedAt`・`fromStatus`・`toStatus`・`staffName`・`rejectionReason`）、`{main}/{ca}/domain/StatusHistoryEntry.java`（record。`Instant changedAt`・`ApplicationStatus fromStatus`・`ApplicationStatus toStatus`・`String staffName`・`String rejectionReason`（否決のときだけ。ほかは null）。`toString` は状態の変化だけ）を作る
- [x] T021 [US1] `{main}/{ca}/web/CardApplicationController.java` を作る：`POST /api/card-applications`。`validator.validate(request)` → `service.register` → 201・`Location`・`CardApplicationDetailResponse`。ログを書く処理は入れない。T015 が green になる。`Instant` が ISO 8601 の文字列（`"2026-11-25T01:30:00Z"`）で返らないとき（Jackson の設定の違い）は、直し方を人に相談してから直す

**Checkpoint**: curl で quickstart.md 4 の「登録する」「複数の誤り」を確かめられる。

---

## Phase 3: User Story 2 - 申込の一覧と詳細を見る（バックエンド）(Priority: P2)

**Goal**: 一覧（申込日時の新しい順、状態で絞り込み）と、詳細（全項目・変更履歴）が API で取れる。

**Independent Test**: 3件登録して `GET /api/card-applications` → 新しい順の3件（4項目だけ）。`?status=APPLIED` で絞り込める。`GET /api/card-applications/A00000001` → 詳細。`A99999999` → 404（US2-1〜US2-7）。

### Tests for User Story 2（先に書き、red を確かめる）

- [ ] T022 [US2] 一覧と履歴のリポジトリのテストを `{test}/{ca}/repository/CardApplicationRepositoryTest.java` に足す：(1) 申込日時の違う3件を入れると `findAll(Optional.empty())` が新しい順、(2) 同じ申込日時の2件は申込番号の大きい順（R10）、(3) 状態を SQL で直接 `UPDATE` して「申込」2件・「審査中」1件・「承認」1件にし、`findAll(Optional.of(APPLIED))` が「申込」の2件だけを新しい順で返す（US2-2）、(4) 0件のとき空のリスト、(5) 履歴を SQL で直接2行入れ（`changed_at` の古い行をあとに入れる）、`findHistory("A00000001")` が `changed_at` の古い順、同じ `changed_at` なら `id` の小さい順に返し、否決でない行の `rejectionReason` は null
- [ ] T023 [US2] 一覧と詳細の API のテストを `{test}/{ca}/web/CardApplicationApiTest.java` に足す（contracts 2・3）：(1) 時計を1分ずつ進めて3件登録 → `GET /api/card-applications` が 200・`items` が新しい順の3件で、各行が `applicationNumber`・`nameKanji`・`status`・`appliedAt` だけ（**本文の文字列**に `birthDate`・`phoneNumber`・`annualIncome` が含まれない）（US2-1）、(2) `?status=UNDER_REVIEW` で絞り込める、`?status=`（空）は「すべて」（US2-2・US2-3）、(3) 0件・該当なしのとき `{"items":[]}`（US2-4・US2-6）、(4) `?status=applied` → 400・`errors[0].field` `status`・contracts のメッセージ、(5) `GET /api/card-applications/A00000001` → 200・contracts 3 の全項目・`history`（SQL で入れた2行が古い順。`rejectionReason` は null または理由）（US2-5）、(6) `A99999999`・`abc` → 404・`application/problem+json`・`title` `申込が見つかりません`・`detail` `申込番号を確かめてください`（US2-7）

- [ ] T075 [US2] 申込の内容を変えられない・削除できないことのテストを `{test}/{ca}/web/CardApplicationApiTest.java` に足す（spec FR-006、contracts 5。2026-11-25、analyze の G1 から足した。番号は最後の続き）：1件登録してから、`PUT`・`PATCH`・`DELETE` を `/api/card-applications/A00000001` に送ると、どれも 405。そのあと `GET /api/card-applications/A00000001` の本文が送る前と同じで、`SELECT count(*) FROM card_application` が 1 のまま。作らないことを確かめるテストなので、書いた時点で通る。002 の T061 と同じく、一時的に `PUT` を受ける処理を足してこのテストが失敗することを確かめてから、その処理を消す

### Implementation for User Story 2

- [ ] T024 [US2] `CardApplicationRepository` に `List<CardApplication> findAll(Optional<ApplicationStatus> status)`（`ORDER BY applied_at DESC, application_number DESC`）と `List<StatusHistoryEntry> findHistory(String applicationNumber)`（`ORDER BY changed_at, id`）を足す。T022 が green になる
- [ ] T025 [US2] `{main}/{ca}/service/ApplicationNotFoundException.java`（メッセージは決まった文と申込番号だけ）を作り、`CardApplicationService` に `list(Optional<ApplicationStatus>)` と `detail(String applicationNumber)`（申込と履歴を返す。ないときは `ApplicationNotFoundException`）を足す（`@Transactional(readOnly = true)`）
- [ ] T026 [US2] `{main}/common/web/ApiExceptionHandler.java` に `ApplicationNotFoundException` → 404 の Problem Details（`type` `about:blank`・`title` `申込が見つかりません`・`detail` `申込番号を確かめてください`）を足す。既存の 400 の扱いは変えない
- [ ] T027 [US2] `{main}/{ca}/web/CardApplicationSummaryResponse.java`（record。4項目）と `{main}/{ca}/web/CardApplicationListResponse.java`（record。`List<CardApplicationSummaryResponse> items`。Should のための項目はまだ足さない）を作り、`CardApplicationController` に `GET /api/card-applications`（`status` を `ApplicationStatus.parse`。空・省略は「すべて」、それ以外の誤りは `InputValidationException`（`status`、contracts 2 のメッセージ））と `GET /api/card-applications/{applicationNumber}` を足す。T023 が green になる

**Checkpoint**: curl で quickstart.md 4 の「一覧と詳細」を確かめられる。

---

## Phase 4: User Story 3 - 審査の状態を進める（バックエンド）(Priority: P3)

**Goal**: 状態を「申込 → 審査中 → 承認／否決」の順に変えられ、変更のたびに履歴が1行増える。許されない変更・担当者名のない変更・理由のない否決・ほかの担当者とぶつかった変更は行われない。

**Independent Test**: 1件を「申込 → 審査中 → 否決」と変え、詳細の `history` に2行。許されない変更・担当者名なし・理由なしは 400、古い `version` は 409 で、どれも状態と履歴が変わらない（US3-1〜US3-8、FR-019、SC-004）。

### Tests for User Story 3（先に書き、red を確かめる）

- [ ] T028 [P] [US3] 次に進める状態のテストを `{test}/{ca}/domain/ApplicationStatusTest.java` に足す：`APPLIED.canChangeTo(UNDER_REVIEW)`、`UNDER_REVIEW.canChangeTo(APPROVED)`・`(REJECTED)` だけが true で、ほかの13通りは false。`APPROVED.isFinal()`・`REJECTED.isFinal()` が true、ほかは false（data-model.md 3）
- [ ] T029 [P] [US3] 遷移のルールのテストを書く：`{test}/{ca}/domain/CardApplicationTest.java`（Spring も DB も使わない）。状態4つ × 頼む状態4つの16通りを `@ParameterizedTest` にし、data-model.md 3 の表のとおり：(1) 許される3通りは、`changeStatus(to, "テスト 担当A", reason, now)` が、状態が `to`・`updatedAt` が `now` の `CardApplication`（`version` は変えない。増やすのは DB）と、`fromStatus`・`toStatus`・`staffName`・`changedAt = now` の `StatusHistoryEntry` を返す、(2) `APPLIED` → `APPROVED`・`REJECTED` は `StatusTransitionException`（理由 `REVIEW_REQUIRED_FIRST`）、(3) `APPROVED`・`REJECTED` から → どれでも `ALREADY_DECIDED`、(4) `APPLIED → APPLIED`・`UNDER_REVIEW → APPLIED`・`UNDER_REVIEW → UNDER_REVIEW` は `NOT_ALLOWED`。あわせて (5) `REJECTED` への変更で理由が null なら `IllegalArgumentException`（入力チェックで先に止めるので、ここに来るのはコードの誤り）、(6) `REJECTED` 以外への変更で理由を渡しても、履歴の `rejectionReason` は null、(7) 元の `CardApplication` は変わらない、(8) 例外のメッセージに担当者名・理由が含まれない
- [ ] T030 [P] [US3] 状態の変更の入力チェックのテストを書く：`{test}/{ca}/web/StatusChangeInputValidatorTest.java`。メッセージは contracts 4「メッセージの一覧（状態の変更）」の文をそのまま期待値にする：(1) 正しい入力 → `toStatus`・前後の空白を取り除いた `staffName`・`version`（`int`）、(2) `toStatus`：未入力、`"approved"`・`"X"` は値の誤り、(3) `staffName`：未入力・`"　 "`（空白だけ）は未入力、50文字は通る・51文字は誤り、(4) `rejectionReason`：`toStatus` が `REJECTED` のとき、未入力・空白だけは「否決の理由を入力してください」、500文字は通る・501文字は誤り。`REJECTED` 以外のときは、501文字でも誤りにせず、結果の理由は null（保存しない）、(5) `version`：未入力・`"-1"`・`"1.0"`・`"abc"`・`"99999999999"`（`int` に収まらない）は誤り、`"0"`・`"01"` は通る、(6) 4項目すべてが誤り → `toStatus`→`staffName`→`rejectionReason`→`version` の順（`toStatus` が値の誤りのときは、理由の要否が決まらないので `rejectionReason` は確かめない）、(7) 例外のメッセージと `errors[].message` に入力の値が含まれない、(8) `StatusChangeRequest` の `toString()` に担当者名・否決の理由が含まれない（R11-1 の4）
- [ ] T031 [P] [US3] サービスの「保存の時点でぶつかった」場合のテストを書く：`{test}/{ca}/service/CardApplicationServiceTest.java`（Spring も DB も使わない。`CardApplicationRepository` を手書きの差し替え（`findByNumber` は `version` 1 の審査中の申込を返し、`updateStatus` は 0 を返す）にする）。`changeStatus(番号, APPROVED, "テスト 担当A", null, 1)` が `StatusConflictException` を投げ、`insertHistory` が呼ばれない。あわせて、読んだ `version`（1）と送られた `version`（0）が違うときも `StatusConflictException` で、`updateStatus` が呼ばれない。ない申込は `ApplicationNotFoundException`
- [ ] T032 [US3] 状態の更新のリポジトリのテストを `{test}/{ca}/repository/CardApplicationRepositoryTest.java` に足す：(1) `updateStatus(番号, UNDER_REVIEW, now, 0)` が 1 を返し、`status`・`updated_at` が変わり `version` が 1 になる、(2) 古い `version`（0）でもう一度呼ぶと 0 を返し、何も変わらない、(3) ない番号は 0、(4) `insertHistory(番号, StatusHistoryEntry)` で入れた行を `findHistory` で読める（否決の行の理由を含む）
- [ ] T033 [US3] 状態の変更の API のテストを `{test}/{ca}/web/CardApplicationApiTest.java` に足す（contracts 4）。どれも、エラーのときは状態・`version`・履歴の件数が変わらないことも確かめる：(1) 登録 → 時計を進めて `{"toStatus":"UNDER_REVIEW","staffName":"テスト 担当A","version":"0"}` → 200・`status` `UNDER_REVIEW`・`updatedAt` が進めた時刻・`version` 1・`history` に「申込→審査中」・変更日時・「テスト 担当A」の1行（US3-1）、(2) 続けて `APPROVED` → `history` 2行が古い順（US3-2・US3-8）、(3) 審査中から理由つきで `REJECTED` → 2行目に理由（US3-3）、(4) 審査中で理由なし・空白だけの `REJECTED` → 400・`rejectionReason`（US3-4）、(5) 担当者名なし・空白だけ → 400・`staffName`（US3-5）、(6) 申込から `APPROVED`・`REJECTED` → 400・`toStatus`・「承認・否決の前に、先に「審査中」にしてください」（US3-6）、(7) 承認・否決から `UNDER_REVIEW` → 400・「審査が終わった申込の状態は変えられません」（US3-7）、(8) 古い `version` → 409・`application/problem+json`・`title` `ほかの担当者が先に状態を変えました`・`detail` `最新の状態を確かめてから、もう一度操作してください`・`errors` なし（FR-019）、(9) ない申込 → 404、(10) ない申込に担当者名なし → 400（入力のチェックが先。data-model.md 4 の順番）、(11) 古い `version` で許されない遷移 → 409（version が先）
- [ ] T034 [US3] 同時の変更のテストを書く：`{test}/{ca}/web/ConcurrentStatusChangeTest.java`（`@SpringBootTest` ＋ MockMvc ＋ Testcontainers、`@BeforeEach` で片付け。テストを `@Transactional` にしない）。審査中（`version` 1）の申込に、2つのスレッドが `CountDownLatch` で同時に、同じ `version` 1 で「承認」（テスト 担当A）と「否決」（テスト 担当B・理由つき）を送る。片方が 200、もう片方が 409 で、最後の状態は 200 だった方、`version` は 2、履歴は2行（spec のエッジケース「2人の担当者が同時に変更」）。結果がばらつかないことを、`@RepeatedTest(5)` で確かめる

### Implementation for User Story 3

- [ ] T035 [US3] `ApplicationStatus` に `boolean canChangeTo(ApplicationStatus)` と `boolean isFinal()` を足す（次に進める状態は enum の中に持つ）。T028 が green になる
- [ ] T036 [US3] `{main}/{ca}/domain/StatusTransitionException.java`（理由の enum `Reason { REVIEW_REQUIRED_FIRST, ALREADY_DECIDED, NOT_ALLOWED }` を持つ。メッセージは理由と状態だけ）を作り、`CardApplication` に `StatusChange changeStatus(ApplicationStatus to, String staffName, String rejectionReason, Instant now)`（変更後の `CardApplication` と `StatusHistoryEntry` の組。record `StatusChange` は同じパッケージ）を足す。判定の順番：今の状態が最終 → `ALREADY_DECIDED`、`APPLIED` から `APPROVED`・`REJECTED` → `REVIEW_REQUIRED_FIRST`、`canChangeTo` が false → `NOT_ALLOWED`。T029 が green になる
- [ ] T037 [US3] `{main}/{ca}/web/StatusChangeRequest.java`（record。`String toStatus`・`String staffName`・`String rejectionReason`・`String version`。`toString` は決まった文）と `{main}/{ca}/web/StatusChangeInputValidator.java` を作る（data-model.md 4 の表。結果は record `ValidStatusChange(ApplicationStatus toStatus, String staffName, String rejectionReason, int version)`）。T030 が green になる
- [ ] T038 [US3] `CardApplicationRepository` に `int updateStatus(String applicationNumber, ApplicationStatus to, Instant now, int expectedVersion)`（`UPDATE card_application SET status = ?, updated_at = ?, version = version + 1 WHERE application_number = ? AND version = ?`。更新した行の数を返す）と `void insertHistory(String applicationNumber, StatusHistoryEntry)` を足す。T032 が green になる
- [ ] T039 [US3] `{main}/{ca}/service/StatusConflictException.java`（メッセージは決まった文と申込番号だけ）を作り、`CardApplicationService` に `changeStatus(String applicationNumber, ApplicationStatus to, String staffName, String rejectionReason, int version)` を足す（`@Transactional`）。data-model.md 4 の 3〜6 の順：`findByNumber`（ない → `ApplicationNotFoundException`）→ `version` が違う → `StatusConflictException` → `changeStatus`（`BusinessTime.now(clock)`）→ `updateStatus` が 0 → `StatusConflictException`（トランザクションを戻す）→ `insertHistory` → 申込と履歴を読み直して返す。T031 が green になる
- [ ] T040 [US3] `ApiExceptionHandler` に `StatusConflictException` → 409 の Problem Details（`type` `about:blank`・`title` `ほかの担当者が先に状態を変えました`・`detail` `最新の状態を確かめてから、もう一度操作してください`）を足す
- [ ] T041 [US3] `CardApplicationController` に `POST /api/card-applications/{applicationNumber}/status-changes` を足す：`StatusChangeInputValidator` → `service.changeStatus` → 200・`CardApplicationDetailResponse`。`StatusTransitionException` は、理由に応じた contracts 4 のメッセージの `InputValidationException`（`field` `toStatus`）に変える（リボ払いの `RevolvingNotPayableException` と同じ形。002 の R4）。T033・T034 が green になる

**Checkpoint**: まとまり A が終わり。curl で quickstart.md 4 をすべて確かめられる。`./mvnw verify` が通り、バックエンドの行・分岐カバレッジが90%以上。

---

# B. 画面（Day7 午後）

## Phase 5: Foundational（タブとフロントの部品）

**Purpose**: どの画面にも要る、タブ・API の呼び出し・型・表示の整形・CSS の部品。

- [ ] T042 [P] タブのテストを `frontend/src/App.test.tsx` に足す：タブが「分割払い」「リボ払い」「申込管理」の3つ、「申込管理」を選ぶと `aria-selected` が true になり、**本物の `ApplicationPage`** が「新規登録」のボタンを出す（`ApplicationPage` は `vi.mock` で差し替えない。2026-11-25、analyze の C1 から直した）。Phase 7 で一覧が最初に API を呼ぶようになったときのために、差し替えてよいのは `{fe}/api.ts` だけ（`list` は `{ kind: 'success', result: { items: [] } }` を返す）。既存の分割払い・リボ払いのタブのテストは変えない
- [ ] T043 [P] 表示の整形のテストを書く：`frontend/src/shared/formatDateTime.test.ts` と `{fe}/formatManYen.test.ts`（FR-011）。日時の表示は **`2026/11/25 00:00` の形（年/月/日、月・日・時・分は2桁、24時間制、日本時間）**（2026-11-25、analyze の U1 から人が決めた）：`"2026-11-24T15:00:00Z"` → `"2026/11/25 00:00"`、`"2026-11-25T01:30:00Z"` → `"2026/11/25 10:30"`、`"2026-01-04T23:05:00Z"` → `"2026/01/05 08:05"`、`"2026-11-25T14:59:00Z"` → `"2026/11/25 23:59"`（UTC の日付・時刻と違う値なので、ブラウザやテストの実行環境のタイムゾーンに頼っていれば失敗する）。年収：`0` → `"0万円"`、`450` → `"450万円"`、`9999` → `"9,999万円"`
- [ ] T044 [P] 状態の表示のテストを書く：`{fe}/statusLabels.test.ts`。`APPLIED`→「申込」、`UNDER_REVIEW`→「審査中」、`APPROVED`→「承認」、`REJECTED`→「否決」。出すボタン：`APPLIED` → `UNDER_REVIEW`（「審査中にする」）だけ、`UNDER_REVIEW` → `APPROVED`（「承認する」）と `REJECTED`（「否決する」）、`APPROVED`・`REJECTED` → なし（research.md R12）
- [ ] T045 [P] API の呼び出しのテストを書く：`{fe}/api.test.ts`（`fetch` を `vi.fn` で差し替える）。`register`・`list(status?)`・`getDetail(number)`・`changeStatus(number, request)` が contracts の URL・メソッド・本文で呼ぶ（入力は文字列のまま送る。`list` は `status` が空なら付けない）。結果の種類：200・201 → `success`、`errors` のある 400 → `inputError`、404 → `notFound`、409 → `conflict`（`detail` を持つ）、それ以外・`errors` のない 400・通信の失敗 → `unexpectedError`
- [ ] T046 T042〜T045 が失敗すること（red）を確かめてから、`{fe}/types.ts`（contracts と同じ項目名の型。状態は4つの値の union）、`frontend/src/shared/formatDateTime.ts`（`Intl.DateTimeFormat('ja-JP', { timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })`。T043 の形にならないときは、`formatToParts` で組み立てる）、`{fe}/formatManYen.ts`（`Intl.NumberFormat('ja-JP')`。表示だけで、計算しない）、`{fe}/statusLabels.ts`、`{fe}/api.ts` を作る。`api.ts` は `shared/postSimulation.ts` を変えずに、この機能の中に書く（返す種類が違うため）。`console.log` を使わない
- [ ] T047 T042 が失敗すること（red）を確かめてから、`{fe}/ApplicationPage.tsx` を「新規登録」のボタン（`.button-secondary`）だけを出す形で作り（押したときの動きと、一覧・登録・詳細の切り替えは Phase 6〜8 で、それぞれのテストを先に書いてから足す）、`frontend/src/App.tsx` の `TABS` に `{ id: 'application', label: '申込管理' }` を足して表示する。見出し「カード支払シミュレーション」は変えない。T042 が green になる
- [ ] T048 `frontend/src/index.css` に、足りない部品だけを足す：注意書きの枠（`.notice`）、状態の小さな札（`.status-badge`）、控えめなボタン（`.button-secondary`。一覧に戻る・新規登録を開く）。色・間隔・角の丸みは `styles/tokens.css` の変数だけを使い、新しい色の変数は足さない（足すときは `styles/contrast.test.ts` にも足す）。狭い画面（`@media`）での並びは既存の `.form-field` に合わせる

**Checkpoint**: タブ「申込管理」が出る。API の呼び出しと整形の部品がテストで確かめられている。

---

## Phase 6: User Story 1 - 申込を登録する（画面）(Priority: P1) 🎯 MVP

**Goal**: 登録の画面で6項目を入力して登録でき、誤りは項目ごとに赤字で出る。

**Independent Test**: 登録の画面で架空の6項目を入れて登録 → その申込の詳細が開き、状態「申込」。誤りがあれば、誤った項目すべての下にメッセージ（US1-1・US1-3、FR-020）。

### Tests for User Story 1（先に書き、red を確かめる）

- [ ] T049 [P] [US1] 登録のフォームのテストを書く：`{fe}/ApplicationForm.test.tsx`（`api.ts` は `vi.mock`）。(1) 6項目の入力欄（氏名（漢字）・氏名（カナ）・生年月日（`type="date"`）・年収（`inputMode="numeric"`、単位「万円」の表示）・勤務先名・電話番号（`inputMode="numeric"`））があり、ラベルで探せる、(2) 「ダミーの情報だけを入力してください。本物の氏名・電話番号などは入力しないでください」の注意書きがある（FR-020）、(3) カード番号の入力欄がない（FR-021）、(4) 入力した文字列をそのまま `register` に渡す、(5) `inputError` のとき、各項目の入力欄のすぐ下に `role="alert"` でメッセージを出し、入力欄に `aria-invalid="true"`、(6) 登録の実行中はボタンを押せない、(7) もう一度登録するとき、前のエラーを消す、(8) `unexpectedError` のとき、予期しないエラーのメッセージを出す、(9) `success` のとき `onRegistered(applicationNumber)` を呼ぶ
- [ ] T050 [P] [US1] 登録から詳細へ移ることのテストを `{fe}/ApplicationPage.test.tsx` に書く：「新規登録」を押すと登録のフォームが出る、登録が成功すると、その申込の詳細（`getDetail` を返した申込番号で呼ぶ）が出る

### Implementation for User Story 1

- [ ] T051 [US1] `{fe}/ApplicationForm.tsx` を作る。既存の部品（`.form`・`.form-field`・`.button`・`.field-error`、`shared/fieldErrors.tsx`・`shared/useReturnFocus.ts`）と T048 の `.notice` を使う。T049 が green になる
- [ ] T052 [US1] `{fe}/ApplicationPage.tsx` の「新規登録」のボタン（T047 で作った）に、登録のフォームを開く動きと、登録 → 詳細の切り替えを足す（詳細の中身は Phase 7 で作るので、ここでは申込番号を見出しに出すだけでよい）。T050 が green になる

**Checkpoint**: 画面から登録でき、quickstart.md 5 の #2〜#4 を確かめられる。

---

## Phase 7: User Story 2 - 申込の一覧と詳細を見る（画面）(Priority: P2)

**Goal**: 一覧を状態で絞り込み、1件を選んで詳細（全項目・変更履歴）を見られる。

**Independent Test**: 一覧に新しい順で出て、状態で絞り込め、0件のときはメッセージ。1件を選ぶと詳細。ない申込は「申込が見つかりません」（US2-1〜US2-7）。

### Tests for User Story 2（先に書き、red を確かめる）

- [ ] T053 [P] [US2] 一覧のテストを書く：`{fe}/ApplicationList.test.tsx`（`api.ts` は `vi.mock`）。(1) 表の列が申込番号・氏名（漢字）・状態（表示名）・申込日時（日本時間）で、API が返した順のまま並べる（並べ替えない）、(2) 状態の `select` は「すべて」「申込」「審査中」「承認」「否決」で、最初は「すべて」、選ぶと `list(status)` を呼び直す、「すべて」に戻すと `status` なしで呼ぶ（US2-2・US2-3）、(3) `items` が空のとき「該当する申込はありません」（US2-4・US2-6）、(4) 申込番号のボタンを押す（キーボードでも）と `onSelect(applicationNumber)` を呼ぶ（US2-5）、(5) 一覧を開くたびに API を呼び、最新の状態を出す（spec のエッジケース「絞り込んだあとに、ほかの担当者が状態を変えた」）
- [ ] T054 [P] [US2] 詳細のテストを書く：`{fe}/ApplicationDetail.test.tsx`。(1) 全項目・状態・申込日時・更新日時（日本時間）が出る。年収は「450万円」（FR-010・FR-011）、(2) 変更履歴の表（変更日時・変更前→変更後・担当者名・否決の理由）が API の順（古い順）で出る。履歴が0行のときは「変更履歴はありません」、(3) `notFound` のとき「申込が見つかりません」と、一覧に戻るボタン（US2-7）、(4) 一覧に戻るボタンで `onBack` を呼ぶ

### Implementation for User Story 2

- [ ] T055 [US2] `{fe}/ApplicationList.tsx` を作る（`.form-field` の `select`、`.table-wrap`・`.schedule-table` の表、`.status-badge`）。T053 が green になる
- [ ] T056 [US2] `{fe}/ApplicationDetail.tsx` を作る（`.summary` の `dl`、履歴は `.table-wrap`・`.schedule-table`、`.button-secondary` の一覧に戻るボタン）。`ApplicationPage.tsx` の最初の表示を一覧にし、一覧 → 詳細 → 一覧の切り替えを足す。T054 と T050 が green のまま

**Checkpoint**: quickstart.md 5 の #1・#5〜#7 を確かめられる。

---

## Phase 8: User Story 3 - 審査の状態を進める（画面）(Priority: P3)

**Goal**: 詳細の画面で、担当者名（と否決の理由）を入力し、今の状態から許される変更だけをボタンで行える。ぶつかったら、最新の状態を見せる。

**Independent Test**: 申込 → 審査中 → 否決と変え、そのたびに状態と履歴が更新される。担当者名なし・理由なしはエラー、承認・否決の申込にはボタンが出ない、ぶつかったらメッセージと最新の状態（US3-1〜US3-8、FR-019）。

### Tests for User Story 3（先に書き、red を確かめる）

- [ ] T057 [P] [US3] 状態の変更のフォームのテストを書く：`{fe}/StatusChangeForm.test.tsx`（`api.ts` は `vi.mock`）。(1) 状態が「申込」のとき「審査中にする」だけ、「審査中」のとき「承認する」「否決する」だけが出る（US3-6）、(2) 「承認」「否決」のとき、フォームの代わりに「審査が終わった申込の状態は変えられません」（US3-7）、(3) 担当者名と否決の理由の入力欄（理由は「審査中」のときだけ出す）、(4) ボタンを押すと `changeStatus(番号, { toStatus, staffName, rejectionReason, version })` を呼ぶ。`version` は詳細で受け取った値を文字列にして送る。担当者名などは入力のまま送る、(5) `inputError` のとき、該当する入力欄の下に `role="alert"` でメッセージ（US3-4・US3-5）、(6) `conflict` のとき、フォームの上に `role="alert"` で `detail` を出し、`onConflict` を呼ぶ。入力していた担当者名は残す（FR-019、research.md R12）、(7) `success` のとき `onChanged(detail)` を呼び、担当者名と理由の入力欄を空にする、(8) 実行中はボタンを押せない
- [ ] T058 [P] [US3] 詳細と状態の変更のつながりのテストを `{fe}/ApplicationDetail.test.tsx` に足す：(1) 変更が成功すると、返った詳細で状態・更新日時・履歴が置き換わる（US3-1）、(2) `conflict` のあと、`getDetail` を呼び直して最新の状態と履歴を出し、ぶつかったことのメッセージは残る

### Implementation for User Story 3

- [ ] T059 [US3] `{fe}/StatusChangeForm.tsx` を作る（どのボタンを出すかは `statusLabels.ts` で決める。フロントは遷移の正しさを判定しない）。T057 が green になる
- [ ] T060 [US3] `{fe}/ApplicationDetail.tsx` に `StatusChangeForm` を組み込み、成功のときの置き換えと、ぶつかったときの読み直しを足す。T058 が green になる

**Checkpoint**: まとまり B が終わり。quickstart.md 5 の #1〜#14 を確かめられる。**Must はここまで。**

---

# C. Should（Must がすべて終わってから。遅れたら、このフェーズごと外す）

## Phase 9: User Story 4 - キーワードで探し、ページを分けて見る (Priority: P4・Should)

**Goal**: 一覧をキーワード（氏名（漢字）・氏名（カナ）・申込番号の部分一致）で絞り込み、20件ずつのページで見られる。

**Independent Test**: 25件登録して、1ページ目に新しい順の20件、2ページ目に5件。キーワード「タロウ」で該当だけ、状態と組み合わせられる（US4-1〜US4-4）。

**外すとき**: このフェーズで足したパラメーター（`keyword`・`page`）・項目（`page`・`totalPages`・`totalItems`）・画面の部品と、そのテストを外す。Phase 1〜8 のコードとテストは変えずに済む（research.md R13）。

### Tests for User Story 4（先に書き、red を確かめる）

- [ ] T061 [P] [US4] 検索とページのリポジトリのテストを `{test}/{ca}/repository/CardApplicationSearchRepositoryTest.java` に書く（Testcontainers、`@BeforeEach` で片付け）：(1) 氏名（カナ）「テスト　タロウ」・「ミホン　ハナコ」と、氏名（漢字）に「タロウ」を含む申込を入れ、キーワード「タロウ」で、漢字・カナ・申込番号のどれかに含むものだけが出る（US4-1）、(2) 申込番号の一部（`"00002"`）でも探せる、(3) キーワードと状態の両方に合うものだけ（US4-2）、(4) 25件で、1ページ目が新しい順の20件・2ページ目が5件・`totalItems` 25・`totalPages` 2、3ページ目は空（US4-3）、(5) 0件のとき `totalPages` 0、(6) キーワード `%`・`_`・`\` は文字そのものとして探す（全件が出ない）
- [ ] T062 [P] [US4] 検索とページの API のテストを `{test}/{ca}/web/CardApplicationSearchApiTest.java` に書く（contracts 6）：(1) `?keyword=タロウ&status=APPLIED&page=2` の形で呼べ、`items`・`page`・`totalPages`・`totalItems` が返る、(2) `keyword` を省略・空・空白だけ → 絞り込まない、(3) `keyword` 51文字 → 400・`keyword`・contracts のメッセージ、(4) `page` が `0`・`-1`・`abc` → 400・`page`、省略は1、(5) Must の `GET /api/card-applications`（パラメーターなし）の `items` は今までと同じ
- [ ] T063 [P] [US4] 一覧の画面のテストを `{fe}/ApplicationListSearch.test.tsx` に書く：キーワードの入力欄と「検索」ボタンで `list({ status, keyword, page: 1 })` を呼ぶ、「前へ」「次へ」と「n / m ページ」、最初のページで「前へ」、最後のページで「次へ」を押せない、該当なしは「該当する申込はありません」（US4-4）

### Implementation for User Story 4

- [ ] T064 [US4] `CardApplicationRepository` に `search(Optional<ApplicationStatus>, Optional<String> keyword, int page)`（`ILIKE '%…%' ESCAPE '\'`、`%`・`_`・`\` をエスケープ、`LIMIT 20 OFFSET (page - 1) * 20`、件数は `count(*)`）を足す。Must の `findAll` は変えない。T061 が green になる
- [ ] T065 [US4] `CardApplicationService` に `search` を、`CardApplicationListResponse` に `page`・`totalPages`・`totalItems` を、`CardApplicationController` の一覧に `keyword`・`page` のパラメーターとそのチェック（contracts 6 のメッセージ）を足す。T062 が green になり、Must の一覧のテスト（T023）も green のまま
- [ ] T066 [US4] `{fe}/api.ts` の `list` に `keyword`・`page` を、`{fe}/types.ts` に3つの項目を、`{fe}/ApplicationList.tsx` にキーワードの入力欄とページの切り替えを足す。T063 が green になり、T053 も green のまま

**Checkpoint**: quickstart.md 7 を確かめられる。

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: 仕上げと、PR を出す前の確認（原則VI）。

- [ ] T067 [P] 金額（年収）に `double`・`float`・`int` を使っていないことを確かめる：自作スキル①（money-calc-review）で `src/main/java` と `frontend/src` 全体を確かめる。年収は `BigDecimal` で、比べるのは `compareTo`、`new BigDecimal` の引数は文字列だけ、丸めはない（原則III、R5）。結果をこのタスクの下に記録する
- [ ] T068 [P] タイムゾーンの決まりを確かめる：`grep -rnE 'LocalDate\.now\(|LocalDateTime\.now\(|ZonedDateTime\.now\(|Instant\.now\(\)|ZoneId\.systemDefault|TimeZone\.getDefault' src/main/java` が何も出さない。`Asia/Tokyo` が `BusinessTime.java` の1か所だけにある。フロントの日時の表示がすべて `formatDateTime`（`timeZone: 'Asia/Tokyo'`）を通っている（R10）。結果をこのタスクの下に記録する
- [ ] T069 [P] 個人情報をログ・例外に出さない決まりを確かめる（R11-1）：(1) `grep -rnE '\blog(ger)?\.|System\.(out|err)|printStackTrace' src/main/java/com/example/cardpayment/cardapplication` が何も出さない、(2) `grep -rn 'new [A-Za-z]*Exception(' src/main/java/com/example/cardpayment/cardapplication` で出た行を全部読み、引数に入力の値（氏名・生年月日・年収・勤務先名・電話番号・担当者名・理由）を入れていない、(3) `grep -rnE 'console\.' frontend/src --include='*.ts' --include='*.tsx' | grep -v '\.test\.'` が何も出さない、(4) `application.properties` に `log-request-details`・`logging.level.org.springframework.web`・アクセスログの設定がない、(5) contracts のメッセージの一覧に、入力を埋め込む文がない。結果をこのタスクの下に記録する
- [ ] T070 [P] カード番号と本物の個人情報が入っていないことを確かめる（原則IV）：`git diff main --stat` の全ファイルで、テスト・例の値が research.md R11 の架空の値だけであること、カード番号の形（13〜19桁の数字の並び）がないことを読んで確かめる。結果をこのタスクの下に記録する
- [ ] T071 起動しているアプリ（`spring-boot:run`・`npm run dev`）を止めてから、`./mvnw verify` で、全テストが通り、行・分岐カバレッジがバックエンド・フロントエンドとも90%以上であることを確かめる（原則II）。結果（件数・カバレッジ）をこのタスクの下に記録する。テスト用の JVM が途中で止まったときは、`clean` の前に CLAUDE.md の手順で証拠を残し、人に報告する
- [ ] T072 [quickstart.md](quickstart.md) の 1〜5（Should を作ったときは 7 も）を上から順に行う。SC-001・SC-002・SC-006 と、画面の #1〜#14 は人が確かめる。結果をこのタスクの下に記録する
- [ ] T073 PR を出す前に、スキル②（pre-pr-check）を使い、結果の一覧を PR の説明文に貼る（CLAUDE.md）。AI の出力を人が確かめた結果は、人が `docs/ai-review-log.md` に記録する
- [ ] T074 main に入ったあと、EC2 で quickstart.md 6 を確かめる（Flyway の V2 の適用、日本時間の表示、ログに個人情報がないこと。sprint-plan の Day7 のゴール）→ **判断③**

---

## Dependencies & Execution Order

### フェーズの依存

- **まとまり A**
  - **Phase 1（DB と共通の部品）**：すぐ始められる。A・B・C のすべての前提（B は API の形だけに依存するので、厳密には Phase 1 を待たなくてもよい）
  - **US1（Phase 2）**：Phase 1 のあと
  - **US2（Phase 3）**：US1 のあと（`CardApplicationRepository`・`CardApplicationRepositoryTest`・`CardApplicationApiTest`・`CardApplicationController` に足すため。テストのデータも登録の API で作る）
  - **US3（Phase 4）**：US2 のあと（詳細の `history` を読むため。同じファイルに足すため）
- **まとまり B**
  - **Phase 5（タブと部品）**：A に依存しない（API は `vi.mock`）。sprint-plan どおり A のあとに進めるのを基本にする
  - **US1〜US3（Phase 6〜8）**：Phase 5 のあと、この順に（`ApplicationPage.tsx`・`ApplicationDetail.tsx` に足すため）
- **まとまり C（Phase 9）**：Phase 1〜8 がすべて終わってから。T072（quickstart）の Must の部分も終わってからが望ましい
- **Polish（Phase 10）**：Must（と、作ったときは Should）のあと

### ストーリーの中の順番

- テスト（red を確かめる）→ 型（コンパイルの red を確かめてから）→ 実装（green）→ 整える（refactor）
- バックエンド：ドメイン → 入力チェック（web）→ リポジトリ → サービス → 例外の変換 → Controller
- フロント：部品 → 画面全体

### 並行して進められるもの

- Phase 1：T004（時刻）・T006（テストの時計）・T007（DB のエラー）・T009（状態）は、T002・T003 と並行できる
- US1（A）：テストの T011・T012・T013 は並行できる。T014・T015 は DB を使うので、T003 のあと
- US3（A）：テストの T028・T029・T030・T031 は並行できる（T028 と T009 は同じファイルだが、T009 は Phase 1 で終わっている）
- Phase 5：T042〜T045 は並行できる
- US1〜US3（B）：各フェーズのテスト（T049・T050、T053・T054、T057・T058）は並行できる
- Phase 9：T061・T062・T063 は並行できる
- Phase 10：T067〜T070 は並行できる

---

## Parallel Example: User Story 1（まとまり A）

```bash
# US1 のテストを並行して書く（それぞれ red を確かめる）：
Task: "登録の入力チェックのテストを {test}/cardapplication/web/CardApplicationInputValidatorTest.java に書く"
Task: "申込日がずれないことのテストを {test}/cardapplication/web/CardApplicationInputValidatorTimeZoneTest.java に書く"
Task: "toString のテストを {test}/cardapplication/PersonalDataToStringTest.java に書く"
```

## Parallel Example: User Story 3（まとまり A）

```bash
# 遷移のルール・入力チェック・サービスのテストを並行して書く（どれも DB を使わない）：
Task: "遷移の16通りのテストを {test}/cardapplication/domain/CardApplicationTest.java に書く"
Task: "状態の変更の入力チェックのテストを {test}/cardapplication/web/StatusChangeInputValidatorTest.java に書く"
Task: "保存の時点でぶつかった場合のテストを {test}/cardapplication/service/CardApplicationServiceTest.java に書く"
```

---

## Implementation Strategy

### MVP を先に（US1 だけ）

1. Phase 1：DB と共通の部品（`./mvnw verify` が通ることを確かめる）
2. Phase 2：US1 のバックエンド
3. **止めて確かめる**：curl で登録と入力エラーを確かめる（quickstart.md 4）
4. Phase 5・6：タブと US1 の画面

### 少しずつ届ける（sprint-plan の Day7）

1. 午前：まとまり A（Phase 1〜4）→ curl で quickstart.md 4 を確かめる
2. 午後：まとまり B（Phase 5〜8）→ 画面で quickstart.md 5 を確かめる
3. Polish（T067〜T073）→ PR → EC2 で確かめる → **判断③**
4. 時間があれば：まとまり C（Phase 9）を別の PR で

### 遅れたとき（sprint-plan「遅れたときに削る順番」）

1. まとまり C（Phase 9、Should）を外す。Must のコードは変えない
2. それでも足りなければ、画面を一覧と状態の変更だけにする：Phase 6（登録の画面）を後回しにし、登録は curl で行う。後回しにしたことを、このファイルに記録する

---

## Notes

- [P] のタスクは、別のファイルで、依存がない
- [Story] のラベルで、どのストーリーのタスクかを追える
- テストは、実装の前に失敗することを確かめる（原則I）。バグの修正も、再現するテストを先に書く
- hook や権限の設定で止められたときは、別の書き方で回避せず、作業を止めて人に報告する（CLAUDE.md）
- コミットと push は、git hooks（lefthook・Claude Code の hooks）の確認を通す（[ADR 0008](../../docs/adr/0008-品質の多層防御.md)）
- V2 のマイグレーションは、main に入ってデプロイされたら書き換えない（直すときは V3）
