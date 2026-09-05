package com.liveclass.settlement.application.settlement;

import com.liveclass.settlement.application.actor.ActorAccessPolicy;
import com.liveclass.settlement.application.actor.ActorContext;
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

    private final ActorAccessPolicy accessPolicy;
    private final SettlementQuery query;

    /**
     * 산술이 한 줄도 없다. 계산은 Task 3의 계산기가 소유한다.
     *
     * 연월을 {@code String}으로 받는다. 파싱·검증은 {@link SettlementPeriod}가
     * 소유한다 — 컨트롤러가 {@code YearMonth}로 바인딩하면 Spring이
     * {@code MethodArgumentTypeMismatchException}을 먼저 던져 {@code 2025-13}의
     * 거부가 도메인이 아니라 프레임워크에서 일어난다.
     *
     * 판매도 취소도 없는 달은 계산기가 전 항목 0인 요약을 만든다. 404가 아니다.
     * "정산이 없다"와 "크리에이터가 없다"를 클라이언트가 구분할 수 없게 된다.
     */
    @Transactional(readOnly = true)
    public SettlementSummary settle(ActorContext actor, String creatorId, String yearMonth) {
        accessPolicy.requireSelfOrAdmin(actor, creatorId);

        SettlementPeriod period = SettlementPeriod.ofYearMonth(yearMonth);
        SettlementSummary summary = query.summarize(period, creatorId);

        log.info("monthly settlement: creatorId={}, yearMonth={}, payout={}, actorId={}",
                creatorId, yearMonth, summary.payout(), actor.actorId());
        return summary;
    }
}
