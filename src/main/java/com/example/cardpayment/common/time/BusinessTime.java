package com.example.cardpayment.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 業務で使う日本時間（research.md R10）。サーバー（EC2 のコンテナ）の既定のタイムゾーンはふつう UTC なので、 既定に頼ると、日本時間の 0:00〜8:59
 * に申込日が前の日になる。日本時間はここで明示し、ほかの場所では書かない。
 *
 * <p>「今」は必ず Clock から取る。LocalDate.now()・ZoneId.systemDefault()・引数なしの Instant.now() は使わない。
 */
public final class BusinessTime {

    public static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    private BusinessTime() {}

    /** 日本時間の今日の日付。Clock のタイムゾーンにも頼らない。 */
    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), JAPAN);
    }

    /** 保存する今の時刻。PostgreSQL の timestamptz はマイクロ秒までなので、保存した値と返す値をそろえるために切り捨てる。 */
    public static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
