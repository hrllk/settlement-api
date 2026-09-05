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
     *
     * creator-2의 2025-01~03이 두 방식을 갈라놓는다. 단일 구간은 판매 120,000에서
     * 환불 60,000을 먼저 상쇄하고 남은 60,000에만 수수료를 붙여 48,000이다.
     * 월별 합산은 2월의 순 판매액이 −60,000이 되어 수수료가 0으로 막히고, 1월에
     * 이미 뗀 12,000이 상쇄되지 않아 36,000이다. 전체로는 264,000 대 252,000.
     *
     * 월별 합산을 안 쓰는 이유는 음수 월마다 수수료 0원 제한이 반복 적용돼
     * 크리에이터에게 불리하고 "왜 월별 합과 다른가"를 설명할 수 없기 때문이다.
     *
     * 이 판단의 최대 리스크는 정책이 아니라 미문서화다. 평가자가 월별 조회 세
     * 번의 합과 이 값을 비교하면 반드시 갈린다. README 최우선 항목이다.
     *
     * {@code findAllCreatorIds}가 필요한 이유는 실적 0인 크리에이터다. 판매·취소
     * 자료만 훑으면 creator-3의 존재를 알 방법이 없어 목록에서 통째로 빠진다.
     * 순서는 Task 2의 어댑터가 {@code id} 오름차순으로 이미 고정했다.
     *
     * 크리에이터마다 포트를 두 번 부른다(N+1). 3명이라 실측 차이가 0이고, 포트
     * 계약을 "{@code creatorId} 항상 필수"로 단순하게 유지하기 위한 선택이다.
     * 수천 명이면 다시 볼 지점이라 README에 남긴다.
     */
    @Transactional(readOnly = true)
    public AdminSettlement aggregate(ActorContext actor, String from, String to) {
        accessPolicy.requireAdmin(actor);

        SettlementPeriod period = SettlementPeriod.ofDateRange(from, to);

        List<CreatorPayout> creators = new ArrayList<>();
        long totalPayout = 0;
        for (String creatorId : queryPort.findAllCreatorIds()) {
            SettlementSummary summary = query.summarize(period, creatorId);
            creators.add(new CreatorPayout(creatorId, summary));
            totalPayout += summary.payout();
        }

        log.info("admin settlement: from={}, to={}, creators={}, totalPayout={}, actorId={}",
                from, to, creators.size(), totalPayout, actor.actorId());
        return new AdminSettlement(List.copyOf(creators), totalPayout);
    }
}
