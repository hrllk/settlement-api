package com.liveclass.settlement.adapter.in.web.dto;

import com.liveclass.settlement.domain.settlement.SettlementSummary;

/**
 * 금액·건수 7개를 전부 내보낸다. 클라이언트가 {@code grossSales - refunds}를 다시
 * 계산하게 두면 음수 처리에서 서버와 갈린다 — {@code fee}는 순 판매액이 음수면
 * 0으로 막히므로 20%를 곱해서는 절대 나오지 않는다.
 *
 * 판매 건수와 취소 건수가 따로인 이유는 이중 집계 기준 때문이다. creator-2의
 * 2025-02는 판매 0건, 취소 1건이다. 하나로 합치면 이 달이 "1건"으로 보인다.
 *
 * {@code payout}은 음수를 그대로 담는다. 크리에이터가 돌려줄 금액이라는 뜻이다.
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
