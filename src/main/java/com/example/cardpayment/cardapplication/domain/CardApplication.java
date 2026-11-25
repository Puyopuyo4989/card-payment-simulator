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
