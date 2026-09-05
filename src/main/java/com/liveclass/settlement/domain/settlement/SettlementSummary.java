package com.liveclass.settlement.domain.settlement;

/**
 * 한 크리에이터의 한 정산 기간 요약. 금액은 전부 원 단위다.
 *
 * <pre>
 *   netSales = grossSales - refunds
 *   fee      = feePolicy.calculate(netSales)   // netSales <= 0 이면 0
 *   payout   = netSales - fee                  // 음수면 크리에이터가 돌려줄 금액
 *
 *   판매는 paidAt, 환불은 cancelledAt 기준으로 귀속 기간을 판단한다.
 *   금액뿐 아니라 건수도 같은 기준을 따른다.
 * </pre>
 *
 * <p>파생값 세 개가 서로 어긋난 인스턴스는 존재할 수 없다. record의 표준
 * 생성자가 public이라 {@link #of} 팩토리만으로는 막지 못하므로 compact
 * 생성자에서 검증한다.
 */
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
