package com.liveclass.settlement.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 비어 있다. {@code existsById}가 {@code JpaRepository}에서 온다.
 * Task 4의 {@code CourseNotFound} 판정이 쓴다.
 *
 * <p>이게 없으면 Task 4가 없는 강의를 걸러낼 수 없고, FK 제약도 없으므로
 * 판매가 그냥 등록된다. 그 판매는 어떤 크리에이터에도 속하지 않아 정산
 * 조회에서 영원히 안 보이는 유령 데이터가 된다.
 */
public interface CourseJpaRepository extends JpaRepository<CourseEntity, String> {
}
