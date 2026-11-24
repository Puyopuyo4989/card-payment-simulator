package com.example.cardpayment.revolving.domain;

/** 条件どおりに払うと、払い終わらないことを表す（data-model.md 5）。利用者に見せるメッセージは持たず、Web 層が理由ごとに入力エラーに変える。 */
public class RevolvingNotPayableException extends RuntimeException {

    /** 払い終わらない理由。 */
    public enum Reason {
        /** 毎月の支払額が1回目の手数料以下で、残高が減らない。 */
        FEE_NOT_COVERED,
        /** 完済までの回数が360回を超える。 */
        TOO_MANY_PAYMENTS
    }

    private final Reason reason;

    public RevolvingNotPayableException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
