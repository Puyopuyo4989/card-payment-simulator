package com.example.cardpayment.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 日本時間の日付と、保存する時刻（research.md R10）。Clock は UTC のものを渡し、 日本時間の 0:00〜8:59（UTC
 * ではまだ前の日）でも日付がずれないことを確かめる。
 */
class BusinessTimeTest {

    @Test
    void 日本時間はAsiaTokyo() {
        assertThat(BusinessTime.JAPAN).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @ParameterizedTest
    @CsvSource({
        // Clock の時刻（UTC）, 日本時間の日付
        "2026-11-24T14:59:59Z, 2026-11-24", // 日本時間 11/24 23:59:59
        "2026-11-24T15:00:00Z, 2026-11-25", // 日本時間 11/25 0:00:00（UTC ではまだ 11/24）
        "2026-11-24T23:59:59Z, 2026-11-25", // 日本時間 11/25 8:59:59（UTC ではまだ 11/24）
        "2026-11-25T00:00:00Z, 2026-11-25", // 日本時間 11/25 9:00:00
    })
    void 今日の日付を日本時間で返す(String instant, String expected) {
        Clock utcClock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);

        assertThat(BusinessTime.today(utcClock)).isEqualTo(LocalDate.parse(expected));
    }

    @Test
    void JVMの既定のタイムゾーンがUTCでも日本時間の日付を返す() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Clock utcClock = Clock.fixed(Instant.parse("2026-11-24T15:00:00Z"), ZoneOffset.UTC);

            assertThat(BusinessTime.today(utcClock)).isEqualTo(LocalDate.of(2026, 11, 25));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void 保存する時刻はマイクロ秒に切り捨てる() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-25T01:30:00.123456789Z"), ZoneOffset.UTC);

        assertThat(BusinessTime.now(clock)).isEqualTo(Instant.parse("2026-11-25T01:30:00.123456Z"));
    }
}
