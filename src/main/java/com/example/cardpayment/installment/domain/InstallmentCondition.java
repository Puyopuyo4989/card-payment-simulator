package com.example.cardpayment.installment.domain;

import java.math.BigDecimal;

/**
 * シミュレーション条件。入力チェックを通ったあとの値で、ここでは値をチェックしない（data-model.md 1）。
 *
 * @param amount 利用金額（円、小数なし）
 * @param paymentCount 支払回数
 * @param annualRate 実質年率（%、小数第1位まで）
 */
public record InstallmentCondition(BigDecimal amount, int paymentCount, BigDecimal annualRate) {}
