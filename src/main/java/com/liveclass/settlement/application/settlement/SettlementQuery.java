package com.liveclass.settlement.application.settlement;

import com.liveclass.settlement.application.port.out.SalesQueryPort;
import com.liveclass.settlement.domain.settlement.SaleData;
import com.liveclass.settlement.domain.settlement.CancelData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.liveclass.settlement.domain.settlement.SettlementCalculator;
import com.liveclass.settlement.domain.settlement.SettlementPeriod;
import com.liveclass.settlement.domain.settlement.SettlementSummary;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 월별 조회와 운영자 집계가 이 한 곳을 쓴다. 두 경로의 차이는 기간 하나뿐이어야 한다. */
@Component
@RequiredArgsConstructor
public class SettlementQuery {

    private final SalesQueryPort salesQueryPort;
    private final SettlementCalculator settlementCalculator;

    /** 한 크리에이터. 조회 2회. */
    public SettlementSummary summarize(SettlementPeriod period, String creatorId) {
        Instant from = period.fromInclusive();
        Instant to = period.toExclusive();
        return settlementCalculator.calculate(
                period,
                salesQueryPort.findSales(from, to, creatorId),
                salesQueryPort.findCancels(from, to, creatorId));
    }

    /**
     * 여러 크리에이터. 명수와 무관하게 조회 2회입니다.
     *
     * 크리에이터마다 {@link #summarize}를 부르면 명수만큼 쿼리가 늘어납니다(N+1). 판매와
     * 취소를 각각 한 번에 읽어 크리에이터별로 묶고, 계산은 위와 <b>같은 계산기</b>가 합니다.
     * 조회 방식만 다르고 계산 경로는 하나라는 것이 중요합니다 — 두 API가 같은 기간에서
     * 다른 값을 낼 수 없습니다.
     *
     * 실적이 없는 크리에이터도 빈 리스트로 계산해 전 항목 0인 요약을 받습니다.
     */
    public Map<String, SettlementSummary> summarizeAll(SettlementPeriod period,
                                                       List<String> creatorIds) {
        Instant from = period.fromInclusive();
        Instant to = period.toExclusive();
        Map<String, List<SaleData>> salesByCreator = salesQueryPort.findSalesByCreator(from, to);
        Map<String, List<CancelData>> cancelsByCreator = salesQueryPort.findCancelsByCreator(from, to);

        Map<String, SettlementSummary> result = new LinkedHashMap<>();
        for (String creatorId : creatorIds) {
            result.put(creatorId, settlementCalculator.calculate(
                    period,
                    salesByCreator.getOrDefault(creatorId, List.of()),
                    cancelsByCreator.getOrDefault(creatorId, List.of())));
        }
        return result;
    }
}
