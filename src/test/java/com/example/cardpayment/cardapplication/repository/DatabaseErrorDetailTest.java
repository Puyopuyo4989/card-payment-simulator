package com.example.cardpayment.cardapplication.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.cardpayment.CardPaymentSimulatorApplication;
import com.example.cardpayment.TestcontainersConfiguration;
import com.example.cardpayment.cardapplication.CardApplicationTables;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 制約の違反のとき、PostgreSQL は「Failing row contains (…)」と行の中身を返す。 それが例外のメッセージに入ると、予期しないエラーのときに
 * 個人情報がログに出るので、入らないことを確かめる（research.md R11-1 の5）。
 */
@SpringBootTest(classes = CardPaymentSimulatorApplication.class)
@Import(TestcontainersConfiguration.class)
class DatabaseErrorDetailTest {

    private static final String NAME = "テスト 漏えい確認";

    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void clearTables() {
        CardApplicationTables.clear(jdbcClient);
    }

    // 年収 -1 は CHECK の違反（Failing row を返す）。10000 は numeric(4, 0) の桁あふれで、CHECK より前に止まり、行の中身を返さない
    @Test
    void 制約の違反の例外のメッセージに行の中身が入らない() {
        Throwable thrown =
                catchThrowable(() ->
                        jdbcClient
                                .sql(
                                        """
                                        INSERT INTO card_application
                                          (name_kanji, name_kana, birth_date, annual_income, employer_name,
                                           phone_number, status, applied_at, updated_at)
                                        VALUES (:name, 'テスト　タロウ', DATE '1990-04-01', -1,
                                                'テスト株式会社', '0000000000', 'APPLIED', :now, :now)
                                        """)
                                .param("name", NAME)
                                .param(
                                        "now",
                                        OffsetDateTime.of(
                                                2026, 11, 25, 1, 30, 0, 0, ZoneOffset.UTC))
                                .update());

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            assertThat(t.getMessage()).as(t.getClass().getName()).doesNotContain(NAME);
        }
    }
}
