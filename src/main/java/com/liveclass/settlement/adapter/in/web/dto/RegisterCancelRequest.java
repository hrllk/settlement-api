package com.liveclass.settlement.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

/**
 * 금액 검증을 DTO에서 하는 이유는 요청 형식의 문제이기 때문이다. 0원이나 음수
 * 취소는 도메인 규칙 위반이 아니라 애초에 말이 안 되는 입력이다. 도메인 예외를
 * 만들면 예외가 하나 늘고 유스케이스마다 같은 검사를 반복하게 된다.
 */
public record RegisterCancelRequest(
        @Positive long amount,
        @NotNull OffsetDateTime cancelledAt) {
}
