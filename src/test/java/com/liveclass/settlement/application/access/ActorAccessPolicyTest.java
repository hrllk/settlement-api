package com.liveclass.settlement.application.access;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 인가 판정 네 갈래를 전부 밟는다. 허용 경로는 여기서만 잠긴다. */
class ActorAccessPolicyTest {

    private final ActorAccessPolicy policy = new ActorAccessPolicy();

    private static ActorContext admin() {
        return new ActorContext("admin-1", ActorRole.ADMIN);
    }

    private static ActorContext creator(String id) {
        return new ActorContext(id, ActorRole.CREATOR);
    }

    @Test
    @DisplayName("운영자는 타인의 자원에도 접근한다")
    void adminAccessesOthers() {
        assertThatCode(() -> policy.requireSelfOrAdmin(admin(), "creator-2"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("크리에이터는 본인 자원에 접근한다")
    void creatorAccessesSelf() {
        assertThatCode(() -> policy.requireSelfOrAdmin(creator("creator-1"), "creator-1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("크리에이터가 타인 자원에 접근하면 거부하고 두 식별자를 메시지에 담는다")
    void creatorCannotAccessOthers() {
        assertThatThrownBy(() -> policy.requireSelfOrAdmin(creator("creator-1"), "creator-2"))
                .isInstanceOf(ActorAccessDeniedException.class)
                .hasMessageContaining("creator-1")
                .hasMessageContaining("creator-2");
    }

    @Test
    @DisplayName("운영자 전용 판정은 운영자만 통과시킨다")
    void adminOnly() {
        assertThatCode(() -> policy.requireAdmin(admin())).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireAdmin(creator("creator-1")))
                .isInstanceOf(ActorAccessDeniedException.class)
                .hasMessageContaining("creator-1");
    }
}
