package com.liveclass.settlement.application.actor;

/**
 * 신원은 알지만 권한이 없다. 전역 예외 처리기가 403으로 변환한다.
 *
 * <p>헤더 누락·형식 오류의 400과 구분한다. 그쪽은 신원을 <b>모르는</b> 것이고
 * 이쪽은 신원을 <b>알고 거부</b>하는 것이다. 둘을 같은 코드로 내면 클라이언트가
 * 재시도해야 할지 포기해야 할지 판단할 수 없다.
 */
public class ActorAccessDenied extends RuntimeException {

    public ActorAccessDenied(String message) {
        super(message);
    }
}
