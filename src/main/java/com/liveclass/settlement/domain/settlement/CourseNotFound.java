package com.liveclass.settlement.domain.settlement;

/**
 * 없는 강의를 참조했다. 전역 예외 처리기가 404로 변환한다.
 *
 * <p>Task 2가 FK 제약을 걸지 않았으므로 없는 {@code courseId}로도 판매 행이
 * 그냥 들어간다. 그러면 그 판매는 어떤 크리에이터에도 속하지 않아 정산 조회에서
 * 영원히 안 보이는 유령 데이터가 된다. 등록 시점에 막아야 한다.
 */
public class CourseNotFound extends RuntimeException {

    public CourseNotFound(String courseId) {
        super("course not found: " + courseId);
    }
}
