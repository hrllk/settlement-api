package com.liveclass.settlement.adapter.in.web.dto;

import com.liveclass.settlement.domain.settlement.SettlementSummary;

/** 파생값과 두 건수를 따로 내보낸다. 클라이언트가 다시 계산하면 갈린다. */
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
