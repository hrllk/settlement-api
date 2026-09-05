package com.liveclass.settlement.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 취소 영속성 표현. 규칙은 {@link SaleEntity}와 같다.
 *
 * <p>원본 판매가 조회 창 밖일 수 있다. cancel-3이 그 경우다 — 2월 취소인데
 * 원본 sale-5는 1월 판매다. 그래서 크리에이터로 좁히려면 판매를 거쳐 조인해야
 * 한다.
 *
 * <p>인덱스 선행 컬럼이 {@code sale_id}인 것은 의도적이다.
 * {@code findBySaleIdIn}에 시간 조건이 아예 없어서, {@code cancelled_at}을
 * 앞에 두면 선행 컬럼을 못 써 풀스캔이 된다.
 */
@Entity
@Table(name = "cancels", indexes = {
        @Index(name = "idx_cancels_sale_cancelled", columnList = "sale_id, cancelled_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CancelEntity {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(name = "sale_id", length = 64, nullable = false)
    private String saleId;

    @Column(nullable = false)
    private long amount;

    @Column(name = "cancelled_at", nullable = false)
    private Instant cancelledAt;

    public CancelEntity(String id, String saleId, long amount, Instant cancelledAt) {
        this.id = id;
        this.saleId = saleId;
        this.amount = amount;
        this.cancelledAt = cancelledAt;
    }
}
