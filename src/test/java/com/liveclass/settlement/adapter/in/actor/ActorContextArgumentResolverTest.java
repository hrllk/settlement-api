package com.liveclass.settlement.adapter.in.actor;

import static com.liveclass.settlement.adapter.in.actor.ActorContextArgumentResolver.ACTOR_ID_HEADER;
import static com.liveclass.settlement.adapter.in.actor.ActorContextArgumentResolver.ACTOR_ROLE_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

class ActorContextArgumentResolverTest {

    private final ActorContextArgumentResolver resolver = new ActorContextArgumentResolver();

    @Test
    @DisplayName("정상 헤더는 ActorContext로 해석된다")
    void resolvesActorContextFromHeaders() {
        assertThat(resolver.supportsParameter(actorParameter())).isTrue();

        ActorContext actor = resolve(requestWith(" creator-1 ", "CREATOR"));

        assertThat(actor).isEqualTo(new ActorContext("creator-1", ActorRole.CREATOR));
    }

    @Test
    @DisplayName("필수 헤더가 없으면 400으로 실패한다")
    void rejectsMissingHeaders() {
        assertThatThrownBy(() -> resolve(requestWith(null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(thrown -> assertThat(((ResponseStatusException) thrown).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("역할값이 대문자 ADMIN·CREATOR가 아니면 400으로 실패한다")
    void rejectsUnknownRole() {
        assertThatThrownBy(() -> resolve(requestWith("creator-1", "creator")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(thrown -> assertThat(((ResponseStatusException) thrown).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private ActorContext resolve(NativeWebRequest webRequest) {
        return resolver.resolveArgument(actorParameter(), null, webRequest, null);
    }

    private static NativeWebRequest requestWith(String actorId, String actorRole) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (actorId != null) {
            request.addHeader(ACTOR_ID_HEADER, actorId);
        }
        if (actorRole != null) {
            request.addHeader(ACTOR_ROLE_HEADER, actorRole);
        }
        return new ServletWebRequest(request);
    }

    private static MethodParameter actorParameter() {
        try {
            Method handler = ActorContextArgumentResolverTest.class
                    .getDeclaredMethod("handler", ActorContext.class);
            return new MethodParameter(handler, 0);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    private void handler(ActorContext actor) {
        // MethodParameter 생성을 위한 시그니처 전용 메서드다.
    }
}
