package com.liveclass.settlement.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancelJpaRepository extends JpaRepository<CancelEntity, String> {

    /**
     * 기간 내 취소를 크리에이터로 좁혀 조회한다.
     *
     * <p>두 조건의 기준 컬럼이 다르다. 기간은 {@code cancelledAt}으로 좁히고
     * 크리에이터는 판매를 거쳐 찾는다. 원본 판매가 조회 창 밖일 수 있기
     * 때문이다 — cancel-3은 2월 취소인데 원본 sale-5는 1월 판매다.
     */
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

    /**
     * 환불 상태 산출용. <b>시간 조건이 없다.</b>
     *
     * <p>환불 상태는 기간 필터를 적용하지 않는다. 여기에 시간 조건을 넣으면
     * 1월 판매 목록에서 sale-5가 {@code FULL}이 아니라 {@code NONE}으로 나온다.
     */
    List<CancelEntity> findBySaleIdIn(Collection<String> saleIds);

    /**
     * Task 4 전용. {@code SaleRepositoryJpaAdapter.findById}가 {@code Sale}
     * 애그리게이트를 적재할 때 그 판매의 취소를 전부 읽는다. 부분 적재하면
     * {@code cancelledTotal()}이 거짓말을 해 초과 환불이 통과한다.
     *
     * <p>Task 2는 선언만 하고 쓰지 않는다.
     */
    List<CancelEntity> findBySaleId(String saleId);
}
