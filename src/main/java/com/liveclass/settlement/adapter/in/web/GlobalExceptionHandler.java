package com.liveclass.settlement.adapter.in.web;

import com.liveclass.settlement.application.actor.ActorAccessDenied;
import com.liveclass.settlement.domain.sales.RefundAmountExceeded;
import com.liveclass.settlement.domain.settlement.CourseNotFound;
import com.liveclass.settlement.domain.settlement.InvalidSettlementPeriod;
import com.liveclass.settlement.domain.settlement.SaleNotFound;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 모든 실패를 RFC 9457 Problem Details 한 가지 모양으로 바꾼다.
 *
 * <p>본문 타입을 직접 만들지 않는다. Spring이 {@link ProblemDetail}을 내장하고
 * {@code application/problem+json}으로 직렬화한다. {@code code}는 RFC 9457 확장
 * 멤버로 남겨 기계가 읽을 판별자를 유지한다 -- 표준 필드 {@code type}이
 * {@code about:blank}라 그 역할이 비기 때문이다.
 *
 * <p><b>{@code Exception}을 잡는 catch-all을 두지 않는다.</b>
 * {@code IllegalArgumentException}과 {@code NullPointerException}은 Task 3 값 타입
 * 불변식 위반, 즉 우리 코드의 버그다. Spring 기본 500으로 나가야 스택트레이스가
 * 로그에 남는다. 400으로 싸잡으면 프로그래밍 버그가 사용자 오류로 위장돼 사라진다.
 *
 * <p>도메인 실패는 WARN이다. ERROR로 두면 정상 동작이 알람을 울린다 -- 초과 환불
 * 거부는 시스템 오류가 아니라 규칙이 제대로 작동한 결과다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({SaleNotFound.class, CourseNotFound.class})
    ProblemDetail notFound(RuntimeException e, HttpServletRequest request) {
        String code = e instanceof SaleNotFound ? "SALE_NOT_FOUND" : "COURSE_NOT_FOUND";
        return problem(HttpStatus.NOT_FOUND, code, e.getMessage(), request);
    }

    @ExceptionHandler(RefundAmountExceeded.class)
    ProblemDetail refundExceeded(RefundAmountExceeded e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "REFUND_AMOUNT_EXCEEDED", e.getMessage(), request);
    }

    @ExceptionHandler(ActorAccessDenied.class)
    ProblemDetail accessDenied(ActorAccessDenied e, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "ACTOR_ACCESS_DENIED", e.getMessage(), request);
    }

    @ExceptionHandler(InvalidSettlementPeriod.class)
    ProblemDetail invalidPeriod(InvalidSettlementPeriod e, HttpServletRequest request) {
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
        body.setType(URI.create("urn:problem-type:" + code.toLowerCase().replace('_', '-')));
        body.setProperty("code", code);
        body.setInstance(URI.create(request.getRequestURI()));
        log.warn("request failed: code={}, status={}, path={}, detail={}",
                code, status.value(), request.getRequestURI(), detail);
        return body;
    }
}
