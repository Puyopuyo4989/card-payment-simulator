package com.example.cardpayment.cardapplication.domain;

import java.util.Arrays;
import java.util.Optional;

/** 申込の状態（data-model.md 3、spec FR-014）。API と DB では、この名前（APPLIED など）をそのまま値に使う。 */
public enum ApplicationStatus {
    /** 申込 */
    APPLIED,
    /** 審査中 */
    UNDER_REVIEW,
    /** 承認 */
    APPROVED,
    /** 否決 */
    REJECTED;

    /** 状態の値の文字列を enum に変える。値でないとき（null・空・小文字など）は空を返す。 */
    public static Optional<ApplicationStatus> parse(String value) {
        return Arrays.stream(values()).filter(status -> status.name().equals(value)).findFirst();
    }
}
