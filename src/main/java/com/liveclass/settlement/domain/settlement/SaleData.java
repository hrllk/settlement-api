package com.liveclass.settlement.domain.settlement;

import java.time.Instant;
import java.util.Objects;

/**
 * 계산기 입력용 판매 자료. JPA 엔티티가 아니다.
 * 계산에 쓰이지 않는 courseId·studentId는 담지 않는다.
 */
public record SaleData(String saleId, String creatorId, long amount, Instant paidAt) {

    public SaleData {
        Objects.requireNonNull(saleId, "saleId");
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(paidAt, "paidAt");
    }
}
