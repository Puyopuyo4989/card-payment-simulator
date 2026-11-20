package com.example.cardpayment.common.web;

import com.example.cardpayment.installment.web.InstallmentInputException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** API のエラーを RFC 9457（Problem Details）の 400 で返す（contracts の 400）。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InstallmentInputException.class)
    public ProblemDetail handleInputError(InstallmentInputException exception) {
        var problem = badRequest("入力に誤りがあります");
        problem.setProperty("errors", exception.errors());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody() {
        return badRequest("リクエストの形式が正しくありません");
    }

    private static ProblemDetail badRequest(String title) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        // Spring 7 は type を指定しないと書き出さないので、contracts のとおり about:blank を明示する
        problem.setType(URI.create("about:blank"));
        problem.setTitle(title);
        return problem;
    }
}
