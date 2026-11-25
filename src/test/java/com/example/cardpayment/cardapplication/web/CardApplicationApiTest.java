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
import java.time.Instant;
import java.time.LocalDate;
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
