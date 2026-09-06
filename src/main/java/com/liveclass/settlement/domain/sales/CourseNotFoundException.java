package com.liveclass.settlement.domain.sales;

/** 없는 강의 참조. 404 — FK가 없어 여기서 막지 않으면 유령 판매 행이 남는다. */
public class CourseNotFoundException extends RuntimeException {

    public CourseNotFoundException(String courseId) {
        super("course not found: " + courseId);
    }
}
