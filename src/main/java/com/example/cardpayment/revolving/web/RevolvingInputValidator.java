package com.example.cardpayment.revolving.web;

import static com.example.cardpayment.common.web.InputRules.isInteger;
import static com.example.cardpayment.common.web.InputRules.isTooLong;
import static com.example.cardpayment.common.web.InputRules.strip;

import com.example.cardpayment.common.web.InputRules;
import com.example.cardpayment.common.web.InputValidationException;
import com.example.cardpayment.common.web.InputValidationException.FieldError;
import com.example.cardpayment.revolving.domain.RevolvingCondition;
import com.example.cardpayment.revolving.domain.RevolvingNotPayableException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 入力の文字列を確かめて RevolvingCondition に変える（data-model.md 1）。項目ごとに「必須 → 形式 → 範囲」の順で確かめ、
 * 毎月の支払額は、そのあとに利用残高以下かどうかを確かめる。最初に当てはまった1つのエラーを返し、誤っている項目があれば、 すべてを持つ InputValidationException
 * を投げる。1回目の手数料と360回の条件は計算が要るので、計算が投げた理由を toInputError で変える。
 */
public class RevolvingInputValidator {

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("1000");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    private static final BigDecimal PAYMENT_UNIT = new BigDecimal("1000");

    public RevolvingCondition validate(RevolvingSimulationRequest request) {
        String balance = strip(request.balance());
        String monthlyPayment = strip(request.monthlyPayment());
        String annualRate = strip(request.annualRate());

        List<FieldError> errors = new ArrayList<>();
        boolean balanceIsValid = checkBalance(balance, errors);
        checkMonthlyPayment(monthlyPayment, balanceIsValid ? balance : null, errors);
        InputRules.checkAnnualRate(annualRate, errors);
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        return new RevolvingCondition(
                new BigDecimal(balance),
                new BigDecimal(monthlyPayment),
                new BigDecimal(annualRate));
    }

    /** 計算で分かった、払い終わらない理由を、毎月の支払額の入力エラーに変える（research.md R4、contracts のメッセージの一覧）。 */
    public InputValidationException toInputError(RevolvingNotPayableException exception) {
        String message =
                switch (exception.reason()) {
                    case FEE_NOT_COVERED -> "毎月の支払額が1回目の手数料以下のため、残高が減りません。毎月の支払額を増やしてください";
                    case TOO_MANY_PAYMENTS -> "完済までの回数が360回を超えます。毎月の支払額を増やしてください";
                };
        return new InputValidationException(List.of(new FieldError("monthlyPayment", message)));
    }

    /** 誤りがなければ true を返す（毎月の支払額が利用残高以下かを確かめるのに使う）。 */
    private static boolean checkBalance(String balance, List<FieldError> errors) {
        if (balance.isEmpty()) {
            errors.add(new FieldError("balance", "利用残高を入力してください"));
        } else if (!isInteger(balance)) {
            errors.add(new FieldError("balance", "利用残高は半角数字（整数）で入力してください"));
        } else if (isTooLong(balance) || !isWithinAmountRange(new BigDecimal(balance))) {
            errors.add(new FieldError("balance", "利用残高は1,000円以上10,000,000円以下で入力してください"));
        } else {
            return true;
        }
        return false;
    }

    /** validBalance は、利用残高に誤りがないときだけ渡す（誤りがあるときは null で、利用残高以下かどうかは確かめない）。 */
    private static void checkMonthlyPayment(
            String monthlyPayment, String validBalance, List<FieldError> errors) {
        if (monthlyPayment.isEmpty()) {
            errors.add(new FieldError("monthlyPayment", "毎月の支払額を入力してください"));
        } else if (!isInteger(monthlyPayment)) {
            errors.add(new FieldError("monthlyPayment", "毎月の支払額は半角数字（整数）で入力してください"));
        } else if (isTooLong(monthlyPayment)
                || !isWithinAmountRange(new BigDecimal(monthlyPayment))
                || new BigDecimal(monthlyPayment).remainder(PAYMENT_UNIT).signum() != 0) {
            errors.add(
                    new FieldError(
                            "monthlyPayment", "毎月の支払額は1,000円以上10,000,000円以下の、1,000円単位の額で入力してください"));
        } else if (validBalance != null
                && new BigDecimal(monthlyPayment).compareTo(new BigDecimal(validBalance)) > 0) {
            errors.add(new FieldError("monthlyPayment", "毎月の支払額は利用残高以下で入力してください"));
        }
    }

    private static boolean isWithinAmountRange(BigDecimal amount) {
        return amount.compareTo(MIN_AMOUNT) >= 0 && amount.compareTo(MAX_AMOUNT) <= 0;
    }
}
