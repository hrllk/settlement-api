package com.liveclass.settlement.domain.settlement;

import java.time.Instant;
import java.util.Objects;

/**
 * 계산기 입력용 취소 자료. JPA 엔티티가 아니다.
 *
 * <p>creatorId가 없는 것은 의도적이다. SettlementDataPort가 취소를 항상
 * 크리에이터로 좁혀 조회하므로 값 자체가 귀속 정보를 들 필요가 없다.
 */
public record CancelData(String cancelId, String saleId, long amount, Instant cancelledAt) {

    public CancelData {
        Objects.requireNonNull(cancelId, "cancelId");
        Objects.requireNonNull(saleId, "saleId");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
    }
}
