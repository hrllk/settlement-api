package com.liveclass.settlement.adapter.in.web.dto;

import java.util.List;

/**
 * {@code from}·{@code to}는 <b>요청에 온 문자열을 그대로</b> 담는다. 도메인에서
 * 되짚어 만들면 안 된다 — {@code SettlementPeriod.toExclusive()}는 {@code to=2025-03-31}
 * 요청에 대해 {@code 2025-04-01}이라 하루 밀린 값이 나간다.
 */
public record AdminSettlementResponse(
        String from, String to,
        List<CreatorPayoutItem> creators,
        long totalPayout) {
}
