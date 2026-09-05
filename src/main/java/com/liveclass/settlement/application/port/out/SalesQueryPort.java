package com.liveclass.settlement.application.port.out;

import com.liveclass.settlement.domain.settlement.CancelData;
import com.liveclass.settlement.domain.settlement.SaleData;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 정산 계산에 필요한 원본 자료를 가져온다. 집계된 금액이 아니라 원본
 * 판매·취소 목록을 반환하고, 집계는 전부 SettlementCalculator가 한다.
 *
 * <p><b>어떤 메서드도 null을 반환하지 않는다.</b> 결과가 없으면 빈 리스트다.
 * 계산기는 null 방어를 하지 않으며 이 계약 위반은 구현체의 결함이다.
 *
 * <p>시간 인자는 Instant다. SettlementPeriod가 KST를 이미 Instant 구간으로
 * 바꿔 주므로 이 포트와 어댑터는 시간대를 모른다.
 *
 * <p>구현체는 Task 2가 만든다.
 */
public interface SalesQueryPort {

    /** 기간 내 결제된 판매. creatorId는 필수다. */
    List<SaleData> findSales(Instant fromInclusive, Instant toExclusive, String creatorId);

    /**
     * 기간 내 취소된 건. creatorId는 필수다.
     *
     * <p>취소의 원본 판매가 조회 창 밖일 수 있다. cancel-3이 그 경우다
     * (2월 취소, 1월 판매). 크리에이터로 좁히려면 취소에서 판매로 조인해야 한다.
     */
    List<CancelData> findCancels(Instant fromInclusive, Instant toExclusive, String creatorId);

    /**
     * 주어진 판매들에 연결된 모든 취소. 환불 상태 전용이다.
     *
     * <p>환불 상태는 기간 필터를 적용하지 않으므로 cancelledAt 창 조회로는
     * 산출할 수 없다. 이 메서드가 없으면 1월 판매 목록에서 sale-5가
     * FULL이 아니라 NONE으로 나온다.
     *
     * <p>빈 컬렉션이 오면 빈 리스트를 반환한다. {@code IN ()}를 생성하지 않는다.
     */
    List<CancelData> findCancelsBySaleIds(Collection<String> saleIds);

    /**
     * 전체 크리에이터 식별자.
     *
     * <p>운영자 기간 집계에서 판매도 취소도 없는 크리에이터를 0원으로
     * 넣으려면 판매·취소 자료만으로는 존재를 알 수 없다.
     */
    List<String> findAllCreatorIds();

    /**
     * 판매 목록 조회용. 결과 없으면 빈 리스트. {@code paidAt} 오름차순.
     *
     * <p>{@link #findSales}와 인자가 같고 반환 모델만 다르다. 포트를 정산용과
     * 판매용으로 나누지 않는 이유가 이것이다 -- 나누면 같은 SQL을 감싸는 껍데기가
     * 둘이 된다. 읽기 모델이 둘인 것은 필요가 실제로 다르기 때문이다.
     */
    List<SaleRecord> findSalesForListing(Instant fromInclusive, Instant toExclusive, String creatorId);

    /**
     * 강의 존재 여부.
     *
     * <p>강의는 판매 애그리게이트 밖이지만 이 하나 때문에 포트를 더 만들지 않는다.
     * 3시간 예산의 판단이며 README에 남긴다.
     */
    boolean courseExists(String courseId);
}
