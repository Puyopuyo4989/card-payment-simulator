package com.example.cardpayment.cardapplication.web;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import java.time.Instant;

/** 詳細の history[] の1行（contracts 3）。rejectionReason は否決の行だけにあり、ほかは null。 */
public record StatusHistoryResponse(
        Instant changedAt,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        String staffName,
        String rejectionReason) {

    /** 担当者名と否決の理由を出さない（research.md R11-1 の4）。 */
    @Override
    public String toString() {
        return "StatusHistoryResponse[" + fromStatus + " -> " + toStatus + "]";
    }
}
