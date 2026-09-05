package com.liveclass.settlement.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

/**
 * 시각이 {@code Instant}가 아니라 {@code OffsetDateTime}인 이유는 오프셋 강제다.
 *
 * <p>Jackson은 {@code Instant} 필드에 {@code "2025-03-05T10:00:00"}처럼 오프셋
 * 없는 값이 오면 UTC로 가정해 조용히 파싱한다. KST 10시로 보낸 요청이 UTC
 * 10시(KST 19시)로 저장되고 아무 오류 없이 9시간 어긋난 정산이 나온다.
 * 경계에 걸린 값이면 귀속 월까지 바뀐다.
 *
 * <p>{@code OffsetDateTime}은 오프셋이 없으면 파싱에 실패한다.
 */
public record RegisterSaleRequest(
        @NotBlank String courseId,
        @Positive long amount,
        @NotNull OffsetDateTime paidAt) {
}
