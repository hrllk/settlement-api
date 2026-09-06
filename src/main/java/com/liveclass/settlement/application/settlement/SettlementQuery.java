package com.liveclass.settlement.application.settlement;

import com.liveclass.settlement.application.port.out.SalesQueryPort;
import com.liveclass.settlement.domain.settlement.SettlementCalculator;
import com.liveclass.settlement.domain.settlement.SettlementPeriod;
import com.liveclass.settlement.domain.settlement.SettlementSummary;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 기간 + 크리에이터 → 정산 요약. 월별 조회와 운영자 집계가 이 한 곳을 쓴다.
 *
 * <p>복제해 두면 조회 순서나 기간 처리를 한쪽만 고쳐 같은 크리에이터의 두 응답이
 * 조용히 갈린다. <b>두 경로의 차이는 기간 하나뿐이어야 한다.</b>
 *
 * <p>판매는 {@code paidAt}, 취소는 {@code cancelledAt} 기준이다.
 */
@Component
@RequiredArgsConstructor
public class SettlementQuery {

    private final SalesQueryPort salesQueryPort;
    private final SettlementCalculator settlementCalculator;

    public SettlementSummary summarize(SettlementPeriod period, String creatorId) {
        Instant from = period.fromInclusive();
        Instant to = period.toExclusive();
        return settlementCalculator.calculate(
                period,
                salesQueryPort.findSales(from, to, creatorId),
                salesQueryPort.findCancels(from, to, creatorId));
    }
}
