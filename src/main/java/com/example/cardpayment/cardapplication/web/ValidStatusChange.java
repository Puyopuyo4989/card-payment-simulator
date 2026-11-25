package com.example.cardpayment.cardapplication.web;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;

/**
 * 入力チェックを通った状態の変更。
 *
 * @param rejectionReason 否決のときだけ入る。ほかの状態のときは null（保存しない）
 */
public record ValidStatusChange(
        ApplicationStatus toStatus, String staffName, String rejectionReason, int version) {

    /** 担当者名と否決の理由を出さない（research.md R11-1 の4）。 */
    @Override
    public String toString() {
        return "ValidStatusChange[toStatus=" + toStatus + ", version=" + version + "]";
    }
}
