package com.liveclass.settlement.adapter.in.web.dto;

import com.liveclass.settlement.domain.settlement.SettlementSummary;

/** 운영자 목록의 한 행. 월별 응답과 같은 필드에 {@code yearMonth}만 없다. */
public record CreatorPayoutItem(
        String creatorId,
        long grossSales, int saleCount,
        long refunds, int cancelCount,
        long netSales, long fee, long payout) {

    public static CreatorPayoutItem of(String creatorId, SettlementSummary s) {
        return new CreatorPayoutItem(creatorId,
                s.grossSales(), s.saleCount(), s.refunds(), s.cancelCount(),
                s.netSales(), s.fee(), s.payout());
    }
}
