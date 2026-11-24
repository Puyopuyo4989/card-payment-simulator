package com.example.cardpayment.revolving.domain;

import java.math.BigDecimal;

/**
 * シミュレーション条件。入力チェックを通ったあとの値で、ここでは値をチェックしない（data-model.md 1）。
 *
 * @param balance 利用残高（円、小数なし）
 * @param monthlyPayment 毎月の支払額（円、小数なし）
 * @param annualRate 実質年率（%、小数第1位まで）
 */
public record RevolvingCondition(
        BigDecimal balance, BigDecimal monthlyPayment, BigDecimal annualRate) {}
