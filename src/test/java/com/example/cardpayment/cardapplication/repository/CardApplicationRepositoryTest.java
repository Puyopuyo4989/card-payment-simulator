package com.example.cardpayment.cardapplication.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.CardPaymentSimulatorApplication;
import com.example.cardpayment.TestcontainersConfiguration;
import com.example.cardpayment.cardapplication.CardApplicationTables;
import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.NewCardApplication;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 申込のリポジトリ（data-model.md 1）。値はすべて架空のもの。 */
@SpringBootTest(classes = CardPaymentSimulatorApplication.class)
@Import(TestcontainersConfiguration.class)
class CardApplicationRepositoryTest {

    /** マイクロ秒までの時刻（保存する時刻は BusinessTime.now でマイクロ秒に切り捨ててから渡す）。 */
    private static final Instant NOW = Instant.parse("2026-11-25T01:30:00.123456Z");

    @Autowired private JdbcClient jdbcClient;
    @Autowired private CardApplicationRepository repository;

    @BeforeEach
    void clearTables() {
        CardApplicationTables.clear(jdbcClient);
    }

    private static NewCardApplication taro() {
        return new NewCardApplication(
                "テスト 太郎",
                "テスト　タロウ",
                LocalDate.of(1990, 4, 1),
                new BigDecimal("450"),
                "テスト株式会社",
                "0000000000");
    }

    // 登録（US1）

    @Test
    void 登録すると申込番号を連番で返す() {
        assertThat(repository.insert(taro(), NOW)).isEqualTo("A00000001");
        assertThat(repository.insert(taro(), NOW)).isEqualTo("A00000002");
    }

    @Test
    void 登録した申込を申込番号で読める() {
        String number = repository.insert(taro(), NOW);

        CardApplication found = repository.findByNumber(number).orElseThrow();

        assertThat(found.applicationNumber()).isEqualTo("A00000001");
        assertThat(found.nameKanji()).isEqualTo("テスト 太郎");
        assertThat(found.nameKana()).isEqualTo("テスト　タロウ");
        assertThat(found.birthDate()).isEqualTo(LocalDate.of(1990, 4, 1));
        assertThat(found.annualIncome()).isEqualByComparingTo("450");
        assertThat(found.employerName()).isEqualTo("テスト株式会社");
        assertThat(found.phoneNumber()).isEqualTo("0000000000");
        assertThat(found.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(found.appliedAt()).isEqualTo(NOW);
        assertThat(found.updatedAt()).isEqualTo(NOW);
        assertThat(found.version()).isZero();
    }

    @Test
    void 読んだ年収は小数のないBigDecimal() {
        String number = repository.insert(taro(), NOW);

        assertThat(repository.findByNumber(number).orElseThrow().annualIncome().scale()).isZero();
    }

    @Test
    void ない申込番号は空を返す() {
        repository.insert(taro(), NOW);

        assertThat(repository.findByNumber("A99999999")).isEmpty();
        assertThat(repository.findByNumber("abc")).isEmpty();
    }

    @Test
    void 同じ内容を2回登録すると別の申込になる() {
        String first = repository.insert(taro(), NOW);
        String second = repository.insert(taro(), NOW);

        assertThat(first).isNotEqualTo(second);
        assertThat(repository.findByNumber(first)).isPresent();
        assertThat(repository.findByNumber(second)).isPresent();
    }
}
