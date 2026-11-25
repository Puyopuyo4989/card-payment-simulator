package com.example.cardpayment.cardapplication.web;

import java.util.List;

/**
 * 申込の一覧（contracts 2）。0件のときは空のリスト。 page・totalPages・totalItems は【Should】のページ分けで足した項目（contracts
 * 6、research.md R13）。
 */
public record CardApplicationListResponse(
        List<CardApplicationSummaryResponse> items, int page, int totalPages, long totalItems) {}
