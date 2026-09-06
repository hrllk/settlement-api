package com.liveclass.settlement.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

/** {@code OffsetDateTime}이라야 오프셋이 강제된다. 금액 상한도 필요하다 — 누적이 래핑한다. */
public record RegisterSaleRequest(
        @NotBlank String courseId,
        @NotBlank String studentId,
        @Positive @Max(MAX_AMOUNT) long amount,
        @NotNull OffsetDateTime paidAt) {

    /** 10억 원. 강의 한 건의 결제로 가능한 값을 한참 넘고, 합산해도 안전한 자리다. */
    public static final long MAX_AMOUNT = 1_000_000_000L;
}
