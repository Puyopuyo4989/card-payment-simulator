package com.example.cardpayment.cardapplication.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.CardPaymentSimulatorApplication;
import com.example.cardpayment.TestcontainersConfiguration;
import com.example.cardpayment.cardapplication.CardApplicationTables;
import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.NewCardApplication;
import com.example.cardpayment.cardapplication.repository.CardApplicationRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 【Should】一覧のキーワード検索とページ分け（contracts 6、US4）。値はすべて架空のもの。 */
@SpringBootTest(classes = CardPaymentSimulatorApplication.class)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CardApplicationSearchApiTest {

    private static final Instant NOW = Instant.parse("2026-11-25T01:30:00Z");
    private static final String KEYWORD_TOO_LONG = "キーワードは50文字以内で入力してください";
    private static final String PAGE_INVALID = "ページは1以上の半角数字で指定してください";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private CardApplicationRepository repository;

    @BeforeEach
    void setUp() {
        CardApplicationTables.clear(jdbcClient);
    }

    private void insert(String nameKanji, String nameKana, int minutes) {
        repository.insert(
                new NewCardApplication(
                        nameKanji,
                        nameKana,
                        LocalDate.of(1990, 4, 1),
                        new BigDecimal("450"),
                        "テスト株式会社",
                        "0000000000"),
                NOW.plus(Duration.ofMinutes(minutes)));
    }

    private MvcTestResult list(String query) {
        return mvc.get().uri("/api/card-applications" + query).exchange();
    }

    @Test
    void キーワードと状態とページを組み合わせて呼べページの項目が返る() {
        // タロウを含む申込を、状態「申込」で25件
        for (int i = 0; i < 25; i++) {
            insert("テスト 太郎", "テスト　タロウ", i);
        }
        insert("見本 花子", "ミホン　ハナコ", 30);

        var result = list("?keyword=タロウ&status=APPLIED&page=2");

        assertThat(result).hasStatus(HttpStatus.OK);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.items[*].applicationNumber")
                .asArray()
                .containsExactly("A00000005", "A00000004", "A00000003", "A00000002", "A00000001");
        json.extractingPath("$.page").isEqualTo(2);
        json.extractingPath("$.totalPages").isEqualTo(2);
        json.extractingPath("$.totalItems").isEqualTo(25);
    }

    @Test
    void キーワードで絞り込める() {
        insert("テスト 太郎", "テスト　タロウ", 0);
        insert("見本 花子", "ミホン　ハナコ", 1);

        var json = assertThat(list("?keyword=タロウ")).bodyJson();

        json.extractingPath("$.items[*].applicationNumber").asArray().containsExactly("A00000001");
        json.extractingPath("$.totalItems").isEqualTo(1);
    }

    @ParameterizedTest
    // MockMvcTester は URI を自分でエンコードするので、ここには % で書かずに文字そのものを書く
    @ValueSource(strings = {"", "?keyword=", "?keyword=  ", "?keyword=　"})
    void キーワードが省略か空か空白だけなら絞り込まない(String query) {
        insert("テスト 太郎", "テスト　タロウ", 0);
        insert("見本 花子", "ミホン　ハナコ", 1);

        var json = assertThat(list(query)).bodyJson();

        json.extractingPath("$.items[*].applicationNumber")
                .asArray()
                .containsExactly("A00000002", "A00000001");
        json.extractingPath("$.page").isEqualTo(1);
        json.extractingPath("$.totalPages").isEqualTo(1);
        json.extractingPath("$.totalItems").isEqualTo(2);
    }

    @Test
    void キーワードの前後の空白は取り除く() {
        insert("テスト 太郎", "テスト　タロウ", 0);
        insert("見本 花子", "ミホン　ハナコ", 1);

        var json = assertThat(list("?keyword= 花子　")).bodyJson();

        json.extractingPath("$.items[*].applicationNumber").asArray().containsExactly("A00000002");
    }

    @Test
    void キーワードは50文字までなら受け付ける() {
        var result = list("?keyword=" + "a".repeat(50));

        assertThat(result).hasStatus(HttpStatus.OK);
    }

    @Test
    void キーワードが51文字なら400() {
        var result = list("?keyword=" + "a".repeat(51));

        assertInputError(result, "keyword", KEYWORD_TOO_LONG);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "１", "00", "99999999999"})
    void ページが1以上の半角数字でないなら400(String page) {
        var result = list("?page=" + page);

        assertInputError(result, "page", PAGE_INVALID);
    }

    @Test
    void 最後のページより後は空のitems() {
        insert("テスト 太郎", "テスト　タロウ", 0);

        var json = assertThat(list("?page=3")).bodyJson();

        json.extractingPath("$.items").asArray().isEmpty();
        json.extractingPath("$.page").isEqualTo(3);
        json.extractingPath("$.totalPages").isEqualTo(1);
        json.extractingPath("$.totalItems").isEqualTo(1);
    }

    @Test
    void 状態とページの両方の誤りを一度に返す() {
        var result = list("?status=applied&page=0");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("status", "page");
    }

    @Test
    void パラメーターなしのitemsは今までと同じ新しい順() {
        insert("テスト 太郎", "テスト　タロウ", 0);
        insert("見本 花子", "ミホン　ハナコ", 1);
        repository.updateStatus("A00000001", ApplicationStatus.UNDER_REVIEW, NOW, 0);

        var json = assertThat(list("")).bodyJson();

        json.extractingPath("$.items[*].applicationNumber")
                .asArray()
                .containsExactly("A00000002", "A00000001");
        json.extractingPath("$.items[1].status").isEqualTo("UNDER_REVIEW");
        json.extractingPath("$.items[1].nameKanji").isEqualTo("テスト 太郎");
    }

    private static void assertInputError(MvcTestResult result, String field, String message) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.title").isEqualTo("入力に誤りがあります");
        json.extractingPath("$.errors[0].field").isEqualTo(field);
        json.extractingPath("$.errors[0].message").isEqualTo(message);
    }
}
