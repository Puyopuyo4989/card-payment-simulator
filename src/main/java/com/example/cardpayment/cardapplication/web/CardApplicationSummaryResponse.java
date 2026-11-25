package com.example.cardpayment.cardapplication.web;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import java.time.Instant;

/** 一覧の1行（contracts 2）。一覧に要らない個人情報は持たない（research.md R8）。 */
public record CardApplicationSummaryResponse(
        String applicationNumber, String nameKanji, ApplicationStatus status, Instant appliedAt) {

    static CardApplicationSummaryResponse from(CardApplication application) {
        return new CardApplicationSummaryResponse(
                application.applicationNumber(),
                application.nameKanji(),
                application.status(),
                application.appliedAt());
    }

    /** 氏名を出さない（research.md R11-1 の4）。 */
    @Override
    public String toString() {
        return "CardApplicationSummaryResponse[applicationNumber="
                + applicationNumber
                + ", status="
                + status
                + "]";
    }
}
