package com.example.cardpayment.cardapplication.web;

import static com.example.cardpayment.common.web.InputRules.isInteger;
import static com.example.cardpayment.common.web.InputRules.isTooLong;
import static com.example.cardpayment.common.web.InputRules.strip;

import com.example.cardpayment.cardapplication.domain.NewCardApplication;
import com.example.cardpayment.common.time.BusinessTime;
import com.example.cardpayment.common.web.InputValidationException;
import com.example.cardpayment.common.web.InputValidationException.FieldError;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 申込の登録の入力を確かめて NewCardApplication に変える（data-model.md 2、contracts 1 のメッセージの一覧）。
 * 前後の空白（全角スペースを含む）を取り除き、項目ごとに「必須 → 形式 → 範囲」の順で最初に当てはまった1つを返す。 誤っている項目があれば、すべてを持つ
 * InputValidationException を投げる。メッセージは決まった文だけで、入力の値を入れない（research.md R11-1）。
 */
@Component
public class CardApplicationInputValidator {

    private static final int NAME_MAX_LENGTH = 50;
    private static final int EMPLOYER_NAME_MAX_LENGTH = 100;
    private static final int MIN_AGE = 18;
    private static final int MAX_AGE = 100;
    private static final BigDecimal MAX_ANNUAL_INCOME = new BigDecimal("9999");

    /** 全角カタカナ（ァ〜ヶ）・長音符（ー）・全角スペースだけ（research.md R9）。 */
    private static final Pattern KANA = Pattern.compile("[ァ-ヶー　]+");

    private static final Pattern PHONE_NUMBER = Pattern.compile("0[0-9]{9,10}");

    /** 実在する日付だけを読む（2月30日などは誤り）。 */
    private static final DateTimeFormatter BIRTH_DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final Clock clock;

    public CardApplicationInputValidator(Clock clock) {
        this.clock = clock;
    }

    public NewCardApplication validate(CardApplicationRequest request) {
        String nameKanji = strip(request.nameKanji());
        String nameKana = strip(request.nameKana());
        String birthDateText = strip(request.birthDate());
        String annualIncomeText = strip(request.annualIncome());
        String employerName = strip(request.employerName());
        String phoneNumber = strip(request.phoneNumber());

        List<FieldError> errors = new ArrayList<>();
        checkNameKanji(nameKanji, errors);
        checkNameKana(nameKana, errors);
        LocalDate birthDate = checkBirthDate(birthDateText, errors);
        checkAnnualIncome(annualIncomeText, errors);
        checkEmployerName(employerName, errors);
        checkPhoneNumber(phoneNumber, errors);
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        return new NewCardApplication(
                nameKanji,
                nameKana,
                birthDate,
                new BigDecimal(annualIncomeText),
                employerName,
                phoneNumber);
    }

    private static void checkNameKanji(String value, List<FieldError> errors) {
        if (value.isEmpty()) {
            errors.add(new FieldError("nameKanji", "氏名（漢字）を入力してください"));
        } else if (length(value) > NAME_MAX_LENGTH) {
            errors.add(new FieldError("nameKanji", "氏名（漢字）は50文字以内で入力してください"));
        }
    }

    private static void checkNameKana(String value, List<FieldError> errors) {
        if (value.isEmpty()) {
            errors.add(new FieldError("nameKana", "氏名（カナ）を入力してください"));
        } else if (!KANA.matcher(value).matches()) {
            errors.add(new FieldError("nameKana", "氏名（カナ）は全角カタカナで入力してください（姓と名の間は全角スペース）"));
        } else if (length(value) > NAME_MAX_LENGTH) {
            errors.add(new FieldError("nameKana", "氏名（カナ）は50文字以内で入力してください"));
        }
    }

    /** 誤りがなければ読んだ日付を返し、誤りがあれば errors に足して null を返す。 */
    private LocalDate checkBirthDate(String value, List<FieldError> errors) {
        if (value.isEmpty()) {
            errors.add(new FieldError("birthDate", "生年月日を入力してください"));
            return null;
        }
        LocalDate birthDate;
        try {
            birthDate = LocalDate.parse(value, BIRTH_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            errors.add(new FieldError("birthDate", "生年月日は実在する日付を、年-月-日の形で入力してください（例：1990-04-01）"));
            return null;
        }
        // 申込日は日本時間の今日（research.md R10）。年齢は満年齢
        LocalDate today = BusinessTime.today(clock);
        if (birthDate.isAfter(today)) {
            errors.add(new FieldError("birthDate", "生年月日に未来の日付は入力できません"));
            return null;
        }
        int age = Period.between(birthDate, today).getYears();
        if (age < MIN_AGE || age > MAX_AGE) {
            errors.add(new FieldError("birthDate", "申込日の時点で18歳以上100歳以下の方だけが申し込めます。生年月日を確かめてください"));
            return null;
        }
        return birthDate;
    }

    private static void checkAnnualIncome(String value, List<FieldError> errors) {
        if (value.isEmpty()) {
            errors.add(new FieldError("annualIncome", "年収を入力してください"));
        } else if (!isInteger(value)) {
            errors.add(new FieldError("annualIncome", "年収は半角数字（万円単位の整数）で入力してください"));
        } else if (isTooLong(value) || new BigDecimal(value).compareTo(MAX_ANNUAL_INCOME) > 0) {
            // 形式のチェックで負の数は除かれているので、下限（0）は確かめなくてよい
            errors.add(new FieldError("annualIncome", "年収は0万円以上9,999万円以下で入力してください"));
        }
    }

    private static void checkEmployerName(String value, List<FieldError> errors) {
        if (value.isEmpty()) {
            errors.add(new FieldError("employerName", "勤務先名を入力してください"));
        } else if (length(value) > EMPLOYER_NAME_MAX_LENGTH) {
            errors.add(new FieldError("employerName", "勤務先名は100文字以内で入力してください"));
        }
    }

    private static void checkPhoneNumber(String value, List<FieldError> errors) {
        if (value.isEmpty()) {
            errors.add(new FieldError("phoneNumber", "電話番号を入力してください"));
        } else if (!PHONE_NUMBER.matcher(value).matches()) {
            errors.add(new FieldError("phoneNumber", "電話番号は、0から始まる10桁か11桁の半角数字を、ハイフンなしで入力してください"));
        }
    }

    /** 文字数をコードポイントで数える（「𠮷」のような4バイトの文字も1文字。research.md R9）。 */
    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
