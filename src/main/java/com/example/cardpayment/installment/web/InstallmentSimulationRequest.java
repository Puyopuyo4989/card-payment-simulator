package com.example.cardpayment.installment.web;

/** API のリクエスト。画面に入力されたままの文字列で受け取り、解釈は InstallmentInputValidator で行う（contracts）。 */
public record InstallmentSimulationRequest(String amount, String paymentCount, String annualRate) {}
