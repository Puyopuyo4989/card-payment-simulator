package com.example.cardpayment.cardapplication.service;

/** ほかの担当者が先に状態を変えた（contracts 4 の 409）。メッセージは決まった文と申込番号だけ（research.md R11-1 の2）。 */
public class StatusConflictException extends RuntimeException {

    public StatusConflictException(String applicationNumber) {
        super("ほかの担当者が先に状態を変えました: " + applicationNumber);
    }
}
