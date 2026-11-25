package com.example.cardpayment.cardapplication.web;

import static com.example.cardpayment.common.web.InputRules.isInteger;
import static com.example.cardpayment.common.web.InputRules.isTooLong;
import static com.example.cardpayment.common.web.InputRules.strip;

import com.example.cardpayment.cardapplication.domain.ApplicationStatus;
import com.example.cardpayment.common.web.InputValidationException;
import com.example.cardpayment.common.web.InputValidationException.FieldError;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 状態の変更の入力を確かめる（data-model.md 4 の表、contracts 4 のメッセージの一覧）。DB は読まない。 誤りは toStatus → staffName →
 * rejectionReason → version の順で返す。toStatus が未入力・値の誤りのときは、 否決の理由が要るかどうかが決まらないので、rejectionReason
 * は確かめない。メッセージに入力の値を入れない（research.md R11-1）。
 */
@Component
public class StatusChangeInputValidator {

    private static final int STAFF_NAME_MAX_LENGTH = 50;
    private static final int REASON_MAX_LENGTH = 500;
    private static final int INT_MAX_DIGITS = String.valueOf(Integer.MAX_VALUE).length();
    private static final String VERSION_INVALID = "画面を開き直してから、もう一度操作してください";

    public ValidStatusChange validate(StatusChangeRequest request) {
        String toStatusText = strip(request.toStatus());
        String staffName = strip(request.staffName());
        String reason = strip(request.rejectionReason());
        String versionText = strip(request.version());

        List<FieldError> errors = new ArrayList<>();
        Optional<ApplicationStatus> toStatus = checkToStatus(toStatusText, errors);
        checkStaffName(staffName, errors);
        boolean rejecting = toStatus.isPresent() && toStatus.get() == ApplicationStatus.REJECTED;
        if (rejecting) {
            checkReason(reason, errors);
        }
        Optional<Integer> version = parseVersion(versionText);
        if (version.isEmpty()) {
            errors.add(new FieldError("version", VERSION_INVALID));
        }
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        return new ValidStatusChange(
                toStatus.get(), staffName, rejecting ? reason : null, version.get());
    }

    private static Optional<ApplicationStatus> checkToStatus(
            String value, List<FieldError> errors) {
        if (value.isEmpty()) {
            errors.add(new FieldError("toStatus", "変更後の状態を指定してください"));
            return Optional.empty();
        }
        Optional<ApplicationStatus> status = ApplicationStatus.parse(value);
        if (status.isEmpty()) {
            errors.add(
                    new FieldError(
                            "toStatus",
                            "変更後の状態は APPLIED・UNDER_REVIEW・APPROVED・REJECTED のどれかを指定してください"));
        }
        return status;
    }

    private static void checkStaffName(String value, List<FieldError> errors) {
        if (value.isEmpty()) {
            errors.add(new FieldError("staffName", "担当者名を入力してください"));
        } else if (length(value) > STAFF_NAME_MAX_LENGTH) {
            errors.add(new FieldError("staffName", "担当者名は50文字以内で入力してください"));
        }
    }

    private static void checkReason(String value, List<FieldError> errors) {
        if (value.isEmpty()) {
            errors.add(new FieldError("rejectionReason", "否決の理由を入力してください"));
        } else if (length(value) > REASON_MAX_LENGTH) {
            errors.add(new FieldError("rejectionReason", "否決の理由は500文字以内で入力してください"));
        }
    }

    /** 半角数字だけで、int に収まるときだけ読む。長すぎる文字列は数に変える前に除く（data-model.md 4）。 */
    private static Optional<Integer> parseVersion(String value) {
        if (!isInteger(value) || isTooLong(value)) {
            return Optional.empty();
        }
        String digits = value.replaceFirst("^0+(?=.)", "");
        if (digits.length() > INT_MAX_DIGITS) {
            return Optional.empty();
        }
        long parsed = Long.parseLong(digits);
        return parsed > Integer.MAX_VALUE ? Optional.empty() : Optional.of((int) parsed);
    }

    /** 文字数をコードポイントで数える（research.md R9）。 */
    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
