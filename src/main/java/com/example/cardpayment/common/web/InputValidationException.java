package com.example.cardpayment.common.web;

import java.util.List;

/** 入力に誤りがあるときの例外。誤っている項目ごとのエラーを、各機能の contracts の項目の順で持つ（contracts の 400）。 */
public class InputValidationException extends RuntimeException {

    /** 1つの項目のエラー。message は画面にそのまま表示する。 */
    public record FieldError(String field, String message) {}

    private final List<FieldError> errors;

    public InputValidationException(List<FieldError> errors) {
        super("入力に誤りがあります");
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }
}
