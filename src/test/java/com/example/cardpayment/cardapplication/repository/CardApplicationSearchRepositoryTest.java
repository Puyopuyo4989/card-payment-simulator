package com.example.cardpayment.cardapplication.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.CardPaymentSimulatorApplication;
import com.example.cardpayment.TestcontainersConfiguration;
import com.example.cardpayment.cardapplication.CardApplicationTables;
import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.NewCardApplication;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 【Should】キーワード検索とページ分け（research.md R13、US4）。値はすべて架空のもの。 */
@SpringBootTest(classes = CardPaymentSimulatorApplication.class)
@Import(TestcontainersConfiguration.class)
class CardApplicationSearchRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-25T01:30:00Z");

    @Autowired private JdbcClient jdbcClient;
    @Autowired private CardApplicationRepository repository;

    @BeforeEach
    void clearTables() {
        CardApplicationTables.clear(jdbcClient);
    }

    private static NewCardApplication application(String nameKanji, String nameKana) {
        return new NewCardApplication(
                nameKanji,
                nameKana,
                LocalDate.of(1990, 4, 1),
                new BigDecimal("450"),
                "テスト株式会社",
                "0000000000");
    }

    /** A00000001：カナにタロウ、A00000002：どこにもタロウなし、A00000003：漢字にタロウ。 */
    private void insertThree() {
        repository.insert(application("テスト 太郎", "テスト　タロウ"), NOW);
        repository.insert(application("見本 花子", "ミホン　ハナコ"), NOW);
        repository.insert(application("テスト タロウ", "ミホン　ハナコ"), NOW);
    }

    private static List<String> numbers(CardApplicationSearchResult result) {
        return result.items().stream().map(CardApplication::applicationNumber).toList();
    }

    @Test
    void キーワードは氏名の漢字とカナのどちらかに含むものだけを返す() {
        insertThree();

        var result = repository.search(Optional.empty(), Optional.of("タロウ"), 1);

        assertThat(numbers(result)).containsExactly("A00000003", "A00000001");
        assertThat(result.totalItems()).isEqualTo(2);
    }

    @Test
    void 申込番号の一部でも探せる() {
        insertThree();

        var result = repository.search(Optional.empty(), Optional.of("00002"), 1);

        assertThat(numbers(result)).containsExactly("A00000002");
    }

    @Test
    void キーワードと状態の両方に合うものだけを返す() {
        insertThree();
        repository.updateStatus("A00000001", ApplicationStatus.UNDER_REVIEW, NOW, 0);

        var result =
                repository.search(
                        Optional.of(ApplicationStatus.UNDER_REVIEW), Optional.of("タロウ"), 1);

        assertThat(numbers(result)).containsExactly("A00000001");
        assertThat(result.totalItems()).isEqualTo(1);
    }

    @Test
    void 二十件ずつ新しい順にページを分ける() {
        for (int i = 0; i < 25; i++) {
            repository.insert(application("テスト 太郎", "テスト　タロウ"), NOW.plus(Duration.ofMinutes(i)));
        }

        var first = repository.search(Optional.empty(), Optional.empty(), 1);
        var second = repository.search(Optional.empty(), Optional.empty(), 2);
        var third = repository.search(Optional.empty(), Optional.empty(), 3);

        assertThat(first.items()).hasSize(20);
        assertThat(numbers(first).getFirst()).isEqualTo("A00000025");
        assertThat(numbers(first).getLast()).isEqualTo("A00000006");
        assertThat(numbers(second))
                .containsExactly("A00000005", "A00000004", "A00000003", "A00000002", "A00000001");
        assertThat(first.page()).isEqualTo(1);
        assertThat(second.page()).isEqualTo(2);
        assertThat(first.totalItems()).isEqualTo(25);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(third.items()).isEmpty();
        assertThat(third.page()).isEqualTo(3);
        assertThat(third.totalItems()).isEqualTo(25);
        assertThat(third.totalPages()).isEqualTo(2);
    }

    @Test
    void 二十件ちょうどなら1ページ() {
        for (int i = 0; i < 20; i++) {
            repository.insert(application("テスト 太郎", "テスト　タロウ"), NOW);
        }

        var result = repository.search(Optional.empty(), Optional.empty(), 1);

        assertThat(result.items()).hasSize(20);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void 該当なしのときは0ページ() {
        var result = repository.search(Optional.empty(), Optional.empty(), 1);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void キーワードの記号は文字そのものとして探す() {
        insertThree();
        repository.insert(application("テスト%太郎", "テスト　タロウ"), NOW);

        assertThat(numbers(repository.search(Optional.empty(), Optional.of("%"), 1)))
                .containsExactly("A00000004");
        assertThat(repository.search(Optional.empty(), Optional.of("_"), 1).items()).isEmpty();
        assertThat(repository.search(Optional.empty(), Optional.of("\\"), 1).items()).isEmpty();
    }
}
