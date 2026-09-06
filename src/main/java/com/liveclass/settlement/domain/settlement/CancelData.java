package com.liveclass.settlement.domain.settlement;

import java.time.Instant;
import java.util.Objects;

/** 계산기 입력용 취소 자료. 포트가 항상 크리에이터로 좁히므로 creatorId가 없다. */
public record CancelData(String cancelId, String saleId, long amount, Instant cancelledAt) {

    public CancelData {
        Objects.requireNonNull(cancelId, "cancelId");
        Objects.requireNonNull(saleId, "saleId");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
    }
}
