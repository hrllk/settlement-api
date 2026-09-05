package com.liveclass.settlement.adapter.in.actor;

import java.util.Optional;

/**
 * 과제용 액터 역할. 실제 인증 체계가 아니라 요청 헤더로 전달되는 신원 표기다.
 */
public enum ActorRole {

    ADMIN,
    CREATOR;

    /**
     * 대문자 이름과 정확히 일치할 때만 역할로 인정한다.
     * {@code admin} 같은 소문자는 대문자로 바꾸지 않고 빈 값을 돌려준다.
     */
    public static Optional<ActorRole> parse(String value) {
        for (ActorRole role : values()) {
            if (role.name().equals(value)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
