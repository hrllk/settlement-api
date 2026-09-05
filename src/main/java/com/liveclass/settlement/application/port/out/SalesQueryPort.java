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
}
