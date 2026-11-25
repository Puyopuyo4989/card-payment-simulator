package com.example.cardpayment.cardapplication.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入力チェックを通った、登録する申込の内容（data-model.md 2）。ここでは値をチェックしない。
 *
 * @param nameKanji 氏名（漢字）
 * @param nameKana 氏名（カナ）
 * @param birthDate 生年月日
 * @param annualIncome 年収（万円、小数なし）。金額なので BigDecimal（constitution 原則III）
 * @param employerName 勤務先名
 * @param phoneNumber 電話番号（ハイフンなし）
 */
public record NewCardApplication(
        String nameKanji,
        String nameKana,
        LocalDate birthDate,
        BigDecimal annualIncome,
        String employerName,
        String phoneNumber) {

    /** 個人情報を出さない（research.md R11-1 の4）。 */
    @Override
    public String toString() {
        return "NewCardApplication[個人情報は表示しない]";
    }
}
