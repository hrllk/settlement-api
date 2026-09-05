package com.liveclass.settlement.application.settlement;

import com.liveclass.settlement.application.port.out.SalesQueryPort;
import com.liveclass.settlement.domain.settlement.SettlementCalculator;
import com.liveclass.settlement.domain.settlement.SettlementPeriod;
import com.liveclass.settlement.domain.settlement.SettlementSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 기간 + 크리에이터 → 정산 요약. 월별 조회와 운영자 집계가 <b>이 한 곳</b>을 쓴다.
 *
 * 세 줄을 아끼려는 것이 아니다. 이 프로젝트에서 가장 설명하기 어려운 동작
 * (월별 합 ≠ 기간 집계)이 이 경로에 걸려 있어서, 두 유스케이스의 차이가
 * <b>기간 하나뿐</b>이라는 것을 구조로 보장해야 한다. 복제해 두면 조회 순서나
 * 기간 처리를 한쪽만 고쳐 같은 크리에이터의 두 응답이 조용히 갈린다.
 *
 * 판매는 {@code paidAt}, 취소는 {@code cancelledAt} 기준이다. 포트 메서드 둘이
 * 서로 다른 컬럼으로 같은 구간을 자른다.
 */
@Component
@RequiredArgsConstructor
public class SettlementQuery {

    private final SalesQueryPort queryPort;
    private final SettlementCalculator calculator;

    public SettlementSummary summarize(SettlementPeriod period, String creatorId) {
        return calculator.calculate(
                period,
                queryPort.findSales(period.fromInclusive(), period.toExclusive(), creatorId),
                queryPort.findCancels(period.fromInclusive(), period.toExclusive(), creatorId));
    }
}
