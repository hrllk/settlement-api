package com.liveclass.settlement.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleJpaRepository extends JpaRepository<SaleEntity, String> {

    /** 반열린 구간이라 {@code between}을 쓰지 않는다. 정렬은 응답 순서 고정용이다. */
    @Query("""
            select s from SaleEntity s
            where s.courseId in (select c.id from CourseEntity c where c.creatorId = :creatorId)
              and s.paidAt >= :fromInclusive and s.paidAt < :toExclusive
            order by s.paidAt, s.id
            """)
    List<SaleEntity> findByCreatorAndPeriod(@Param("creatorId") String creatorId,
                                            @Param("fromInclusive") Instant fromInclusive,
                                            @Param("toExclusive") Instant toExclusive);
}
