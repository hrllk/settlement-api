package com.liveclass.settlement.domain.settlement;

import java.util.Collection;
import java.util.Objects;

/**
 * 판매 한 건의 환불 상태. 저장하지 않고 취소 금액 합계에서 매번 계산한다.
 *
 * <p><b>기간 필터를 적용하지 않는다.</b> 그 판매에 연결된 모든 취소를 본다.
 * sale-5는 1월 판매이고 취소는 2월 3일인데, 1월 판매 목록을 조회해도
 * 상태는 FULL이어야 한다. 정산 금액 집계만 기간 기준으로 나뉘고
 * 환불 상태는 나뉘지 않는다. 이 클래스의 시그니처에 SettlementPeriod가
 * 없는 것이 그 보장이다.
 */
public enum RefundStatus {

    NONE, PARTIAL, FULL;

    /**
     * 취소 목록에서 해당 판매의 것만 합산해 상태를 구한다.
     * 시각은 보지 않고 saleId만 본다.
     */
    public static RefundStatus of(SaleData sale, Collection<CancelData> cancelsOfSale) {
        Objects.requireNonNull(sale, "sale");
        Objects.requireNonNull(cancelsOfSale, "cancelsOfSale");
        long cancelledTotal = 0;
        for (CancelData cancel : cancelsOfSale) {
            if (cancel.saleId().equals(sale.saleId())) {
                cancelledTotal += cancel.amount();
            }
        }
        return of(sale.amount(), cancelledTotal);
    }

    /**
     * 초과 등록은 Task 4가 거부하지만 Task 4는 나중 태스크다. 지금 초과
     * 입력이 오면 분기가 열린 채 남으므로 {@code >=}로 닫는다.
     */
    public static RefundStatus of(long saleAmount, long cancelledTotal) {
        if (cancelledTotal <= 0) {
            return NONE;
        }
        if (cancelledTotal >= saleAmount) {
            return FULL;
        }
        return PARTIAL;
    }
}
