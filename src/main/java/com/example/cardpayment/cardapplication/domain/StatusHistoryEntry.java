package com.example.cardpayment.cardapplication.domain;

import java.time.Instant;

/**
 * 状態の変更履歴の1行（data-model.md 1.2・5）。
 *
 * @param changedAt 変更日時
 * @param fromStatus 変更前の状態
 * @param toStatus 変更後の状態
 * @param staffName 担当者名（自己申告。ADR 0010）
 * @param rejectionReason 否決の理由。否決の行だけにあり、ほかの行は null
 */
public record StatusHistoryEntry(
        Instant changedAt,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        String staffName,
        String rejectionReason) {

    /** 担当者名と否決の理由を出さず、状態の変化だけを出す（research.md R11-1 の4）。 */
    @Override
    public String toString() {
        return "StatusHistoryEntry[" + fromStatus + " -> " + toStatus + "]";
    }
}
