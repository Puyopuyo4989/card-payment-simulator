package com.example.cardpayment.cardapplication.service;

/** 申込が見つからない（contracts 3 の 404）。メッセージは決まった文と申込番号だけ（research.md R11-1 の2）。 */
public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(String applicationNumber) {
        super("申込が見つかりません: " + applicationNumber);
    }
}
