package com.liveclass.settlement.application.settlement;

import com.liveclass.settlement.domain.settlement.SettlementSummary;

/** 운영자 집계의 한 행. 크리에이터 하나와 그 요약. */
public record CreatorPayout(String creatorId, SettlementSummary summary) {
}
