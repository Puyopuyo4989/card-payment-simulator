package com.example.cardpayment.installment.web;

import static com.example.cardpayment.common.web.InputRules.isInteger;
import static com.example.cardpayment.common.web.InputRules.isTooLong;
import static com.example.cardpayment.common.web.InputRules.strip;

import com.example.cardpayment.common.web.InputRules;
import com.example.cardpayment.common.web.InputValidationException;
import com.example.cardpayment.common.web.InputValidationException.FieldError;
import com.example.cardpayment.installment.domain.InstallmentCondition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 入力の文字列を確かめて InstallmentCondition に変える（data-model.md 1）。項目ごとに「必須 → 形式 → 範囲」の順で確かめ、
 * 最初に当てはまった1つのエラーを返す。誤っている項目があれば、すべてを持つ InputValidationException を投げる。 長さの上限と実質年率のチェックは、リボ払いと共通の
 * InputRules で行う。
 */
public class InstallmentInputValidator {

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("1000");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    /** 先頭の0（"012"）も利用金額と同じく受け付けるよう、数として照合する。 */
    private static final Set<BigDecimal> PAYMENT_COUNTS =
            Set.of(3, 5, 6, 10, 12, 15, 18, 20, 24, 30, 36).stream()
                    .map(BigDecimal::valueOf)
                    .collect(Collectors.toUnmodifiableSet());

    public InstallmentCondition validate(InstallmentSimulationRequest request) {
        String amount = strip(request.amount());
        String paymentCount = strip(request.paymentCount());
        String annualRate = strip(request.annualRate());

        List<FieldError> errors = new ArrayList<>();
        checkAmount(amount, errors);
        checkPaymentCount(paymentCount, errors);
        InputRules.checkAnnualRate(annualRate, errors);
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        return new InstallmentCondition(
                new BigDecimal(amount), Integer.parseInt(paymentCount), new BigDecimal(annualRate));
    }

    private static void checkAmount(String amount, List<FieldError> errors) {
        if (amount.isEmpty()) {
            errors.add(new FieldError("amount", "利用金額を入力してください"));
        } else if (!isInteger(amount)) {
            errors.add(new FieldError("amount", "利用金額は半角数字（整数）で入力してください"));
        } else if (isTooLong(amount)
                || new BigDecimal(amount).compareTo(MIN_AMOUNT) < 0
                || new BigDecimal(amount).compareTo(MAX_AMOUNT) > 0) {
            errors.add(new FieldError("amount", "利用金額は1,000円以上10,000,000円以下で入力してください"));
        }
    }

    private static void checkPaymentCount(String paymentCount, List<FieldError> errors) {
        if (paymentCount.isEmpty()) {
            errors.add(new FieldError("paymentCount", "支払回数を選んでください"));
        } else if (!isInteger(paymentCount)
                || isTooLong(paymentCount)
                || !PAYMENT_COUNTS.contains(new BigDecimal(paymentCount))) {
            // 形式の誤りと一覧にない回数は、1つのメッセージにまとめる（data-model.md 1）
            errors.add(
                    new FieldError("paymentCount", "支払回数は3・5・6・10・12・15・18・20・24・30・36回から選んでください"));
        }
    }
}
