package com.example.cardpayment.cardapplication.domain;

/** 状態を変えた結果：変更後の申込と、履歴に足す1行。 */
public record StatusChange(CardApplication application, StatusHistoryEntry history) {}
