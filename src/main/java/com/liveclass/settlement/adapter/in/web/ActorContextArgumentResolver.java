package com.liveclass.settlement.adapter.in.web;

import com.liveclass.settlement.application.access.ActorContext;
import com.liveclass.settlement.application.access.ActorRole;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code X-Actor-Id}와 {@code X-Actor-Role} 헤더를 {@link ActorContext}로 바꾼다.
 * 헤더가 없거나 형식이 어긋나면 400으로 실패시킨다.
 *
 * <p><b>필터가 아니다. 패키지를 {@code filter}로 바꾸지 말 것.</b> 필터는 모든
 * 요청을 거치지만 이건 {@code HandlerMethodArgumentResolver}라 핸들러가
 * {@link ActorContext} 파라미터를 선언해야만 동작한다. 그 차이가 이 프로젝트에서
 * 컴파일러가 잡아주지 않는 유일한 구조적 위험이고, {@code ControllerActorGuardTest}가
 * 존재하는 이유다. 이름이 {@code filter}면 읽는 사람이 "모든 요청이 검사된다"고
 * 정반대로 믿는다.
 */
public class ActorContextArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String ACTOR_ID_HEADER = "X-Actor-Id";
    public static final String ACTOR_ROLE_HEADER = "X-Actor-Role";

    private static final int ACTOR_ID_MAX_LENGTH = 100;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return ActorContext.class.equals(parameter.getParameterType());
    }

    @Override
    public ActorContext resolveArgument(MethodParameter parameter,
                                        ModelAndViewContainer mavContainer,
                                        NativeWebRequest webRequest,
                                        WebDataBinderFactory binderFactory) {
        return new ActorContext(readActorId(webRequest), readActorRole(webRequest));
    }

    private String readActorId(NativeWebRequest webRequest) {
        String rawActorId = webRequest.getHeader(ACTOR_ID_HEADER);
        if (rawActorId == null) {
            throw badRequest(ACTOR_ID_HEADER + " 헤더가 필요합니다.");
        }
        String actorId = rawActorId.strip();
        if (actorId.isEmpty() || actorId.length() > ACTOR_ID_MAX_LENGTH) {
            throw badRequest(ACTOR_ID_HEADER + " 헤더는 1~" + ACTOR_ID_MAX_LENGTH + "자여야 합니다.");
        }
        return actorId;
    }

    private ActorRole readActorRole(NativeWebRequest webRequest) {
        String rawActorRole = webRequest.getHeader(ACTOR_ROLE_HEADER);
        if (rawActorRole == null) {
            throw badRequest(ACTOR_ROLE_HEADER + " 헤더가 필요합니다.");
        }
        return ActorRole.parse(rawActorRole.strip())
                .orElseThrow(() -> badRequest(ACTOR_ROLE_HEADER + " 헤더는 ADMIN 또는 CREATOR여야 합니다."));
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
