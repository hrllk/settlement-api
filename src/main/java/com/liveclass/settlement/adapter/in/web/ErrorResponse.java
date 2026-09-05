package com.liveclass.settlement.adapter.in.web;

/**
 * 모든 실패가 이 한 가지 모양으로 나간다.
 *
 * <p>{@code code}는 예외 이름을 {@code UPPER_SNAKE_CASE}로 바꾼 값이다.
 * 프레임워크 예외 넷은 예외 이름이 사용자에게 의미가 없어 따로 정한다 --
 * {@code VALIDATION_FAILED}, {@code INVALID_ACTOR_HEADER},
 * {@code MALFORMED_REQUEST}, {@code MISSING_PARAMETER}.
 */
public record ErrorResponse(String code, String message, int status) {
}
