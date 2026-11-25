package com.example.cardpayment.cardapplication.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.CardPaymentSimulatorApplication;
import com.example.cardpayment.TestcontainersConfiguration;
import com.example.cardpayment.cardapplication.CardApplicationTables;
import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.NewCardApplication;
import com.example.cardpayment.cardapplication.domain.StatusHistoryEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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

    // 状態の更新（US3）

    @Test
    void 版が合えば状態と更新日時を変えて版を1増やす() {
        String number = repository.insert(taro(), NOW);
        Instant later = NOW.plusSeconds(600);

        int rows = repository.updateStatus(number, ApplicationStatus.UNDER_REVIEW, later, 0);

        assertThat(rows).isEqualTo(1);
        CardApplication found = repository.findByNumber(number).orElseThrow();
        assertThat(found.status()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(found.updatedAt()).isEqualTo(later);
        assertThat(found.appliedAt()).isEqualTo(NOW);
        assertThat(found.version()).isEqualTo(1);
    }

    @Test
    void 古い版では何も変えず0を返す() {
        String number = repository.insert(taro(), NOW);
        repository.updateStatus(number, ApplicationStatus.UNDER_REVIEW, NOW.plusSeconds(60), 0);

        int rows =
                repository.updateStatus(
                        number, ApplicationStatus.APPROVED, NOW.plusSeconds(120), 0);

        assertThat(rows).isZero();
        CardApplication found = repository.findByNumber(number).orElseThrow();
        assertThat(found.status()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(found.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(found.version()).isEqualTo(1);
    }

    @Test
    void ない申込の更新は0を返す() {
        repository.insert(taro(), NOW);

        assertThat(repository.updateStatus("A99999999", ApplicationStatus.UNDER_REVIEW, NOW, 0))
                .isZero();
    }

    @Test
    void 足した履歴を読める() {
        String number = repository.insert(taro(), NOW);
        var review =
                new StatusHistoryEntry(
                        NOW.plusSeconds(60),
                        ApplicationStatus.APPLIED,
                        ApplicationStatus.UNDER_REVIEW,
                        "テスト 担当A",
                        null);
        var reject =
                new StatusHistoryEntry(
                        NOW.plusSeconds(120),
                        ApplicationStatus.UNDER_REVIEW,
                        ApplicationStatus.REJECTED,
                        "テスト 担当B",
                        "テスト用の否決理由");

        repository.insertHistory(number, review);
        repository.insertHistory(number, reject);

        assertThat(repository.findHistory(number)).containsExactly(review, reject);
    }

    // 一覧（US2）

    @Test
    void 一覧は申込日時の新しい順() {
        String oldest = repository.insert(taro(), NOW);
        String newest = repository.insert(taro(), NOW.plusSeconds(120));
        String middle = repository.insert(taro(), NOW.plusSeconds(60));

        assertThat(repository.findAll(Optional.empty()))
                .extracting(CardApplication::applicationNumber)
                .containsExactly(newest, middle, oldest);
    }

    @Test
    void 申込日時が同じときは申込番号の大きい順() {
        repository.insert(taro(), NOW);
        repository.insert(taro(), NOW);

        assertThat(repository.findAll(Optional.empty()))
                .extracting(CardApplication::applicationNumber)
                .containsExactly("A00000002", "A00000001");
    }

    @Test
    void 状態で絞り込むとその状態の申込だけを新しい順に返す() {
        String applied1 = repository.insert(taro(), NOW);
        String underReview = repository.insert(taro(), NOW.plusSeconds(60));
        String applied2 = repository.insert(taro(), NOW.plusSeconds(120));
        String approved = repository.insert(taro(), NOW.plusSeconds(180));
        setStatus(underReview, "UNDER_REVIEW");
        setStatus(approved, "APPROVED");

        assertThat(repository.findAll(Optional.of(ApplicationStatus.APPLIED)))
                .extracting(CardApplication::applicationNumber)
                .containsExactly(applied2, applied1);
        assertThat(repository.findAll(Optional.of(ApplicationStatus.UNDER_REVIEW)))
                .extracting(CardApplication::applicationNumber)
                .containsExactly(underReview);
        assertThat(repository.findAll(Optional.of(ApplicationStatus.REJECTED))).isEmpty();
    }

    @Test
    void 申込がないときの一覧は空() {
        assertThat(repository.findAll(Optional.empty())).isEmpty();
    }

    // 変更履歴（US2）

    @Test
    void 履歴は変更日時の古い順で同じ日時なら行の順() {
        String number = repository.insert(taro(), NOW);
        // 古い行をあとから入れても、変更日時の古い順に返す
        insertHistory(number, NOW.plusSeconds(120), "UNDER_REVIEW", "REJECTED", "テスト用の否決理由");
        insertHistory(number, NOW.plusSeconds(60), "APPLIED", "UNDER_REVIEW", null);

        List<StatusHistoryEntry> history = repository.findHistory(number);

        assertThat(history)
                .extracting(StatusHistoryEntry::toStatus)
                .containsExactly(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.REJECTED);
        StatusHistoryEntry first = history.get(0);
        assertThat(first.changedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(first.fromStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(first.staffName()).isEqualTo("テスト 担当A");
        assertThat(first.rejectionReason()).isNull();
        assertThat(history.get(1).rejectionReason()).isEqualTo("テスト用の否決理由");
    }

    @Test
    void 変更日時が同じ履歴は入れた順() {
        String number = repository.insert(taro(), NOW);
        insertHistory(number, NOW, "APPLIED", "UNDER_REVIEW", null);
        insertHistory(number, NOW, "UNDER_REVIEW", "APPROVED", null);

        assertThat(repository.findHistory(number))
                .extracting(StatusHistoryEntry::toStatus)
                .containsExactly(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.APPROVED);
    }

    @Test
    void ほかの申込の履歴は返さない() {
        String mine = repository.insert(taro(), NOW);
        String other = repository.insert(taro(), NOW);
        insertHistory(other, NOW, "APPLIED", "UNDER_REVIEW", null);

        assertThat(repository.findHistory(mine)).isEmpty();
    }

    @Test
    void 同じ内容を2回登録すると別の申込になる() {
        String first = repository.insert(taro(), NOW);
        String second = repository.insert(taro(), NOW);

        assertThat(first).isNotEqualTo(second);
        assertThat(repository.findByNumber(first)).isPresent();
        assertThat(repository.findByNumber(second)).isPresent();
    }

    /** 状態の更新の処理は US3 で作るので、ここでは SQL で直接変える。 */
    private void setStatus(String number, String status) {
        jdbcClient
                .sql(
                        "UPDATE card_application SET status = :status WHERE application_number = :number")
                .param("status", status)
                .param("number", number)
                .update();
    }

    /** 履歴を足す処理は US3 で作るので、ここでは SQL で直接入れる。 */
    private void insertHistory(
            String number, Instant changedAt, String from, String to, String reason) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO card_application_status_history
                          (application_number, changed_at, from_status, to_status, staff_name, rejection_reason)
                        VALUES (:number, :changedAt, :from, :to, 'テスト 担当A', :reason)
                        """)
                .param("number", number)
                .param("changedAt", OffsetDateTime.ofInstant(changedAt, ZoneOffset.UTC))
                .param("from", from)
                .param("to", to)
                .param("reason", reason)
                .update();
    }
}
