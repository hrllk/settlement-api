package com.liveclass.settlement.adapter.in.web.dto;

import java.util.List;

/**
 * {@code from}·{@code to}는 요청 문자열 그대로다. {@code SettlementPeriod.toExclusive()}는
 * {@code to=2025-03-31} 요청에 대해 {@code 2025-04-01}이라 되짚어 만들면 하루 밀린다.
 */
public record AdminSettlementResponse(
        String from, String to,
        List<CreatorPayoutItem> creators,
        long totalPayout) {
}
