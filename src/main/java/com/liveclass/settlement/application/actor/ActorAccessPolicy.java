package com.liveclass.settlement.application.actor;

import org.springframework.stereotype.Component;

/**
 * 접근 판정만 한다. 어느 엔드포인트에 무엇이 붙는지는 역할 매트릭스가 정한다.
 *
 * <p>과제용 신원 표기라 실제 인증이 아니다. {@code X-Actor-Role} 헤더는 누구나
 * 바꿀 수 있다. 이 한계를 README에 명시한다.
 *
 * <p>판정을 컨트롤러가 아니라 유스케이스가 부른다. 컨트롤러에 두면 Task 4와
 * Task 5가 같은 호출을 각자 복제하고, 유스케이스를 직접 테스트할 때 경계가
 * 빠진다.
 */
@Component
public class ActorAccessPolicy {

    /** 본인 또는 운영자. 크리에이터별 조회에 붙는다. */
    public void requireSelfOrAdmin(ActorContext actor, String creatorId) {
        if (actor.role() == ActorRole.ADMIN) {
            return;
        }
        if (!actor.actorId().equals(creatorId)) {
            throw new ActorAccessDenied(
                    "creator " + actor.actorId() + " cannot access creator " + creatorId);
        }
    }

    /** 운영자 전용. 등록과 운영자 집계에 붙는다. */
    public void requireAdmin(ActorContext actor) {
        if (actor.role() != ActorRole.ADMIN) {
            throw new ActorAccessDenied("actor " + actor.actorId() + " is not an admin");
        }
    }
}
