package com.liveclass.settlement.application.access;

import java.util.Optional;

/** 과제용 액터 역할. 실제 인증이 아니라 요청 헤더로 오는 신원 표기다. */
public enum ActorRole {

    ADMIN,
    CREATOR;

    /** 대문자와 정확히 일치할 때만 인정한다. 소문자를 올려주지 않는다. */
    public static Optional<ActorRole> parse(String value) {
        for (ActorRole role : values()) {
            if (role.name().equals(value)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
