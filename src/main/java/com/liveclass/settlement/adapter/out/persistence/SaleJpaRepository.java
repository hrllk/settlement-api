package com.liveclass.settlement.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleJpaRepository extends JpaRepository<SaleEntity, String> {

    /**
     * 기간 내 결제된 판매를 크리에이터로 좁혀 조회한다.
     *
     * 반열린 구간이라 {@code between}(양끝 닫힘)을 쓰지 않는다.
     * 정렬은 응답 순서를 고정하기 위해 필수다.
     */
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
