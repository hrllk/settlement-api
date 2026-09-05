package com.liveclass.settlement.domain.sales;

import java.util.Optional;

/**
 * 도메인 인터페이스다. 애그리게이트를 통째로 주고받는다.
 *
 * <p>{@code domain}에 두는 이유는 반환 타입이 도메인 타입이고 시그니처에 영속성
 * 어휘가 없기 때문이다. 구현은 {@code adapter/out/persistence}에 있고 도메인은
 * 그 존재를 모른다.
 *
 * <p>읽기는 이 인터페이스를 쓰지 않는다. 판매 목록은
 * {@code SalesQueryPort.findSalesForListing}이 값 모델을 돌려준다. 애그리게이트를
 * N개 로딩하면 각각 자기 취소를 딸고 와 N+1이 된다.
 */
public interface SaleRepository {

    /**
     * 취소까지 함께 적재한다. 없으면 빈 Optional. null을 반환하지 않는다.
     *
     * <p>부분 적재하면 {@link Sale#cancelledTotal()}이 실제보다 작게 나와 초과
     * 환불이 통과한다. 애그리게이트는 불변식을 지키는 단위이므로 온전해야 한다.
     */
    Optional<Sale> findById(String saleId);

    /** 신규 판매와 새로 추가된 취소를 반영한다. */
    void save(Sale sale);
}
