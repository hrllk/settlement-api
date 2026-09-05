package com.liveclass.settlement.application.actor;

/**
 * 신원은 알지만 권한이 없다. 전역 예외 처리기가 403으로 변환한다.
 * 신원을 모르는 헤더 누락·형식 오류(400)와 구분한다.
 */
public class ActorAccessDenied extends RuntimeException {

    public ActorAccessDenied(String message) {
        super(message);
    }
}
