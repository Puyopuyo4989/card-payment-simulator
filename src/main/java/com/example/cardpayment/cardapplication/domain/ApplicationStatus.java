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

    /** 次に進める状態か（data-model.md 3）。許されるのは「申込→審査中」「審査中→承認」「審査中→否決」だけ。 */
    public boolean canChangeTo(ApplicationStatus to) {
        return switch (this) {
            case APPLIED -> to == UNDER_REVIEW;
            case UNDER_REVIEW -> to == APPROVED || to == REJECTED;
            case APPROVED, REJECTED -> false;
        };
    }

    /** 審査が終わった状態（承認・否決）。ここからはどの状態にも変えられない。 */
    public boolean isFinal() {
        return this == APPROVED || this == REJECTED;
    }

    /** 状態の値の文字列を enum に変える。値でないとき（null・空・小文字など）は空を返す。 */
    public static Optional<ApplicationStatus> parse(String value) {
        return Arrays.stream(values()).filter(status -> status.name().equals(value)).findFirst();
    }
}
