package com.liveclass.settlement.application.access;

/** 요청 하나의 액터 신원. 컨트롤러 매개변수로 주입된다. */
public record ActorContext(String actorId, ActorRole role) {
}
