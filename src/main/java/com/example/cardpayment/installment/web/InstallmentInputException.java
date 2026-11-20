package com.example.cardpayment.installment.web;

import java.util.List;

/** 入力に誤りがあるときの例外。誤っている項目ごとのエラーを、amount → paymentCount → annualRate の順で持つ（contracts の 400）。 */
public class InstallmentInputException extends RuntimeException {

    /** 1つの項目のエラー。message は画面にそのまま表示する。 */
    public record FieldError(String field, String message) {}

    private final List<FieldError> errors;

    public InstallmentInputException(List<FieldError> errors) {
        super("入力に誤りがあります");
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }
}
