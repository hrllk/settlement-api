package com.liveclass.settlement.adapter.in.web.dto;

import com.liveclass.settlement.domain.settlement.SettlementSummary;

/**
 * 파생값 {@code netSales}·{@code fee}·{@code payout}도 내보낸다. 클라이언트가 다시
 * 계산하면 갈린다 — {@code fee}는 순 판매액이 음수면 0으로 막힌다.
 *
 * <p>판매 건수와 취소 건수가 따로인 이유는 이중 집계 기준 때문이다. 합치면
 * creator-2의 2025-02(판매 0, 취소 1)가 "1건"으로 보인다.
 */
public record MonthlySettlementResponse(
        String creatorId, String yearMonth,
        long grossSales, int saleCount,
        long refunds, int cancelCount,
        long netSales, long fee, long payout) {

    public static MonthlySettlementResponse of(String creatorId, String yearMonth,
                                               SettlementSummary s) {
        return new MonthlySettlementResponse(creatorId, yearMonth,
                s.grossSales(), s.saleCount(), s.refunds(), s.cancelCount(),
                s.netSales(), s.fee(), s.payout());
    }
}
