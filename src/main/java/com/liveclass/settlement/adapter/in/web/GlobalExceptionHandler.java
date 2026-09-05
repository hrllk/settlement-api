package com.liveclass.settlement.adapter.in.web;

import com.liveclass.settlement.application.actor.ActorAccessDenied;
import com.liveclass.settlement.domain.sales.RefundAmountExceeded;
import com.liveclass.settlement.domain.settlement.CourseNotFound;
import com.liveclass.settlement.domain.settlement.InvalidSettlementPeriod;
import com.liveclass.settlement.domain.settlement.SaleNotFound;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 모든 실패를 {@link ErrorResponse} 한 가지 모양으로 바꾼다.
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
    ResponseEntity<ErrorResponse> notFound(RuntimeException e, HttpServletRequest request) {
        String code = e instanceof SaleNotFound ? "SALE_NOT_FOUND" : "COURSE_NOT_FOUND";
        return respond(code, e, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(RefundAmountExceeded.class)
    ResponseEntity<ErrorResponse> refundExceeded(RefundAmountExceeded e, HttpServletRequest request) {
        return respond("REFUND_AMOUNT_EXCEEDED", e, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(ActorAccessDenied.class)
    ResponseEntity<ErrorResponse> accessDenied(ActorAccessDenied e, HttpServletRequest request) {
        return respond("ACTOR_ACCESS_DENIED", e, HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(InvalidSettlementPeriod.class)
    ResponseEntity<ErrorResponse> invalidPeriod(InvalidSettlementPeriod e, HttpServletRequest request) {
        return respond("INVALID_SETTLEMENT_PERIOD", e, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * 안 잡으면 Spring이 자체 {@code ProblemDetail} 본문을 내보내 포맷이 갈린다.
     * 여러 필드가 실패하면 첫 위반의 메시지를 쓴다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validationFailed(MethodArgumentNotValidException e,
                                                   HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("validation failed");
        return build("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Task 1의 {@code ActorContextArgumentResolver}가 헤더 오류에 이 예외를 던진다.
     * Task 1 코드를 고치지 않고 여기서 흡수한다. 상태는 예외가 든 값을 그대로 쓴다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> actorHeader(ResponseStatusException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        return build("INVALID_ACTOR_HEADER", e.getReason(), status, request);
    }

    /** 오프셋 없는 시각 등 역직렬화 실패. DTO를 OffsetDateTime으로 둔 이유가 이것이다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException e,
                                            HttpServletRequest request) {
        return build("MALFORMED_REQUEST", "request body is malformed",
                HttpStatus.BAD_REQUEST, request);
    }

    /** from/to 누락. 안 잡으면 "모든 실패가 한 가지 모양"이라는 주장이 거짓이 된다. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException e,
                                                   HttpServletRequest request) {
        return build("MISSING_PARAMETER", e.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    private ResponseEntity<ErrorResponse> respond(String code, RuntimeException e,
                                                  HttpStatus status, HttpServletRequest request) {
        return build(code, e.getMessage(), status, request);
    }

    private ResponseEntity<ErrorResponse> build(String code, String message,
                                                HttpStatus status, HttpServletRequest request) {
        log.warn("request failed: code={}, status={}, path={}, message={}",
                code, status.value(), request.getRequestURI(), message);
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, status.value()));
    }
}
