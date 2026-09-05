package com.liveclass.settlement.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 이름에 {@code Jpa}가 붙는 이유는 Task 4가 {@code domain.sales}에
 * {@code SaleRepository}를 두기 때문이다. 도메인이 깨끗한 이름을 갖고 인프라가
 * 접미사를 받는다. 네 리포지토리 전부 같은 규칙을 따른다.
 */
public interface SaleJpaRepository extends JpaRepository<SaleEntity, String> {

    /**
     * 기간 내 결제된 판매를 크리에이터로 좁혀 조회한다.
     *
     * <p>반열린 구간을 쿼리에 직접 쓴다. {@code between}은 양끝이 닫혀 종료
     * 경계가 포함되므로 쓰지 않는다. Task 3이 반열린 구간을 핵심 설계 판단으로
     * 잡고 경계 테스트 3방향으로 잠갔는데 여기서 무너뜨리면 안 된다.
     *
     * <p>엔티티에 연관 매핑이 없어 JPQL 조인 경로가 없으므로 서브쿼리로
     * 표현한다. 어떤 테이블을 거치는지 그대로 보인다.
     *
     * <p>정렬을 고정한다. 정산 금액은 순서와 무관하지만 판매 목록 응답은
     * 순서가 곧 결과라, 정렬을 안 주면 같은 요청이 실행마다 다른 순서로 나간다.
     * {@code id} 보조 정렬은 같은 시각의 두 행을 가른다.
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
