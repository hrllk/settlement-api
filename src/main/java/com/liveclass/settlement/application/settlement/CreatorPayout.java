package com.liveclass.settlement.application.settlement;

import com.liveclass.settlement.domain.settlement.SettlementSummary;

public record CreatorPayout(String creatorId, SettlementSummary summary) {
}
