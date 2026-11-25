package com.example.cardpayment.cardapplication.service;

import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.StatusHistoryEntry;
import java.util.List;

/** 申込と、その変更履歴（古い順）。詳細の画面に出すもの（spec FR-010）。 */
public record CardApplicationDetail(
        CardApplication application, List<StatusHistoryEntry> history) {}
