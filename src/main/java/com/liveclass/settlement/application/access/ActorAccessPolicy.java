package com.liveclass.settlement.application.access;

import org.springframework.stereotype.Component;

/** 접근 판정만 한다. 컨트롤러가 아니라 유스케이스가 부른다. */
@Component
public class ActorAccessPolicy {

    /** 본인 또는 운영자. 크리에이터별 조회에 붙는다. */
    public void requireSelfOrAdmin(ActorContext actor, String creatorId) {
        if (actor.role() == ActorRole.ADMIN) {
            return;
        }
        if (!actor.actorId().equals(creatorId)) {
            throw new ActorAccessDeniedException(
                    "creator " + actor.actorId() + " cannot access creator " + creatorId);
        }
    }

    /** 운영자 전용. 등록과 운영자 집계에 붙는다. */
    public void requireAdmin(ActorContext actor) {
        if (actor.role() != ActorRole.ADMIN) {
            throw new ActorAccessDeniedException("actor " + actor.actorId() + " is not an admin");
        }
    }
}
