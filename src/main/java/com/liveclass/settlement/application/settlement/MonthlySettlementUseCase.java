package com.liveclass.settlement.application.settlement;

import com.liveclass.settlement.application.access.ActorAccessPolicy;
import com.liveclass.settlement.application.access.ActorContext;
import com.liveclass.settlement.domain.settlement.SettlementPeriod;
import com.liveclass.settlement.domain.settlement.SettlementSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlySettlementUseCase {

    private final ActorAccessPolicy actorAccessPolicy;
    private final SettlementQuery settlementQuery;

    /**
     * 연월을 {@code String}으로 받는다. {@code @PathVariable YearMonth}로 바인딩하면
     * Spring이 먼저 거부해 {@code 2025-13}의 오류 코드가 달라진다.
     *
     * <p>판매도 취소도 없는 달은 전 항목 0인 요약이 된다. 404가 아니다.
     */
    @Transactional(readOnly = true)
    public SettlementSummary settle(ActorContext actor, String creatorId, String yearMonth) {
        actorAccessPolicy.requireSelfOrAdmin(actor, creatorId);

        SettlementPeriod period = SettlementPeriod.ofYearMonth(yearMonth);
        SettlementSummary summary = settlementQuery.summarize(period, creatorId);

        log.info("monthly settlement: creatorId={}, yearMonth={}, payout={}, actorId={}",
                creatorId, yearMonth, summary.payout(), actor.actorId());
        return summary;
    }
}
