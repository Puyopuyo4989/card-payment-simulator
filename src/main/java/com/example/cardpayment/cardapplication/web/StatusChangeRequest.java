package com.example.cardpayment.cardapplication.web;

/** 状態の変更のリクエスト（contracts 4）。4項目とも文字列で受ける。JSON の数値が来ても、文字列として受け付ける。 */
public record StatusChangeRequest(
        String toStatus, String staffName, String rejectionReason, String version) {

    /** 担当者名と否決の理由を出さない（research.md R11-1 の4）。 */
    @Override
    public String toString() {
        return "StatusChangeRequest[担当者名と理由は表示しない]";
    }
}
