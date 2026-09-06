package com.liveclass.settlement.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleJpaRepository extends JpaRepository<SaleEntity, String> {

    /**
     * 기간 내 판매 전체를 크리에이터와 함께 한 번에 읽는다. 운영자 집계 전용이다.
     * 크리에이터로 좁히면 명수만큼 쿼리가 늘어난다(N+1).
     */
    @Query("""
            select c.creatorId as creatorId, s.id as saleId,
                   s.amount as amount, s.paidAt as paidAt
            from SaleEntity s, CourseEntity c
            where s.courseId = c.id
              and s.paidAt >= :fromInclusive and s.paidAt < :toExclusive
            order by c.creatorId, s.paidAt, s.id
            """)
    List<CreatorScopedSale> findAllByPeriodWithCreator(@Param("fromInclusive") Instant fromInclusive,
                                                       @Param("toExclusive") Instant toExclusive);

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
