package com.example.cardpayment.cardapplication.repository;

import com.example.cardpayment.cardapplication.domain.CardApplication;
import java.util.List;

/**
 * 【Should】検索の1ページ分（research.md R13）。items は最大 {@link CardApplicationRepository#PAGE_SIZE} 件。
 * 該当が0件のときは totalPages も0。
 */
public record CardApplicationSearchResult(List<CardApplication> items, int page, long totalItems) {

    public int totalPages() {
        return (int)
                ((totalItems + CardApplicationRepository.PAGE_SIZE - 1)
                        / CardApplicationRepository.PAGE_SIZE);
    }
}
