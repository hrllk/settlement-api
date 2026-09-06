package com.liveclass.settlement.application.access;

/** 신원은 알지만 권한이 없다. 403 — 신원을 모르는 400과 구분한다. */
public class ActorAccessDeniedException extends RuntimeException {

    public ActorAccessDeniedException(String message) {
        super(message);
    }
}
