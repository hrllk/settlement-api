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

/** 취소 영속성 표현. 인덱스 선행 컬럼은 {@code sale_id}여야 풀스캔을 면한다. */
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
