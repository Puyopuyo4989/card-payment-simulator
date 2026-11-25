package com.example.cardpayment.cardapplication.web;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.StatusHistoryEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 申込の詳細（contracts 3）。登録・状態の変更の成功のときも、この形で返す。 年収は小数のない BigDecimal のまま返し、JSON では整数になる。日時は ISO 8601 の
 * UTC、生年月日は YYYY-MM-DD。
 */
public record CardApplicationDetailResponse(
        String applicationNumber,
        String nameKanji,
        String nameKana,
        LocalDate birthDate,
        BigDecimal annualIncome,
        String employerName,
        String phoneNumber,
        ApplicationStatus status,
        Instant appliedAt,
        Instant updatedAt,
        int version,
        List<StatusHistoryResponse> history) {

    /** 登録したばかりの申込（履歴はまだない）。 */
    static CardApplicationDetailResponse from(CardApplication application) {
        return from(application, List.of());
    }

    /** 申込と変更履歴（古い順）。 */
    static CardApplicationDetailResponse from(
            CardApplication application, List<StatusHistoryEntry> history) {
        return new CardApplicationDetailResponse(
                application.applicationNumber(),
                application.nameKanji(),
                application.nameKana(),
                application.birthDate(),
                application.annualIncome(),
                application.employerName(),
                application.phoneNumber(),
                application.status(),
                application.appliedAt(),
                application.updatedAt(),
                application.version(),
                history.stream()
                        .map(entry ->
                                new StatusHistoryResponse(
                                        entry.changedAt(),
                                        entry.fromStatus(),
                                        entry.toStatus(),
                                        entry.staffName(),
                                        entry.rejectionReason()))
                        .toList());
    }

    /** 個人情報と、履歴の担当者名・否決の理由を出さない（research.md R11-1 の4）。 */
    @Override
    public String toString() {
        return "CardApplicationDetailResponse[applicationNumber="
                + applicationNumber
                + ", status="
                + status
                + ", version="
                + version
                + "]";
    }
}
