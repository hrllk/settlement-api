package com.liveclass.settlement.application.settlement;

import com.liveclass.settlement.application.access.ActorAccessPolicy;
import com.liveclass.settlement.application.access.ActorContext;
import com.liveclass.settlement.application.port.out.SalesQueryPort;
import com.liveclass.settlement.domain.settlement.SettlementPeriod;
import com.liveclass.settlement.domain.settlement.SettlementSummary;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSettlementUseCase {

    private final ActorAccessPolicy actorAccessPolicy;
    private final SalesQueryPort salesQueryPort;
    private final SettlementQuery settlementQuery;

    /** 기간 전체를 단일 구간으로 계산한다. 월별 합과 다르다 — 근거는 README. */
    @Transactional(readOnly = true)
    public AdminSettlement aggregate(ActorContext actor, String from, String to) {
        actorAccessPolicy.requireAdmin(actor);

        SettlementPeriod period = SettlementPeriod.ofDateRange(from, to);

        List<CreatorPayout> creators = new ArrayList<>();
        long totalPayout = 0;
        for (String creatorId : salesQueryPort.findAllCreatorIds()) {   // id 오름차순
            SettlementSummary summary = settlementQuery.summarize(period, creatorId);
            creators.add(new CreatorPayout(creatorId, summary));
            totalPayout += summary.payout();
        }

        log.info("admin settlement: from={}, to={}, creators={}, totalPayout={}, actorId={}",
                from, to, creators.size(), totalPayout, actor.actorId());
        return new AdminSettlement(List.copyOf(creators), totalPayout);
    }
}
