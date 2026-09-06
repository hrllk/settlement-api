package com.liveclass.settlement.domain.settlement;

import java.util.Collection;
import java.util.Objects;

/** 취소 합계에서 매번 계산한다. 시그니처에 기간이 없는 것이 무필터의 보장이다. */
public enum RefundStatus {

    NONE, PARTIAL, FULL;

    /** 취소 목록에서 saleId가 일치하는 것만 합산한다. 시각은 보지 않는다. */
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

    /** 초과 등록은 거부되지만, 방어적으로 {@code >=}로 닫는다. */
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
