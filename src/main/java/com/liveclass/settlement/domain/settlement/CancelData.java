package com.liveclass.settlement.domain.settlement;

import java.time.Instant;
import java.util.Objects;

/**
 * 계산기 입력용 취소 자료. JPA 엔티티가 아니다.
 * 포트가 항상 크리에이터로 좁혀 조회하므로 creatorId를 담지 않는다.
 */
public record CancelData(String cancelId, String saleId, long amount, Instant cancelledAt) {

    public CancelData {
        Objects.requireNonNull(cancelId, "cancelId");
        Objects.requireNonNull(saleId, "saleId");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
    }
}
