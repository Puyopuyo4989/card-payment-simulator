package com.example.cardpayment.cardapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.cardapplication.domain.CardApplication;
import com.example.cardpayment.cardapplication.domain.NewCardApplication;
import com.example.cardpayment.cardapplication.domain.StatusHistoryEntry;
import com.example.cardpayment.cardapplication.web.CardApplicationRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 個人情報を持つ record の toString に、個人情報を出さない（research.md R11-1 の4）。 うっかりログや例外に渡しても漏れないようにするため。 */
class PersonalDataToStringTest {

    private static final String NAME_KANJI = "テスト 太郎";
    private static final String NAME_KANA = "テスト　タロウ";
    private static final String BIRTH_DATE = "1990-04-01";
    private static final String ANNUAL_INCOME = "450";
    private static final String EMPLOYER_NAME = "テスト株式会社";
    private static final String PHONE_NUMBER = "0000000000";
    private static final List<String> PERSONAL_DATA =
            List.of(NAME_KANJI, NAME_KANA, BIRTH_DATE, ANNUAL_INCOME, EMPLOYER_NAME, PHONE_NUMBER);

    private static final Instant NOW = Instant.parse("2026-11-25T01:30:00Z");

    @Test
    void 登録の内容のtoStringに個人情報を出さない() {
        var application =
                new NewCardApplication(
                        NAME_KANJI,
                        NAME_KANA,
                        LocalDate.parse(BIRTH_DATE),
                        new BigDecimal(ANNUAL_INCOME),
                        EMPLOYER_NAME,
                        PHONE_NUMBER);

        assertThat(application.toString()).doesNotContain(PERSONAL_DATA);
    }

    @Test
    void 申込のtoStringに個人情報を出さず申込番号は出す() {
        var application =
                new CardApplication(
                        "A00000001",
                        NAME_KANJI,
                        NAME_KANA,
                        LocalDate.parse(BIRTH_DATE),
                        new BigDecimal(ANNUAL_INCOME),
                        EMPLOYER_NAME,
                        PHONE_NUMBER,
                        ApplicationStatus.APPLIED,
                        NOW,
                        NOW,
                        0);

        assertThat(application.toString()).doesNotContain(PERSONAL_DATA).contains("A00000001");
    }

    @Test
    void 登録のリクエストのtoStringに個人情報を出さない() {
        var request =
                new CardApplicationRequest(
                        NAME_KANJI,
                        NAME_KANA,
                        BIRTH_DATE,
                        ANNUAL_INCOME,
                        EMPLOYER_NAME,
                        PHONE_NUMBER);

        assertThat(request.toString()).doesNotContain(PERSONAL_DATA);
    }

    @Test
    void 履歴の行のtoStringに担当者名と否決の理由を出さない() {
        var entry =
                new StatusHistoryEntry(
                        NOW,
                        ApplicationStatus.UNDER_REVIEW,
                        ApplicationStatus.REJECTED,
                        "テスト 担当A",
                        "テスト用の否決理由");

        assertThat(entry.toString()).doesNotContain("テスト 担当A", "テスト用の否決理由");
    }
}
