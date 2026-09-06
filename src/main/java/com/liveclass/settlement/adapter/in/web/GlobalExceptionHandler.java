package com.liveclass.settlement.adapter.in.web;

import com.liveclass.settlement.application.access.ActorAccessDeniedException;
import com.liveclass.settlement.domain.sales.CancelBeforePaymentException;
import com.liveclass.settlement.domain.sales.CourseNotFoundException;
import com.liveclass.settlement.domain.sales.RefundAmountExceededException;
import com.liveclass.settlement.domain.sales.SaleNotFoundException;
import com.liveclass.settlement.domain.settlement.InvalidSettlementPeriodException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 모든 실패를 RFC 9457 Problem Details로 바꾼다.
 *
 * {@code Exception} catch-all을 두지 않는다. {@code IllegalArgumentException},
 * {@code NullPointerException}은 우리 코드의 버그이므로 400으로 위장시키지 않는다.
 *
 * {@code @Order}를 빼면 안 된다. Spring의 {@code ProblemDetailsExceptionHandler}가
 * {@code @Order(0)}이라, 순서를 안 주면 검증·역직렬화 예외를 Spring이 먼저 가져가
 * {@code code}가 사라진다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({SaleNotFoundException.class, CourseNotFoundException.class})
    ProblemDetail notFound(RuntimeException e, HttpServletRequest request) {
        String code = e instanceof SaleNotFoundException ? "SALE_NOT_FOUND" : "COURSE_NOT_FOUND";
        return problem(HttpStatus.NOT_FOUND, code, e.getMessage(), request);
    }

    @ExceptionHandler(RefundAmountExceededException.class)
    ProblemDetail refundExceeded(RefundAmountExceededException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "REFUND_AMOUNT_EXCEEDED", e.getMessage(), request);
    }

    @ExceptionHandler(CancelBeforePaymentException.class)
    ProblemDetail cancelBeforePayment(CancelBeforePaymentException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "CANCEL_BEFORE_PAYMENT", e.getMessage(), request);
    }

    @ExceptionHandler(ActorAccessDeniedException.class)
    ProblemDetail accessDenied(ActorAccessDeniedException e, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "ACTOR_ACCESS_DENIED", e.getMessage(), request);
    }

    @ExceptionHandler(InvalidSettlementPeriodException.class)
    ProblemDetail invalidPeriod(InvalidSettlementPeriodException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_SETTLEMENT_PERIOD", e.getMessage(), request);
    }

    /**
     * 안 잡으면 Spring이 자체 {@code ProblemDetail}을 내보내 {@code code} 확장
     * 멤버가 빠진다. 여러 필드가 실패하면 첫 위반의 메시지를 쓴다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validationFailed(MethodArgumentNotValidException e, HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("validation failed");
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail, request);
    }

    /**
     * Task 1의 {@code ActorContextArgumentResolver}가 헤더 오류에 이 예외를 던진다.
     * Task 1 코드를 고치지 않고 여기서 흡수한다. 상태는 예외가 든 값을 그대로 쓴다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail actorHeader(ResponseStatusException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        return problem(status, "INVALID_ACTOR_HEADER", e.getReason(), request);
    }

    /** 오프셋 없는 시각 등 역직렬화 실패. DTO를 OffsetDateTime으로 둔 이유가 이것이다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformed(HttpMessageNotReadableException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "request body is malformed", request);
    }

    /** from/to 누락. 안 잡으면 "모든 실패가 한 가지 모양"이라는 주장이 거짓이 된다. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail missingParameter(MissingServletRequestParameterException e,
                                   HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", e.getMessage(), request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail,
                                  HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                status, detail != null ? detail : status.getReasonPhrase());
        // 기본값 about:blank 는 직렬화에서 생략된다. 실제 타입 URI를 넣어야
        // type 이 응답에 남고, RFC 9457도 문제 유형을 식별하는 URI를 권한다.
        // 문서를 호스팅하지 않으므로 URN을 쓴다.
        body.setType(URI.create(
                "urn:problem-type:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        body.setProperty("code", code);
        body.setInstance(URI.create(request.getRequestURI()));
        log.warn("request failed: code={}, status={}, path={}, detail={}",
                code, status.value(), request.getRequestURI(), detail);
        return body;
    }
}
