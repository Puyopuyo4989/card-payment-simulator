package com.example.cardpayment.cardapplication.web;

/** 申込の登録のリクエスト（contracts 1）。6項目とも、画面に入力されたままの文字列で受ける。 JSON の数値が来ても、文字列として受け付ける。 */
public record CardApplicationRequest(
        String nameKanji,
        String nameKana,
        String birthDate,
        String annualIncome,
        String employerName,
        String phoneNumber) {

    /** 個人情報を出さない（research.md R11-1 の4）。 */
    @Override
    public String toString() {
        return "CardApplicationRequest[個人情報は表示しない]";
    }
}
