package com.example.cardpayment.cardapplication.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.CardPaymentSimulatorApplication;
import com.example.cardpayment.TestcontainersConfiguration;
import com.example.cardpayment.cardapplication.CardApplicationTables;
import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.repository.CardApplicationRepository;
import com.example.cardpayment.common.config.MutableTestClock;
import com.example.cardpayment.common.config.TestClockConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 申込管理の API（contracts/card-application-api.md）。値はすべて架空のもの。 */
@SpringBootTest(classes = CardPaymentSimulatorApplication.class)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class CardApplicationApiTest {

    private static final String VALID_REQUEST =
            """
            {"nameKanji":"テスト 太郎","nameKana":"テスト　タロウ","birthDate":"1990-04-01",
             "annualIncome":"450","employerName":"テスト株式会社","phoneNumber":"0000000000"}
            """;

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private MutableTestClock clock;
    @Autowired private CardApplicationRepository repository;

    @BeforeEach
    void setUp() {
        CardApplicationTables.clear(jdbcClient);
        clock.set(TestClockConfiguration.START);
    }

    // 1. 申込を登録する（US1）

    @Test
    void 登録すると201と申込番号の場所と詳細を返す() {
        var result = register(VALID_REQUEST);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).headers().hasValue("Location", "/api/card-applications/A00000001");
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.applicationNumber").isEqualTo("A00000001");
        json.extractingPath("$.nameKanji").isEqualTo("テスト 太郎");
        json.extractingPath("$.nameKana").isEqualTo("テスト　タロウ");
        json.extractingPath("$.birthDate").isEqualTo("1990-04-01");
        json.extractingPath("$.annualIncome").isEqualTo(450);
        json.extractingPath("$.employerName").isEqualTo("テスト株式会社");
        json.extractingPath("$.phoneNumber").isEqualTo("0000000000");
        json.extractingPath("$.status").isEqualTo("APPLIED");
        json.extractingPath("$.appliedAt").isEqualTo("2026-11-25T01:30:00Z");
        json.extractingPath("$.updatedAt").isEqualTo("2026-11-25T01:30:00Z");
        json.extractingPath("$.version").isEqualTo(0);
        json.extractingPath("$.history").asArray().isEmpty();
    }

    @Test
    void 年収は整数のまま返す() {
        var result = register(VALID_REQUEST);

        // JSON を読み込んでから比べると 450 と 450.0 の違いが消えるので、本文の文字列で確かめる
        assertThat(result)
                .body()
                .asString()
                .contains("\"annualIncome\":450")
                .doesNotContain("450.0", "4.5E+2");
    }

    @Test
    void 項目にJSONの数値が来ても文字列として受け付ける() {
        var result =
                register(
                        """
                        {"nameKanji":"テスト 太郎","nameKana":"テスト　タロウ","birthDate":"1990-04-01",
                         "annualIncome":450,"employerName":"テスト株式会社","phoneNumber":"0000000000"}
                        """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.annualIncome").isEqualTo(450);
    }

    @Test
    void 登録した内容はDBに残る() {
        register(VALID_REQUEST);

        CardApplication saved = repository.findByNumber("A00000001").orElseThrow();
        assertThat(saved.nameKanji()).isEqualTo("テスト 太郎");
        assertThat(saved.birthDate()).isEqualTo(LocalDate.of(1990, 4, 1));
        assertThat(saved.annualIncome()).isEqualByComparingTo("450");
        assertThat(saved.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(saved.appliedAt()).isEqualTo(Instant.parse("2026-11-25T01:30:00Z"));
    }

    @Test
    void 同じ内容を2回登録すると別の申込番号になる() {
        register(VALID_REQUEST);
        var second = register(VALID_REQUEST);

        assertThat(second).bodyJson().extractingPath("$.applicationNumber").isEqualTo("A00000002");
        assertThat(countApplications()).isEqualTo(2);
    }

    @Test
    void 誤りのある入力は400と項目ごとのエラーを返し何も保存しない() {
        var result =
                register(
                        """
                        {"nameKanji":"","nameKana":"てすと","birthDate":"2026-02-30",
                         "annualIncome":"10000","employerName":"テスト株式会社","phoneNumber":"03-0000-0000"}
                        """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.type").isEqualTo("about:blank");
        json.extractingPath("$.title").isEqualTo("入力に誤りがあります");
        json.extractingPath("$.status").isEqualTo(400);
        json.extractingPath("$.instance").isEqualTo("/api/card-applications");
        json.extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly(
                        "nameKanji", "nameKana", "birthDate", "annualIncome", "phoneNumber");
        json.extractingPath("$.errors[0].message").isEqualTo("氏名（漢字）を入力してください");
        assertThat(result).body().asString().doesNotContain("てすと", "03-0000-0000", "2026-02-30");
        assertThat(countApplications()).isZero();
    }

    @Test
    void 本文がJSONとして読めないときは400でerrorsを付けない() {
        var result = register("{");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.title").isEqualTo("リクエストの形式が正しくありません");
        json.doesNotHavePath("$.errors");
        assertThat(countApplications()).isZero();
    }

    // 2. 申込の一覧（US2）

    @Test
    void 一覧は申込日時の新しい順で4項目だけを返す() {
        registerThreeMinutesApart();

        var result = mvc.get().uri("/api/card-applications").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.items[*].applicationNumber")
                .asArray()
                .containsExactly("A00000003", "A00000002", "A00000001");
        json.extractingPath("$.items[0].nameKanji").isEqualTo("テスト 太郎");
        json.extractingPath("$.items[0].status").isEqualTo("APPLIED");
        json.extractingPath("$.items[0].appliedAt").isEqualTo("2026-11-25T01:32:00Z");
        // 一覧に要らない個人情報は返さない（research.md R8）
        assertThat(result)
                .body()
                .asString()
                .doesNotContain(
                        "birthDate", "phoneNumber", "annualIncome", "nameKana", "employerName");
    }

    @Test
    void 一覧を状態で絞り込める() {
        registerThreeMinutesApart();
        setStatus("A00000002", "UNDER_REVIEW");

        var underReview = mvc.get().uri("/api/card-applications?status=UNDER_REVIEW").exchange();
        var applied = mvc.get().uri("/api/card-applications?status=APPLIED").exchange();

        assertThat(underReview)
                .bodyJson()
                .extractingPath("$.items[*].applicationNumber")
                .asArray()
                .containsExactly("A00000002");
        assertThat(applied)
                .bodyJson()
                .extractingPath("$.items[*].applicationNumber")
                .asArray()
                .containsExactly("A00000003", "A00000001");
    }

    @Test
    void 状態が空のときはすべてを返す() {
        registerThreeMinutesApart();

        var result = mvc.get().uri("/api/card-applications?status=").exchange();

        assertThat(result).bodyJson().extractingPath("$.items").asArray().hasSize(3);
    }

    @Test
    void 該当する申込がないときは空のitemsを返す() {
        var none = mvc.get().uri("/api/card-applications").exchange();
        registerThreeMinutesApart();
        var rejected = mvc.get().uri("/api/card-applications?status=REJECTED").exchange();

        assertThat(none).hasStatus(HttpStatus.OK);
        assertThat(none).body().asString().isEqualToIgnoringWhitespace("{\"items\":[]}");
        assertThat(rejected).body().asString().isEqualToIgnoringWhitespace("{\"items\":[]}");
    }

    @Test
    void 状態が4つの値のどれでもないときは400() {
        var result = mvc.get().uri("/api/card-applications?status=applied").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.title").isEqualTo("入力に誤りがあります");
        json.extractingPath("$.errors[0].field").isEqualTo("status");
        json.extractingPath("$.errors[0].message")
                .isEqualTo("状態は APPLIED・UNDER_REVIEW・APPROVED・REJECTED のどれかを指定してください");
    }

    // 3. 申込の詳細（US2）

    @Test
    void 詳細は全項目と変更履歴を古い順に返す() {
        register(VALID_REQUEST);
        setStatus("A00000001", "REJECTED");
        insertHistory(
                "A00000001",
                "2026-11-25T03:10:00Z",
                "UNDER_REVIEW",
                "REJECTED",
                "テスト 担当B",
                "テスト用の否決理由");
        insertHistory(
                "A00000001", "2026-11-25T02:00:00Z", "APPLIED", "UNDER_REVIEW", "テスト 担当A", null);

        var result = mvc.get().uri("/api/card-applications/A00000001").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.applicationNumber").isEqualTo("A00000001");
        json.extractingPath("$.nameKana").isEqualTo("テスト　タロウ");
        json.extractingPath("$.birthDate").isEqualTo("1990-04-01");
        json.extractingPath("$.annualIncome").isEqualTo(450);
        json.extractingPath("$.employerName").isEqualTo("テスト株式会社");
        json.extractingPath("$.phoneNumber").isEqualTo("0000000000");
        json.extractingPath("$.status").isEqualTo("REJECTED");
        json.extractingPath("$.appliedAt").isEqualTo("2026-11-25T01:30:00Z");
        json.extractingPath("$.version").isEqualTo(0);
        json.extractingPath("$.history[0].changedAt").isEqualTo("2026-11-25T02:00:00Z");
        json.extractingPath("$.history[0].fromStatus").isEqualTo("APPLIED");
        json.extractingPath("$.history[0].toStatus").isEqualTo("UNDER_REVIEW");
        json.extractingPath("$.history[0].staffName").isEqualTo("テスト 担当A");
        json.extractingPath("$.history[0].rejectionReason").isNull();
        json.extractingPath("$.history[1].toStatus").isEqualTo("REJECTED");
        json.extractingPath("$.history[1].staffName").isEqualTo("テスト 担当B");
        json.extractingPath("$.history[1].rejectionReason").isEqualTo("テスト用の否決理由");
    }

    @Test
    void ない申込の詳細は404() {
        register(VALID_REQUEST);

        for (String number : new String[] {"A99999999", "abc"}) {
            var result = mvc.get().uri("/api/card-applications/" + number).exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            var json = assertThat(result).bodyJson();
            json.extractingPath("$.type").isEqualTo("about:blank");
            json.extractingPath("$.title").isEqualTo("申込が見つかりません");
            json.extractingPath("$.status").isEqualTo(404);
            json.extractingPath("$.detail").isEqualTo("申込番号を確かめてください");
            json.extractingPath("$.instance").isEqualTo("/api/card-applications/" + number);
        }
    }

    // 4. 状態を変える（US3）

    @Test
    void 申込を審査中にすると変更後の詳細と履歴の1行を返す() {
        register(VALID_REQUEST);
        clock.advance(Duration.ofMinutes(30));

        var result = changeStatus("A00000001", review(0));

        assertThat(result).hasStatus(HttpStatus.OK);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.status").isEqualTo("UNDER_REVIEW");
        json.extractingPath("$.appliedAt").isEqualTo("2026-11-25T01:30:00Z");
        json.extractingPath("$.updatedAt").isEqualTo("2026-11-25T02:00:00Z");
        json.extractingPath("$.version").isEqualTo(1);
        json.extractingPath("$.history").asArray().hasSize(1);
        json.extractingPath("$.history[0].changedAt").isEqualTo("2026-11-25T02:00:00Z");
        json.extractingPath("$.history[0].fromStatus").isEqualTo("APPLIED");
        json.extractingPath("$.history[0].toStatus").isEqualTo("UNDER_REVIEW");
        json.extractingPath("$.history[0].staffName").isEqualTo("テスト 担当A");
        json.extractingPath("$.history[0].rejectionReason").isNull();
    }

    @Test
    void 審査中から承認すると履歴が古い順に2行になる() {
        registerUnderReview();
        clock.advance(Duration.ofMinutes(10));

        var result =
                changeStatus(
                        "A00000001",
                        """
                        {"toStatus":"APPROVED","staffName":"テスト 担当B","version":1}
                        """);

        assertThat(result).hasStatus(HttpStatus.OK);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.status").isEqualTo("APPROVED");
        json.extractingPath("$.version").isEqualTo(2);
        json.extractingPath("$.history[*].toStatus")
                .asArray()
                .containsExactly("UNDER_REVIEW", "APPROVED");
        json.extractingPath("$.history[1].fromStatus").isEqualTo("UNDER_REVIEW");
        json.extractingPath("$.history[1].staffName").isEqualTo("テスト 担当B");
        json.extractingPath("$.history[1].changedAt").isEqualTo("2026-11-25T01:40:00Z");
    }

    @Test
    void 審査中から理由つきで否決すると履歴に理由が残る() {
        registerUnderReview();

        var result = changeStatus("A00000001", reject("テスト用の否決理由", "1"));

        assertThat(result).hasStatus(HttpStatus.OK);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.status").isEqualTo("REJECTED");
        json.extractingPath("$.history[1].toStatus").isEqualTo("REJECTED");
        json.extractingPath("$.history[1].rejectionReason").isEqualTo("テスト用の否決理由");
    }

    @Test
    void 否決の理由がないときは400で何も変えない() {
        registerUnderReview();

        for (String reason : new String[] {"", "　 "}) {
            var result = changeStatus("A00000001", reject(reason, "1"));

            assertInputError(result, "rejectionReason", "否決の理由を入力してください");
        }
        var noField =
                changeStatus(
                        "A00000001",
                        """
                        {"toStatus":"REJECTED","staffName":"テスト 担当A","version":"1"}
                        """);
        assertInputError(noField, "rejectionReason", "否決の理由を入力してください");
        assertUnchanged("UNDER_REVIEW", 1, 1);
    }

    @Test
    void 担当者名がないときは400で何も変えない() {
        register(VALID_REQUEST);

        for (String staff : new String[] {"", "　 "}) {
            var result =
                    changeStatus(
                            "A00000001",
                            "{\"toStatus\":\"UNDER_REVIEW\",\"staffName\":\""
                                    + staff
                                    + "\",\"version\":\"0\"}");

            assertInputError(result, "staffName", "担当者名を入力してください");
        }
        assertUnchanged("APPLIED", 0, 0);
    }

    @Test
    void 申込から承認や否決にはできない() {
        register(VALID_REQUEST);

        var approve =
                changeStatus(
                        "A00000001",
                        """
                        {"toStatus":"APPROVED","staffName":"テスト 担当A","version":"0"}
                        """);
        var rejectResult = changeStatus("A00000001", reject("テスト用の否決理由", "0"));

        assertInputError(approve, "toStatus", "承認・否決の前に、先に「審査中」にしてください");
        assertInputError(rejectResult, "toStatus", "承認・否決の前に、先に「審査中」にしてください");
        assertUnchanged("APPLIED", 0, 0);
    }

    @Test
    void 審査が終わった申込の状態は変えられない() {
        registerUnderReview();
        changeStatus(
                "A00000001",
                """
                {"toStatus":"APPROVED","staffName":"テスト 担当A","version":"1"}
                """);

        var result = changeStatus("A00000001", review(2));

        assertInputError(result, "toStatus", "審査が終わった申込の状態は変えられません");
        assertUnchanged("APPROVED", 2, 2);
    }

    @Test
    void 否決した申込も状態は変えられない() {
        registerUnderReview();
        changeStatus("A00000001", reject("テスト用の否決理由", "1"));

        var result = changeStatus("A00000001", review(2));

        assertInputError(result, "toStatus", "審査が終わった申込の状態は変えられません");
        assertUnchanged("REJECTED", 2, 2);
    }

    @Test
    void 同じ状態や前の状態には変えられない() {
        registerUnderReview();

        var result = changeStatus("A00000001", review(1));

        assertInputError(result, "toStatus", "この状態には変えられません。変えられるのは「申込→審査中」「審査中→承認」「審査中→否決」だけです");
        assertUnchanged("UNDER_REVIEW", 1, 1);
    }

    @Test
    void 古い版で変えようとすると409で何も変えない() {
        registerUnderReview();

        var result =
                changeStatus(
                        "A00000001",
                        """
                        {"toStatus":"APPROVED","staffName":"テスト 担当B","version":"0"}
                        """);

        assertConflict(result, "/api/card-applications/A00000001/status-changes");
        assertUnchanged("UNDER_REVIEW", 1, 1);
    }

    @Test
    void ない申込の状態は変えられない() {
        var result = changeStatus("A99999999", review(0));

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("申込が見つかりません");
    }

    @Test
    void ない申込でも入力の誤りを先に返す() {
        var result =
                changeStatus(
                        "A99999999",
                        """
                        {"toStatus":"UNDER_REVIEW","staffName":"","version":"0"}
                        """);

        assertInputError(result, "staffName", "担当者名を入力してください");
    }

    @Test
    void 古い版で許されない変更をすると遷移より先に409を返す() {
        registerUnderReview();

        // 審査中→審査中は許されないが、版が古いことを先に返す（data-model.md 4 の順番）
        var result = changeStatus("A00000001", review(0));

        assertConflict(result, "/api/card-applications/A00000001/status-changes");
        assertUnchanged("UNDER_REVIEW", 1, 1);
    }

    @Test
    void 状態の変更の本文がJSONとして読めないときは400() {
        register(VALID_REQUEST);

        var result = changeStatus("A00000001", "{");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().doesNotHavePath("$.errors");
        assertUnchanged("APPLIED", 0, 0);
    }

    // 5. 登録・変更しないもの（spec FR-006。tasks.md T075）

    @Test
    void 申込の内容の変更と削除はできない() throws Exception {
        register(VALID_REQUEST);
        String before = detailBody("A00000001");

        var put =
                mvc.put()
                        .uri("/api/card-applications/A00000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("テスト 太郎", "見本 花子"))
                        .exchange();
        var patch =
                mvc.patch()
                        .uri("/api/card-applications/A00000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nameKanji\":\"見本 花子\"}")
                        .exchange();
        var delete = mvc.delete().uri("/api/card-applications/A00000001").exchange();

        assertThat(put).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(patch).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(delete).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(detailBody("A00000001")).isEqualTo(before);
        assertThat(countApplications()).isEqualTo(1);
    }

    private String detailBody(String number) throws Exception {
        return mvc.get()
                .uri("/api/card-applications/" + number)
                .exchange()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** 日本時間 10:30・10:31・10:32 に1件ずつ登録する（A00000001〜A00000003）。 */
    private void registerThreeMinutesApart() {
        for (int i = 0; i < 3; i++) {
            register(VALID_REQUEST);
            clock.advance(Duration.ofMinutes(1));
        }
    }

    /** 状態の更新の API は US3 で作るので、ここでは SQL で直接変える。 */
    private void setStatus(String number, String status) {
        jdbcClient
                .sql(
                        "UPDATE card_application SET status = :status WHERE application_number = :number")
                .param("status", status)
                .param("number", number)
                .update();
    }

    /** 履歴を足す API は US3 で作るので、ここでは SQL で直接入れる。 */
    private void insertHistory(
            String number,
            String changedAt,
            String from,
            String to,
            String staffName,
            String reason) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO card_application_status_history
                          (application_number, changed_at, from_status, to_status, staff_name, rejection_reason)
                        VALUES (:number, :changedAt, :from, :to, :staffName, :reason)
                        """)
                .param("number", number)
                .param(
                        "changedAt",
                        OffsetDateTime.ofInstant(Instant.parse(changedAt), ZoneOffset.UTC))
                .param("from", from)
                .param("to", to)
                .param("staffName", staffName)
                .param("reason", reason)
                .update();
    }

    /** 1件登録し、「テスト 担当A」が審査中にする（version 1・履歴1行）。 */
    private void registerUnderReview() {
        register(VALID_REQUEST);
        assertThat(changeStatus("A00000001", review(0))).hasStatus(HttpStatus.OK);
    }

    private static String review(int version) {
        return "{\"toStatus\":\"UNDER_REVIEW\",\"staffName\":\"テスト 担当A\",\"version\":\""
                + version
                + "\"}";
    }

    private static String reject(String reason, String version) {
        return "{\"toStatus\":\"REJECTED\",\"staffName\":\"テスト 担当B\",\"rejectionReason\":\""
                + reason
                + "\",\"version\":\""
                + version
                + "\"}";
    }

    private MvcTestResult changeStatus(String number, String body) {
        return mvc.post()
                .uri("/api/card-applications/" + number + "/status-changes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static void assertInputError(MvcTestResult result, String field, String message) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.title").isEqualTo("入力に誤りがあります");
        json.extractingPath("$.errors").asArray().hasSize(1);
        json.extractingPath("$.errors[0].field").isEqualTo(field);
        json.extractingPath("$.errors[0].message").isEqualTo(message);
    }

    static void assertConflict(MvcTestResult result, String instance) {
        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.type").isEqualTo("about:blank");
        json.extractingPath("$.title").isEqualTo("ほかの担当者が先に状態を変えました");
        json.extractingPath("$.status").isEqualTo(409);
        json.extractingPath("$.detail").isEqualTo("最新の状態を確かめてから、もう一度操作してください");
        json.extractingPath("$.instance").isEqualTo(instance);
        json.doesNotHavePath("$.errors");
    }

    /** A00000001 の状態・版・履歴の件数が、期待どおりのまま変わっていない。 */
    private void assertUnchanged(String status, int version, int historyCount) {
        CardApplication saved = repository.findByNumber("A00000001").orElseThrow();
        assertThat(saved.status()).isEqualTo(ApplicationStatus.valueOf(status));
        assertThat(saved.version()).isEqualTo(version);
        assertThat(repository.findHistory("A00000001")).hasSize(historyCount);
    }

    private MvcTestResult register(String body) {
        return mvc.post()
                .uri("/api/card-applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private int countApplications() {
        return jdbcClient
                .sql("SELECT count(*) FROM card_application")
                .query(Integer.class)
                .single();
    }
}
