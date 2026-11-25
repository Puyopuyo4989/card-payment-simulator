package com.example.cardpayment.cardapplication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.StatusHistoryEntry;
import com.example.cardpayment.cardapplication.repository.CardApplicationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 状態の変更の処理の順番（data-model.md 4 の 3〜6）。DB では起こしにくい「読んだあと、保存の前にほかの人が変えた」場合を、
 * リポジトリを手書きで差し替えて確かめる。Spring も DB も使わない。値はすべて架空のもの。
 */
class CardApplicationServiceTest {

    private static final Instant APPLIED_AT = Instant.parse("2026-11-25T01:30:00Z");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-25T02:00:00Z"), ZoneOffset.UTC);

    private final StubRepository repository = new StubRepository();
    private final CardApplicationService service = new CardApplicationService(repository, CLOCK);

    @Test
    void 保存の時点でほかの人が変えていたらぶつかったことにし履歴を足さない() {
        repository.stored = Optional.of(underReview(1));
        repository.updatedRows = 0;

        assertThatThrownBy(() ->
                        service.changeStatus(
                                "A00000001", ApplicationStatus.APPROVED, "テスト 担当A", null, 1))
                .isInstanceOf(StatusConflictException.class);
        assertThat(repository.updateCalls).isEqualTo(1);
        assertThat(repository.insertedHistory).isEmpty();
    }

    @Test
    void 読んだ版と送られた版が違うときは保存しない() {
        repository.stored = Optional.of(underReview(1));

        assertThatThrownBy(() ->
                        service.changeStatus(
                                "A00000001", ApplicationStatus.APPROVED, "テスト 担当A", null, 0))
                .isInstanceOf(StatusConflictException.class);
        assertThat(repository.updateCalls).isZero();
        assertThat(repository.insertedHistory).isEmpty();
    }

    @Test
    void ない申込は見つからない() {
        repository.stored = Optional.empty();

        assertThatThrownBy(() ->
                        service.changeStatus(
                                "A99999999", ApplicationStatus.APPROVED, "テスト 担当A", null, 0))
                .isInstanceOf(ApplicationNotFoundException.class);
        assertThat(repository.updateCalls).isZero();
    }

    private static CardApplication underReview(int version) {
        return new CardApplication(
                "A00000001",
                "テスト 太郎",
                "テスト　タロウ",
                LocalDate.of(1990, 4, 1),
                new BigDecimal("450"),
                "テスト株式会社",
                "0000000000",
                ApplicationStatus.UNDER_REVIEW,
                APPLIED_AT,
                APPLIED_AT,
                version);
    }

    /** 状態の変更で使うメソッドだけを差し替える。DB には触らない。 */
    private static final class StubRepository extends CardApplicationRepository {

        Optional<CardApplication> stored = Optional.empty();
        int updatedRows = 1;
        int updateCalls;
        final List<StatusHistoryEntry> insertedHistory = new ArrayList<>();

        StubRepository() {
            super(null);
        }

        @Override
        public Optional<CardApplication> findByNumber(String applicationNumber) {
            return stored;
        }

        @Override
        public int updateStatus(
                String applicationNumber, ApplicationStatus to, Instant now, int expectedVersion) {
            updateCalls++;
            return updatedRows;
        }

        @Override
        public void insertHistory(String applicationNumber, StatusHistoryEntry entry) {
            insertedHistory.add(entry);
        }

        @Override
        public List<StatusHistoryEntry> findHistory(String applicationNumber) {
            return List.copyOf(insertedHistory);
        }
    }
}
