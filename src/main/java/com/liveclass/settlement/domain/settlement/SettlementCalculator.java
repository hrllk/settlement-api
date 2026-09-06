package com.liveclass.settlement.domain.settlement;

import java.util.List;
import java.util.Objects;

/** 판매는 {@code paidAt}, 취소는 {@code cancelledAt}으로 집계한다. 포트를 모르는 순수 함수다. */
public final class SettlementCalculator {

    private final FeePolicy feePolicy;

    public SettlementCalculator(FeePolicy feePolicy) {
        this.feePolicy = Objects.requireNonNull(feePolicy, "feePolicy");
    }

    public SettlementSummary calculate(SettlementPeriod period,
                                       List<SaleData> sales,
                                       List<CancelData> cancels) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(sales, "sales");
        Objects.requireNonNull(cancels, "cancels");

        long grossSales = 0;
        int saleCount = 0;
        for (SaleData sale : sales) {
            if (period.contains(sale.paidAt())) {
                grossSales += sale.amount();
                saleCount++;
            }
        }

        long refunds = 0;
        int cancelCount = 0;
        for (CancelData cancel : cancels) {
            if (period.contains(cancel.cancelledAt())) {
                refunds += cancel.amount();
                cancelCount++;
            }
        }

        return SettlementSummary.of(grossSales, saleCount, refunds, cancelCount, feePolicy);
    }
}
