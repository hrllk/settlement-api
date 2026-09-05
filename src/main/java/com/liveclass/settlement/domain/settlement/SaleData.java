package com.liveclass.settlement.domain.settlement;

import java.time.Instant;
import java.util.Objects;

/**
 * 계산기 입력용 판매 자료. JPA 엔티티가 아니다.
 *
 * <p>courseId, studentId는 정산 계산에 쓰이지 않으므로 담지 않는다.
 * 식별자는 원본 데이터가 {@code creator-1} 같은 문자열이라 래퍼 없이 String으로 둔다.
 * amount 부호는 검증하지 않는다. 등록 시점 규칙이라 Task 4 소관이다.
 */
public record SaleData(String saleId, String creatorId, long amount, Instant paidAt) {

    public SaleData {
        Objects.requireNonNull(saleId, "saleId");
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(paidAt, "paidAt");
    }
}
