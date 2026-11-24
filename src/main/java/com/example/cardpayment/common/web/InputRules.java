package com.example.cardpayment.common.web;

import com.example.cardpayment.common.web.InputValidationException.FieldError;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/** 分割払いとリボ払いに共通の、入力の文字列の解釈（data-model.md 1）。値は strip で前後の空白を取り除いてから渡す。 */
public final class InputRules {

    private static final Pattern INTEGER = Pattern.compile("[0-9]+");
    private static final Pattern RATE = Pattern.compile("[0-9]+(\\.[0-9])?");

    /** 各項目に共通の長さの上限（前後の空白を取り除いたあとの文字数）。形式に合っていて超えたものは、範囲の誤りにする （形式に合わないものは、長さにかかわらず形式の誤り）。 */
    private static final int MAX_LENGTH = 20;

    private static final BigDecimal MAX_RATE = new BigDecimal("15.0");

    private InputRules() {}

    /** 未指定（null）は空文字として扱い、前後の空白を取り除く。 */
    public static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    /** 半角数字だけでできている。小数・負の数・カンマ・全角数字は含まない。 */
    public static boolean isInteger(String value) {
        return INTEGER.matcher(value).matches();
    }

    /** 長さが上限を超える。数として解釈する前に確かめ、長すぎる文字列を BigDecimal に変えない。 */
    public static boolean isTooLong(String value) {
        return value.length() > MAX_LENGTH;
    }

    /** 実質年率を「必須 → 形式 → 範囲」の順で確かめ、誤りがあれば errors の後ろに1つ足す。メッセージは2機能で同じ。 */
    public static void checkAnnualRate(String annualRate, List<FieldError> errors) {
        if (annualRate.isEmpty()) {
            errors.add(new FieldError("annualRate", "実質年率を入力してください"));
        } else if (!RATE.matcher(annualRate).matches()) {
            errors.add(new FieldError("annualRate", "実質年率は半角数字で、小数第1位までで入力してください（例：12.5）"));
        } else if (isTooLong(annualRate) || new BigDecimal(annualRate).compareTo(MAX_RATE) > 0) {
            // 形式のチェックで負の数は除かれているので、下限（0.0）は確かめなくてよい
            errors.add(new FieldError("annualRate", "実質年率は0.0%以上15.0%以下で入力してください"));
        }
    }
}
