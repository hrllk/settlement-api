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
 * 판매 영속성 표현. 도메인 모델이 아니라 저장 표현이라 규칙도 setter도 없다.
 *
 * 연관 매핑을 걸지 않는다. {@code @ManyToOne}은 지연 로딩 프록시와 N+1을
 * 부른다. 필요한 조인은 리포지토리가 JPQL로 명시한다.
 *
 * Lombok은 {@code @Getter}와 {@code @NoArgsConstructor}만 쓴다.
 * {@code @Data}·{@code @EqualsAndHashCode}는 JPA 식별자 의미론과 어긋난다.
 */
@Entity
@Table(name = "sales", indexes = {
        @Index(name = "idx_sales_course_paid", columnList = "course_id, paid_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // Hibernate 전용
public class SaleEntity {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(name = "course_id", length = 64, nullable = false)
    private String courseId;

    /** 원 단위. {@code Long}이 아니라 {@code long}이라 null이 애초에 불가능하다. */
    @Column(nullable = false)
    private long amount;

    /** UTC 순간. {@code LocalDateTime}을 쓰면 오프셋이 사라져 귀속 월을 알 수 없다. */
    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    public SaleEntity(String id, String courseId, long amount, Instant paidAt) {
        this.id = id;
        this.courseId = courseId;
        this.amount = amount;
        this.paidAt = paidAt;
    }
}
