package com.example.cardpayment.cardapplication.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.cardpayment.cardapplication.domain.NewCardApplication;
import com.example.cardpayment.common.web.InputValidationException;
import com.example.cardpayment.common.web.InputValidationException.FieldError;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 申込の登録の入力チェック（data-model.md 2、contracts 1 のメッセージの一覧）。 申込日は日本時間の 2026-11-25（Clock は UTC の 03:00 ＝
 * 日本時間の 12:00）。値はすべて架空のもの。
 */
class CardApplicationInputValidatorTest {

    private static final String NAME_KANJI_REQUIRED = "氏名（漢字）を入力してください";
    private static final String NAME_KANJI_LENGTH = "氏名（漢字）は50文字以内で入力してください";
    private static final String NAME_KANA_REQUIRED = "氏名（カナ）を入力してください";
    private static final String NAME_KANA_FORMAT = "氏名（カナ）は全角カタカナで入力してください（姓と名の間は全角スペース）";
    private static final String NAME_KANA_LENGTH = "氏名（カナ）は50文字以内で入力してください";
    private static final String BIRTH_DATE_REQUIRED = "生年月日を入力してください";
    private static final String BIRTH_DATE_FORMAT = "生年月日は実在する日付を、年-月-日の形で入力してください（例：1990-04-01）";
    private static final String BIRTH_DATE_FUTURE = "生年月日に未来の日付は入力できません";
    private static final String BIRTH_DATE_AGE = "申込日の時点で18歳以上100歳以下の方だけが申し込めます。生年月日を確かめてください";
    private static final String ANNUAL_INCOME_REQUIRED = "年収を入力してください";
    private static final String ANNUAL_INCOME_FORMAT = "年収は半角数字（万円単位の整数）で入力してください";
    private static final String ANNUAL_INCOME_RANGE = "年収は0万円以上9,999万円以下で入力してください";
    private static final String EMPLOYER_NAME_REQUIRED = "勤務先名を入力してください";
    private static final String EMPLOYER_NAME_LENGTH = "勤務先名は100文字以内で入力してください";
    private static final String PHONE_NUMBER_REQUIRED = "電話番号を入力してください";
    private static final String PHONE_NUMBER_FORMAT = "電話番号は、0から始まる10桁か11桁の半角数字を、ハイフンなしで入力してください";

    private final CardApplicationInputValidator validator =
            new CardApplicationInputValidator(
                    Clock.fixed(Instant.parse("2026-11-25T03:00:00Z"), ZoneOffset.UTC));

    /** 6項目とも正しい入力。各テストは、1つの項目だけを変えて使う。 */
    private static CardApplicationRequest valid() {
        return new CardApplicationRequest(
                "テスト 太郎", "テスト　タロウ", "1990-04-01", "450", "テスト株式会社", "0000000000");
    }

    // 正しい入力

    @Test
    void 正しい6項目を登録の内容に変える() {
        NewCardApplication result = validator.validate(valid());

        assertThat(result.nameKanji()).isEqualTo("テスト 太郎");
        assertThat(result.nameKana()).isEqualTo("テスト　タロウ");
        assertThat(result.birthDate()).isEqualTo(LocalDate.of(1990, 4, 1));
        assertThat(result.annualIncome()).isEqualByComparingTo("450");
        assertThat(result.employerName()).isEqualTo("テスト株式会社");
        assertThat(result.phoneNumber()).isEqualTo("0000000000");
    }

    @Test
    void 前後の空白と全角スペースを取り除いて保存する() {
        NewCardApplication result =
                validator.validate(
                        new CardApplicationRequest(
                                "　テスト 太郎 ",
                                " テスト　タロウ　",
                                " 1990-04-01 ",
                                " 450 ",
                                "　テスト株式会社　",
                                " 0000000000 "));

        assertThat(result.nameKanji()).isEqualTo("テスト 太郎");
        assertThat(result.nameKana()).isEqualTo("テスト　タロウ");
        assertThat(result.birthDate()).isEqualTo(LocalDate.of(1990, 4, 1));
        assertThat(result.annualIncome()).isEqualByComparingTo("450");
        assertThat(result.employerName()).isEqualTo("テスト株式会社");
        assertThat(result.phoneNumber()).isEqualTo("0000000000");
    }

    @Test
    void 年収は小数のないBigDecimalにする() {
        NewCardApplication result = validator.validate(valid());

        assertThat(result.annualIncome().scale()).isZero();
    }

    // 氏名（漢字）

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "　", " 　 "})
    void 氏名漢字が未入力か空白だけのときはエラー(String value) {
        assertThat(errorsOf(withNameKanji(value)))
                .containsExactly(new FieldError("nameKanji", NAME_KANJI_REQUIRED));
    }

    @Test
    void 氏名漢字は50文字まで() {
        assertThat(validator.validate(withNameKanji("あ".repeat(50))).nameKanji()).hasSize(50);
        assertThat(errorsOf(withNameKanji("あ".repeat(51))))
                .containsExactly(new FieldError("nameKanji", NAME_KANJI_LENGTH));
    }

    @Test
    void 氏名漢字の文字数は4バイトの文字も1文字と数える() {
        String fiftyChars = "𠮷".repeat(50);

        assertThat(validator.validate(withNameKanji(fiftyChars)).nameKanji()).isEqualTo(fiftyChars);
        assertThat(errorsOf(withNameKanji("𠮷".repeat(51))))
                .containsExactly(new FieldError("nameKanji", NAME_KANJI_LENGTH));
    }

    // 氏名（カナ）

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "　"})
    void 氏名カナが未入力か空白だけのときはエラー(String value) {
        assertThat(errorsOf(withNameKana(value)))
                .containsExactly(new FieldError("nameKana", NAME_KANA_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"テスト　タロウ", "ヴァーチャル", "テストー", "ヵヶ"})
    void 氏名カナは全角カタカナと長音符と全角スペースを受け付ける(String value) {
        assertThat(validator.validate(withNameKana(value)).nameKana()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"テスト タロウ", "てすと", "ﾃｽﾄ", "テスト・タロウ", "テスト太郎", "TESUTO"})
    void 氏名カナにほかの文字があるときはエラー(String value) {
        assertThat(errorsOf(withNameKana(value)))
                .containsExactly(new FieldError("nameKana", NAME_KANA_FORMAT));
    }

    @Test
    void 氏名カナは50文字まで() {
        assertThat(validator.validate(withNameKana("テ".repeat(50))).nameKana()).hasSize(50);
        assertThat(errorsOf(withNameKana("テ".repeat(51))))
                .containsExactly(new FieldError("nameKana", NAME_KANA_LENGTH));
    }

    // 生年月日（申込日は 2026-11-25）

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void 生年月日が未入力のときはエラー(String value) {
        assertThat(errorsOf(withBirthDate(value)))
                .containsExactly(new FieldError("birthDate", BIRTH_DATE_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2026-02-30",
                "1990-02-29",
                "1990-13-01",
                "1990/04/01",
                "19900401",
                "1990-4-1",
                "abc"
            })
    void 生年月日が形の誤りか存在しない日付のときはエラー(String value) {
        assertThat(errorsOf(withBirthDate(value)))
                .containsExactly(new FieldError("birthDate", BIRTH_DATE_FORMAT));
    }

    @Test
    void うるう年の2月29日は実在する日付() {
        assertThat(validator.validate(withBirthDate("2000-02-29")).birthDate())
                .isEqualTo(LocalDate.of(2000, 2, 29));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-26", "2100-01-01"})
    void 生年月日が未来の日付のときはエラー(String value) {
        assertThat(errorsOf(withBirthDate(value)))
                .containsExactly(new FieldError("birthDate", BIRTH_DATE_FUTURE));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2008-11-25", // この日に18歳になる
                "1926-11-25", // この日に100歳になる
                "1925-11-26", // 明日101歳になる（今日は100歳）
            })
    void 申込日に18歳以上100歳以下なら登録できる(String value) {
        assertThat(validator.validate(withBirthDate(value)).birthDate())
                .isEqualTo(LocalDate.parse(value));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2008-11-26", // 17歳（明日18歳になる）
                "2026-11-25", // 今日生まれ（0歳）
                "1925-11-25", // この日に101歳になる
            })
    void 申込日に18歳未満か100歳を超えるときはエラー(String value) {
        assertThat(errorsOf(withBirthDate(value)))
                .containsExactly(new FieldError("birthDate", BIRTH_DATE_AGE));
    }

    // 年収（万円）

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void 年収が未入力のときはエラー(String value) {
        assertThat(errorsOf(withAnnualIncome(value)))
                .containsExactly(new FieldError("annualIncome", ANNUAL_INCOME_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "9999", "0450"})
    void 年収は0から9999まで受け付ける(String value) {
        assertThat(validator.validate(withAnnualIncome(value)).annualIncome())
                .isEqualByComparingTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "-1", "１００", "1,000", "abc", "450万"})
    void 年収が半角数字の整数でないときはエラー(String value) {
        assertThat(errorsOf(withAnnualIncome(value)))
                .containsExactly(new FieldError("annualIncome", ANNUAL_INCOME_FORMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10000", "123456789012345678901"})
    void 年収が9999を超えるときはエラー(String value) {
        assertThat(errorsOf(withAnnualIncome(value)))
                .containsExactly(new FieldError("annualIncome", ANNUAL_INCOME_RANGE));
    }

    // 勤務先名

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "　"})
    void 勤務先名が未入力か空白だけのときはエラー(String value) {
        assertThat(errorsOf(withEmployerName(value)))
                .containsExactly(new FieldError("employerName", EMPLOYER_NAME_REQUIRED));
    }

    @Test
    void 勤務先名は100文字まで() {
        assertThat(validator.validate(withEmployerName("あ".repeat(100))).employerName())
                .hasSize(100);
        assertThat(errorsOf(withEmployerName("あ".repeat(101))))
                .containsExactly(new FieldError("employerName", EMPLOYER_NAME_LENGTH));
    }

    // 電話番号

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void 電話番号が未入力のときはエラー(String value) {
        assertThat(errorsOf(withPhoneNumber(value)))
                .containsExactly(new FieldError("phoneNumber", PHONE_NUMBER_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000000000", "00000000000"})
    void 電話番号は0から始まる10桁か11桁を受け付ける(String value) {
        assertThat(validator.validate(withPhoneNumber(value)).phoneNumber()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "000000000",
                "000000000000",
                "1000000000",
                "03-0000-0000",
                "０００００００００００",
                "0000a00000"
            })
    void 電話番号の形が違うときはエラー(String value) {
        assertThat(errorsOf(withPhoneNumber(value)))
                .containsExactly(new FieldError("phoneNumber", PHONE_NUMBER_FORMAT));
    }

    // 複数の誤り（FR-003）

    @Test
    void 誤っている項目はすべて決まった順に返す() {
        var request =
                new CardApplicationRequest("", "てすと", "2026-02-30", "10000", "", "03-0000-0000");

        assertThat(errorsOf(request))
                .containsExactly(
                        new FieldError("nameKanji", NAME_KANJI_REQUIRED),
                        new FieldError("nameKana", NAME_KANA_FORMAT),
                        new FieldError("birthDate", BIRTH_DATE_FORMAT),
                        new FieldError("annualIncome", ANNUAL_INCOME_RANGE),
                        new FieldError("employerName", EMPLOYER_NAME_REQUIRED),
                        new FieldError("phoneNumber", PHONE_NUMBER_FORMAT));
    }

    @Test
    void エラーのメッセージに入力した値を入れない() {
        var request =
                new CardApplicationRequest(
                        "テ".repeat(51),
                        "てすと",
                        "2026-02-30",
                        "99999",
                        "あ".repeat(101),
                        "03-0000-0000");

        var exception =
                catchThrowableOfType(
                        InputValidationException.class, () -> validator.validate(request));

        assertThat(exception.errors()).hasSize(6);
        for (String value : List.of("テテテ", "てすと", "2026-02-30", "99999", "あああ", "03-0000-0000")) {
            assertThat(exception.getMessage()).doesNotContain(value);
            assertThat(exception.errors())
                    .allSatisfy(e -> assertThat(e.message()).doesNotContain(value));
        }
    }

    private List<FieldError> errorsOf(CardApplicationRequest request) {
        return catchThrowableOfType(
                        InputValidationException.class, () -> validator.validate(request))
                .errors();
    }

    private static CardApplicationRequest withNameKanji(String value) {
        var v = valid();
        return new CardApplicationRequest(
                value,
                v.nameKana(),
                v.birthDate(),
                v.annualIncome(),
                v.employerName(),
                v.phoneNumber());
    }

    private static CardApplicationRequest withNameKana(String value) {
        var v = valid();
        return new CardApplicationRequest(
                v.nameKanji(),
                value,
                v.birthDate(),
                v.annualIncome(),
                v.employerName(),
                v.phoneNumber());
    }

    private static CardApplicationRequest withBirthDate(String value) {
        var v = valid();
        return new CardApplicationRequest(
                v.nameKanji(),
                v.nameKana(),
                value,
                v.annualIncome(),
                v.employerName(),
                v.phoneNumber());
    }

    private static CardApplicationRequest withAnnualIncome(String value) {
        var v = valid();
        return new CardApplicationRequest(
                v.nameKanji(),
                v.nameKana(),
                v.birthDate(),
                value,
                v.employerName(),
                v.phoneNumber());
    }

    private static CardApplicationRequest withEmployerName(String value) {
        var v = valid();
        return new CardApplicationRequest(
                v.nameKanji(),
                v.nameKana(),
                v.birthDate(),
                v.annualIncome(),
                value,
                v.phoneNumber());
    }

    private static CardApplicationRequest withPhoneNumber(String value) {
        var v = valid();
        return new CardApplicationRequest(
                v.nameKanji(),
                v.nameKana(),
                v.birthDate(),
                v.annualIncome(),
                v.employerName(),
                value);
    }
}
