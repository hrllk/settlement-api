package com.liveclass.settlement.domain.sales;

/**
 * 없는 강의를 참조했다. 전역 예외 처리기가 404로 변환한다.
 *
 * FK 제약이 없으므로 등록 시점에 막지 않으면 유령 판매 행이 남는다.
 */
public class CourseNotFoundException extends RuntimeException {

    public CourseNotFoundException(String courseId) {
        super("course not found: " + courseId);
    }
}
