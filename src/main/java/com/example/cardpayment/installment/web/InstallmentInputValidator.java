package com.example.cardpayment.installment.web;

import com.example.cardpayment.installment.domain.InstallmentCondition;
import com.example.cardpayment.installment.web.InstallmentInputException.FieldError;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 入力の文字列を確かめて InstallmentCondition に変える（data-model.md 1）。項目ごとに「必須 → 形式 → 範囲」の順で確かめ、
 * 最初に当てはまった1つのエラーを返す。誤っている項目があれば、すべてを持つ InstallmentInputException を投げる。
 */
public class InstallmentInputValidator {

    private static final Pattern INTEGER = Pattern.compile("[0-9]+");
    private static final Pattern RATE = Pattern.compile("[0-9]+(\\.[0-9])?");

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("1000");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    /** 先頭の0（"012"）も利用金額と同じく受け付けるよう、数として照合する。 */
    private static final Set<BigDecimal> PAYMENT_COUNTS =
            Set.of(3, 5, 6, 10, 12, 15, 18, 20, 24, 30, 36).stream()
                    .map(BigDecimal::valueOf)
                    .collect(Collectors.toUnmodifiableSet());

    private static final BigDecimal MAX_RATE = new BigDecimal("15.0");

    public InstallmentCondition validate(InstallmentSimulationRequest request) {
        String amount = strip(request.amount());
        String paymentCount = strip(request.paymentCount());
        String annualRate = strip(request.annualRate());

        List<FieldError> errors = new ArrayList<>();
        checkAmount(amount, errors);
        checkPaymentCount(paymentCount, errors);
        checkAnnualRate(annualRate, errors);
        if (!errors.isEmpty()) {
            throw new InstallmentInputException(errors);
        }

        return new InstallmentCondition(
                new BigDecimal(amount), Integer.parseInt(paymentCount), new BigDecimal(annualRate));
    }

    private static void checkAmount(String amount, List<FieldError> errors) {
        if (amount.isEmpty()) {
            errors.add(new FieldError("amount", "利用金額を入力してください"));
        } else if (!INTEGER.matcher(amount).matches()) {
            errors.add(new FieldError("amount", "利用金額は半角数字（整数）で入力してください"));
        } else {
            BigDecimal value = new BigDecimal(amount);
            if (value.compareTo(MIN_AMOUNT) < 0 || value.compareTo(MAX_AMOUNT) > 0) {
                errors.add(new FieldError("amount", "利用金額は1,000円以上10,000,000円以下で入力してください"));
            }
        }
    }

    private static void checkPaymentCount(String paymentCount, List<FieldError> errors) {
        if (paymentCount.isEmpty()) {
            errors.add(new FieldError("paymentCount", "支払回数を選んでください"));
        } else if (!INTEGER.matcher(paymentCount).matches()
                || !PAYMENT_COUNTS.contains(new BigDecimal(paymentCount))) {
            // 形式の誤りと一覧にない回数は、1つのメッセージにまとめる（data-model.md 1）
            errors.add(
                    new FieldError("paymentCount", "支払回数は3・5・6・10・12・15・18・20・24・30・36回から選んでください"));
        }
    }

    private static void checkAnnualRate(String annualRate, List<FieldError> errors) {
        if (annualRate.isEmpty()) {
            errors.add(new FieldError("annualRate", "実質年率を入力してください"));
        } else if (!RATE.matcher(annualRate).matches()) {
            errors.add(new FieldError("annualRate", "実質年率は半角数字で、小数第1位までで入力してください（例：12.5）"));
        } else if (new BigDecimal(annualRate).compareTo(MAX_RATE) > 0) {
            // 形式のチェックで負の数は除かれているので、下限（0.0）は確かめなくてよい
            errors.add(new FieldError("annualRate", "実質年率は0.0%以上15.0%以下で入力してください"));
        }
    }

    /** 未指定（null）は空文字として扱い、前後の空白を取り除く。 */
    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
