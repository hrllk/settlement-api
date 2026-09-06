package com.liveclass.settlement.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

/**
 * {@code Instant}가 아니라 {@code OffsetDateTime}인 이유는 오프셋 강제다.
 * {@code Instant}는 오프셋 없는 값을 UTC로 가정해 조용히 9시간 어긋난다.
 *
 * 금액 상한이 있어야 한다. {@code @Positive}만 두면 {@code Long.MAX_VALUE}가
 * 들어오고, 그러면 수수료의 {@code netSales * basisPoints}와 기간 합계의 누적이
 * 예외 없이 래핑해 음수 정산이 나간다.
 */
public record RegisterSaleRequest(
        @NotBlank String courseId,
        @Positive @Max(MAX_AMOUNT) long amount,
        @NotNull OffsetDateTime paidAt) {

    /** 10억 원. 강의 한 건의 결제로 가능한 값을 한참 넘고, 합산해도 안전한 자리다. */
    public static final long MAX_AMOUNT = 1_000_000_000L;
}
