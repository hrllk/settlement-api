package com.liveclass.settlement.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** existsById만 쓴다. 없는 강의로 판매가 등록되는 것을 막는 용도. */
public interface CourseJpaRepository extends JpaRepository<CourseEntity, String> {
}
