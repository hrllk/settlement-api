package com.liveclass.settlement.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

/** 0원·음수 취소는 도메인 규칙 위반이 아니라 형식 오류라 DTO에서 막는다. */
public record RegisterCancelRequest(
        @Positive @Max(RegisterSaleRequest.MAX_AMOUNT) long amount,
        @NotNull OffsetDateTime cancelledAt) {
}
