package com.liveclass.settlement.application.settlement;

import com.liveclass.settlement.application.actor.ActorAccessPolicy;
import com.liveclass.settlement.application.actor.ActorContext;
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

    private final ActorAccessPolicy accessPolicy;
    private final SalesQueryPort queryPort;
    private final SettlementQuery query;

    /**
     * <b>기간 전체를 단일 구간으로 계산한다. 월별로 계산해 더하지 않는다.</b>
     * creator-2의 1~3월이 이 방식으로 48,000, 월별 합으로는 36,000이다. 음수 월에
     * 수수료 0원 제한이 걸려 1월에 뗀 수수료가 상쇄되지 않기 때문이다. 근거는 README.
     *
     * <p>{@code findAllCreatorIds}는 실적 0인 크리에이터 때문에 필요하다. 판매·취소
     * 자료만 훑으면 존재를 알 방법이 없어 목록에서 통째로 빠진다.
     *
     * <p>크리에이터마다 포트를 두 번 부른다(N+1). 3명이라 실측 차이가 0이고, 포트
     * 계약을 단순하게 유지하려는 선택이다.
     */
    @Transactional(readOnly = true)
    public AdminSettlement aggregate(ActorContext actor, String from, String to) {
        accessPolicy.requireAdmin(actor);

        SettlementPeriod period = SettlementPeriod.ofDateRange(from, to);

        List<CreatorPayout> creators = new ArrayList<>();
        long totalPayout = 0;
        for (String creatorId : queryPort.findAllCreatorIds()) {   // id 오름차순
            SettlementSummary summary = query.summarize(period, creatorId);
            creators.add(new CreatorPayout(creatorId, summary));
            totalPayout += summary.payout();
        }

        log.info("admin settlement: from={}, to={}, creators={}, totalPayout={}, actorId={}",
                from, to, creators.size(), totalPayout, actor.actorId());
        return new AdminSettlement(List.copyOf(creators), totalPayout);
    }
}
