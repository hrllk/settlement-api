package com.liveclass.settlement.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancelJpaRepository extends JpaRepository<CancelEntity, String> {

    /**
     * 기간 내 취소 전체를 크리에이터와 함께 한 번에 읽는다. 운영자 집계 전용이다.
     * 취소에서 크리에이터까지는 판매와 강의를 거친다. 원본 판매가 조회 창 밖일 수 있어
     * 기간은 {@code cancelledAt}으로만 좁힌다.
     */
    @Query("""
            select c.creatorId as creatorId, x.id as cancelId, x.saleId as saleId,
                   x.amount as amount, x.cancelledAt as cancelledAt
            from CancelEntity x, SaleEntity s, CourseEntity c
            where x.saleId = s.id and s.courseId = c.id
              and x.cancelledAt >= :fromInclusive and x.cancelledAt < :toExclusive
            order by c.creatorId, x.cancelledAt, x.id
            """)
    List<CreatorScopedCancel> findAllByPeriodWithCreator(@Param("fromInclusive") Instant fromInclusive,
                                                         @Param("toExclusive") Instant toExclusive);

    /** 기간은 {@code cancelledAt} 기준. 원본 판매는 조회 창 밖일 수 있다. */
    @Query("""
            select x from CancelEntity x
            where x.saleId in (
                  select s.id from SaleEntity s
                  where s.courseId in (select c.id from CourseEntity c where c.creatorId = :creatorId))
              and x.cancelledAt >= :fromInclusive and x.cancelledAt < :toExclusive
            order by x.cancelledAt, x.id
            """)
    List<CancelEntity> findByCreatorAndPeriod(@Param("creatorId") String creatorId,
                                              @Param("fromInclusive") Instant fromInclusive,
                                              @Param("toExclusive") Instant toExclusive);

    /** 환불 상태 산출용. 시간 조건을 넣지 말 것 — 환불 상태는 기간과 무관하다. */
    List<CancelEntity> findBySaleIdIn(Collection<String> saleIds);

    /** 애그리게이트 적재용. 전부 읽어야 한다 — 부분 적재하면 초과 환불이 통과한다. */
    List<CancelEntity> findBySaleId(String saleId);
}
