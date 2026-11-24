package com.example.cardpayment.revolving.web;

/** API のリクエスト。画面に入力されたままの文字列で受け取り、解釈は RevolvingInputValidator で行う（contracts）。 */
public record RevolvingSimulationRequest(
        String balance, String monthlyPayment, String annualRate) {}
