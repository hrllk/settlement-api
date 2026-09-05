package com.liveclass.settlement.adapter.in.web.dto;

import java.time.OffsetDateTime;

public record CancelResponse(String cancelId, String saleId, long amount,
                             OffsetDateTime cancelledAt) {
}
