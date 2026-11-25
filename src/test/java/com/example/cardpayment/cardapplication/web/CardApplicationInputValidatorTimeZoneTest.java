package com.example.cardpayment.cardapplication.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.cardpayment.common.web.InputValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 申込日を日本時間で決めることの確認（research.md R10 の表）。Clock は UTC で渡す。 日本時間の 0:00〜8:59 は、UTC ではまだ前の日なので、UTC
 * の日付で判定していれば2・3行目の結果が逆になる。
 */
class CardApplicationInputValidatorTimeZoneTest {

    @ParameterizedTest(name = "{0}（日本時間 {1}）")
    @CsvSource({
        // Clock の時刻（UTC）, 日本時間, 2008-11-25 生まれを受け付けるか, 1925-11-25 生まれを受け付けるか
        "2026-11-24T14:59:59Z, 11/24 23:59:59, false, true",
        "2026-11-24T15:00:00Z, 11/25 0:00:00, true, false",
        "2026-11-24T23:59:59Z, 11/25 8:59:59, true, false",
        "2026-11-25T00:00:00Z, 11/25 9:00:00, true, false",
    })
    void 申込日を日本時間の日付で決める(String instant, String japanTime, boolean accepts18, boolean accepts100) {
        var validator =
                new CardApplicationInputValidator(
                        Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));

        assertThat(accepts(validator, "2008-11-25")).as("18歳の境界").isEqualTo(accepts18);
        assertThat(accepts(validator, "1925-11-25")).as("101歳の境界").isEqualTo(accepts100);
    }

    private static boolean accepts(CardApplicationInputValidator validator, String birthDate) {
        var request =
                new CardApplicationRequest(
                        "テスト 太郎", "テスト　タロウ", birthDate, "450", "テスト株式会社", "0000000000");
        return catchThrowableOfType(
                        InputValidationException.class, () -> validator.validate(request))
                == null;
    }
}
