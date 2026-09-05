package com.liveclass.settlement.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

/**
 * {@code Instant}가 아니라 {@code OffsetDateTime}인 이유는 오프셋 강제다.
 * {@code Instant}는 오프셋 없는 값을 UTC로 가정해 조용히 9시간 어긋난다.
 */
public record RegisterSaleRequest(
        @NotBlank String courseId,
        @Positive long amount,
        @NotNull OffsetDateTime paidAt) {
}
