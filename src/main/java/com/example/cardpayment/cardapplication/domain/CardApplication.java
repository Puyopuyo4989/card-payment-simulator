package com.example.cardpayment.cardapplication.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 保存された申込（data-model.md 1・5）。登録したあとは、状態・更新日時・version だけが変わる（spec FR-006）。
 *
 * @param applicationNumber 申込番号（例：A00000001）
 * @param nameKanji 氏名（漢字）
 * @param nameKana 氏名（カナ）
 * @param birthDate 生年月日
 * @param annualIncome 年収（万円、小数なし）
 * @param employerName 勤務先名
 * @param phoneNumber 電話番号
 * @param status 今の状態
 * @param appliedAt 申込日時
 * @param updatedAt 更新日時
 * @param version 楽観ロックの版（状態を変えるたびに1増える）
 */
public record CardApplication(
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
        int version) {

    /**
     * 状態を変える（data-model.md 3、spec FR-015）。この申込は変えず、変更後の申込と履歴の1行を返す。 version を増やすのは DB（UPDATE …
     * version = version + 1）なので、ここでは変えない。
     *
     * @param rejectionReason 否決の理由。否決のときは必須（入力チェックで先に確かめる）。ほかの状態のときは捨てる
     * @throws StatusTransitionException 許されない変更
     */
    public StatusChange changeStatus(
            ApplicationStatus to, String staffName, String rejectionReason, Instant now) {
        if (status.isFinal()) {
            throw new StatusTransitionException(
                    StatusTransitionException.Reason.ALREADY_DECIDED, status, to);
        }
        if (status == ApplicationStatus.APPLIED && to.isFinal()) {
            throw new StatusTransitionException(
                    StatusTransitionException.Reason.REVIEW_REQUIRED_FIRST, status, to);
        }
        if (!status.canChangeTo(to)) {
            throw new StatusTransitionException(
                    StatusTransitionException.Reason.NOT_ALLOWED, status, to);
        }
        boolean rejecting = to == ApplicationStatus.REJECTED;
        if (rejecting && rejectionReason == null) {
            throw new IllegalArgumentException("否決には理由が要ります");
        }
        CardApplication changed =
                new CardApplication(
                        applicationNumber,
                        nameKanji,
                        nameKana,
                        birthDate,
                        annualIncome,
                        employerName,
                        phoneNumber,
                        to,
                        appliedAt,
                        now,
                        version);
        var history =
                new StatusHistoryEntry(
                        now, status, to, staffName, rejecting ? rejectionReason : null);
        return new StatusChange(changed, history);
    }

    /** 個人情報を出さず、申込番号と状態だけを出す（research.md R11-1 の4）。 */
    @Override
    public String toString() {
        return "CardApplication[applicationNumber="
                + applicationNumber
                + ", status="
                + status
                + "]";
    }
}
