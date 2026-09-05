package com.liveclass.settlement.domain.sales;

import java.util.Optional;

/**
 * 애그리게이트를 통째로 주고받는다. 목록 조회는 이걸 쓰지 않는다 — N+1이 된다.
 */
public interface SaleRepository {

    /** 취소까지 온전히 적재해야 한다. 부분 적재하면 초과 환불이 통과한다. */
    Optional<Sale> findById(String saleId);

    /** 신규 판매와 새로 추가된 취소를 반영한다. */
    void save(Sale sale);
}
