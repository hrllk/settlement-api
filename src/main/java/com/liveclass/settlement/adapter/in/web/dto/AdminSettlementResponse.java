package com.liveclass.settlement.adapter.in.web.dto;

import java.util.List;

/** {@code from}·{@code to}는 요청 문자열 그대로다. {@code toExclusive}는 하루 뒤라 못 쓴다. */
public record AdminSettlementResponse(
        String from, String to,
        List<CreatorPayoutItem> creators,
        long totalPayout) {
}
