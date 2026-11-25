package com.example.cardpayment.cardapplication.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cardpayment.CardPaymentSimulatorApplication;
import com.example.cardpayment.TestcontainersConfiguration;
import com.example.cardpayment.cardapplication.CardApplicationTables;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Flyway の V2 で作る表と制約（data-model.md 1）。SQL を直接実行して確かめる。値はすべて架空のもの。 */
@SpringBootTest(classes = CardPaymentSimulatorApplication.class)
@Import(TestcontainersConfiguration.class)
class SchemaMigrationTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 11, 25, 1, 30, 0, 0, ZoneOffset.UTC);

    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void clearTables() {
        CardApplicationTables.clear(jdbcClient);
    }

    @Test
    void FlywayがV2を当てている() {
        Boolean success =
                jdbcClient
                        .sql("SELECT success FROM flyway_schema_history WHERE version = '2'")
                        .query(Boolean.class)
                        .single();

        assertThat(success).isTrue();
    }

    @Test
    void 申込番号はAと8桁の連番になる() {
        assertThat(insertApplication("テスト 太郎", 450, "0000000000")).isEqualTo("A00000001");
        assertThat(insertApplication("見本 花子", 450, "0000000000")).isEqualTo("A00000002");
    }

    @Test
    void 片付けたあとは申込番号が1から始まる() {
        // 前のテストの順番によらず、各テストの前の片付けで連番が1に戻っていることを確かめる
        assertThat(insertApplication("テスト 太郎", 450, "0000000000")).isEqualTo("A00000001");
    }

    @Test
    void 氏名は4バイトの文字を含めて50文字まで入る() {
        String fiftyChars = "𠮷".repeat(10) + "テ".repeat(40);

        assertThat(insertApplication(fiftyChars, 450, "0000000000")).isEqualTo("A00000001");
    }

    @Test
    void 氏名が51文字のときは入らない() {
        assertThatThrownBy(() -> insertApplication("テ".repeat(51), 450, "0000000000"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {10000, -1})
    void 年収が0から9999の外のときは入らない(int annualIncome) {
        assertThatThrownBy(() -> insertApplication("テスト 太郎", annualIncome, "0000000000"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"03-0000-0000", "1000000000"})
    void 電話番号の形が違うときは入らない(String phoneNumber) {
        assertThatThrownBy(() -> insertApplication("テスト 太郎", 450, phoneNumber))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 状態が4つの値のどれでもないときは入らない() {
        assertThatThrownBy(() ->
                        jdbcClient
                                .sql(
                                        """
                                        INSERT INTO card_application
                                          (name_kanji, name_kana, birth_date, annual_income, employer_name,
                                           phone_number, status, applied_at, updated_at)
                                        VALUES ('テスト 太郎', 'テスト　タロウ', DATE '1990-04-01', 450,
                                                'テスト株式会社', '0000000000', 'UNKNOWN', :now, :now)
                                        """)
                                .param("now", NOW)
                                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 否決の履歴は理由がないと入らない() {
        String number = insertApplication("テスト 太郎", 450, "0000000000");

        assertThatThrownBy(() -> insertHistory(number, "UNDER_REVIEW", "REJECTED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 否決でない履歴は理由があると入らない() {
        String number = insertApplication("テスト 太郎", 450, "0000000000");

        assertThatThrownBy(() -> insertHistory(number, "APPLIED", "UNDER_REVIEW", "テスト用の否決理由"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 否決の履歴は理由があれば入る() {
        String number = insertApplication("テスト 太郎", 450, "0000000000");

        insertHistory(number, "UNDER_REVIEW", "REJECTED", "テスト用の否決理由");

        Integer count =
                jdbcClient
                        .sql("SELECT count(*) FROM card_application_status_history")
                        .query(Integer.class)
                        .single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void ない申込の履歴は入らない() {
        assertThatThrownBy(() -> insertHistory("A99999999", "APPLIED", "UNDER_REVIEW", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void versionを指定せずに入れると0になる() {
        String number = insertApplication("テスト 太郎", 450, "0000000000");

        Integer version =
                jdbcClient
                        .sql(
                                "SELECT version FROM card_application WHERE application_number = :number")
                        .param("number", number)
                        .query(Integer.class)
                        .single();
        assertThat(version).isZero();
    }

    private String insertApplication(String nameKanji, int annualIncome, String phoneNumber) {
        return jdbcClient
                .sql(
                        """
                        INSERT INTO card_application
                          (name_kanji, name_kana, birth_date, annual_income, employer_name,
                           phone_number, status, applied_at, updated_at)
                        VALUES (:nameKanji, 'テスト　タロウ', DATE '1990-04-01', :annualIncome,
                                'テスト株式会社', :phoneNumber, 'APPLIED', :now, :now)
                        RETURNING application_number
                        """)
                .param("nameKanji", nameKanji)
                .param("annualIncome", annualIncome)
                .param("phoneNumber", phoneNumber)
                .param("now", NOW)
                .query(String.class)
                .single();
    }

    private void insertHistory(String number, String from, String to, String reason) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO card_application_status_history
                          (application_number, changed_at, from_status, to_status, staff_name, rejection_reason)
                        VALUES (:number, :now, :from, :to, 'テスト 担当A', :reason)
                        """)
                .param("number", number)
                .param("now", NOW)
                .param("from", from)
                .param("to", to)
                .param("reason", reason)
                .update();
    }
}
