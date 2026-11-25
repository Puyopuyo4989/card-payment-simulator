package com.example.cardpayment.cardapplication.domain;

/** 許されない状態の変更（data-model.md 3）。メッセージは理由と状態だけ（research.md R11-1 の2）。 */
public class StatusTransitionException extends RuntimeException {

    /** 許されない理由。Web 層が contracts 4 のメッセージに変える。 */
    public enum Reason {
        /** 申込から承認・否決にしようとした。先に審査中にする必要がある */
        REVIEW_REQUIRED_FIRST,
        /** 承認・否決の申込を変えようとした */
        ALREADY_DECIDED,
        /** そのほかの許されない変更 */
        NOT_ALLOWED
    }

    private final Reason reason;

    public StatusTransitionException(Reason reason, ApplicationStatus from, ApplicationStatus to) {
        super("状態を変えられません: " + reason + " (" + from + " -> " + to + ")");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
