package com.example.cardpayment.cardapplication.web;

import java.util.List;

/** 申込の一覧（contracts 2）。0件のときは空のリスト。 */
public record CardApplicationListResponse(List<CardApplicationSummaryResponse> items) {}
