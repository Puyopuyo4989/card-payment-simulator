package com.example.cardpayment.common.web;

import com.example.cardpayment.cardapplication.service.ApplicationNotFoundException;
import com.example.cardpayment.cardapplication.service.StatusConflictException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** API のエラーを RFC 9457（Problem Details）で返す（各機能の contracts の 400・404・409）。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InputValidationException.class)
    public ProblemDetail handleInputError(InputValidationException exception) {
        var problem = badRequest("入力に誤りがあります");
        problem.setProperty("errors", exception.errors());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody() {
        return badRequest("リクエストの形式が正しくありません");
    }

    /** 申込が見つからない（申込管理の contracts 3）。申込番号は本文の instance に入る。 */
    @ExceptionHandler(ApplicationNotFoundException.class)
    public ProblemDetail handleApplicationNotFound() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "申込番号を確かめてください");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("申込が見つかりません");
        return problem;
    }

    /** ほかの担当者が先に状態を変えた（申込管理の contracts 4）。errors は付けない。 */
    @ExceptionHandler(StatusConflictException.class)
    public ProblemDetail handleStatusConflict() {
        var problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "最新の状態を確かめてから、もう一度操作してください");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("ほかの担当者が先に状態を変えました");
        return problem;
    }

    private static ProblemDetail badRequest(String title) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        // Spring 7 は type を指定しないと書き出さないので、contracts のとおり about:blank を明示する
        problem.setType(URI.create("about:blank"));
        problem.setTitle(title);
        return problem;
    }
}
