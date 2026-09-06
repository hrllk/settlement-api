package com.liveclass.settlement.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveclass.settlement.application.access.ActorContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 선언 안 한 핸들러는 해석기를 안 거쳐 무방비로 열린다. 컴파일러가 못 잡는 유일한 위험이다. */
@SpringBootTest
@AutoConfigureMockMvc   // MockMvc를 안 쓰지만 붙인다. 컨트롤러 테스트와 컨텍스트를 공유해 기동을 한 번 줄인다.
class ControllerActorGuardTest {

    private static final String APP_PACKAGE = "com.liveclass.settlement";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("모든 컨트롤러 핸들러가 ActorContext를 선언한다")
    void everyHandlerDeclaresActorContext() {
        // Spring 기본 오류 컨트롤러가 섞이면 항상 실패하므로 우리 패키지만 본다.
        List<String> missing = handlerMapping.getHandlerMethods().values().stream()
                .filter(ControllerActorGuardTest::isOurs)
                .filter(handler -> !declaresActorContext(handler))
                .map(HandlerMethod::getShortLogMessage)
                .toList();

        assertThat(missing)
                .as("ActorContext 파라미터가 없는 핸들러는 헤더 검사도 인가 판정도 없이 열린다")
                .isEmpty();
    }

    /** 위 테스트는 대상이 0개여도 통과한다. 패키지 필터가 어긋난 경우를 막는다. */
    @Test
    @DisplayName("가드가 최소 5개 핸들러를 대상으로 삼는다")
    void guardIsNotVacuous() {
        long ours = handlerMapping.getHandlerMethods().values().stream()
                .filter(ControllerActorGuardTest::isOurs)
                .count();

        assertThat(ours)
                .as("Task 4의 3개 + Task 5의 2개. 이보다 적으면 패키지 필터가 어긋난 것이다")
                .isGreaterThanOrEqualTo(5);
    }

    private static boolean isOurs(HandlerMethod handler) {
        return handler.getBeanType().getName().startsWith(APP_PACKAGE);
    }

    private static boolean declaresActorContext(HandlerMethod handler) {
        for (MethodParameter parameter : handler.getMethodParameters()) {
            if (ActorContext.class.equals(parameter.getParameterType())) {
                return true;
            }
        }
        return false;
    }
}
