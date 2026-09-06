package com.liveclass.settlement.domain.settlement;

/** 정산 요약. record 표준 생성자가 public이라 파생값 정합성을 compact 생성자가 검증한다. */
public record SettlementSummary(
        long grossSales, int saleCount,
        long refunds, int cancelCount,
        long netSales, long fee, long payout) {

    public SettlementSummary {
        if (netSales != grossSales - refunds) {
            throw new IllegalArgumentException(
                    "netSales must equal grossSales - refunds: " + netSales
                            + " != " + grossSales + " - " + refunds);
        }
        if (payout != netSales - fee) {
            throw new IllegalArgumentException(
                    "payout must equal netSales - fee: " + payout
                            + " != " + netSales + " - " + fee);
        }
    }

    public static SettlementSummary of(long grossSales, int saleCount,
                                       long refunds, int cancelCount,
                                       FeePolicy feePolicy) {
        long netSales = grossSales - refunds;
        long fee = feePolicy.calculate(netSales);
        return new SettlementSummary(
                grossSales, saleCount, refunds, cancelCount, netSales, fee, netSales - fee);
    }
}
