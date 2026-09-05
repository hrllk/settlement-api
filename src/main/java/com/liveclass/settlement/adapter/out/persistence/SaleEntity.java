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
 * 판매 영속성 표현. 도메인 모델이 아니다.
 *
 * <p>규칙을 갖지 않는다. 누적 환불 불변식은 Task 4의 {@code domain.sales.Sale}
 * 애그리게이트가 소유하고, 정산 계산은 Task 3의 순수 계산기가 한다. 이 클래스는
 * 저장 표현일 뿐이라 setter도 검증도 없다.
 *
 * <p>연관 매핑을 걸지 않는다. {@code courseId}는 평범한 문자열 컬럼이다.
 * {@code @ManyToOne}을 쓰면 지연 로딩 프록시와 N+1이 따라오고 어댑터가 어떤
 * 쿼리를 던지는지 코드만 봐서는 알 수 없게 된다. 필요한 조인은 리포지토리가
 * JPQL로 명시한다. FK 제약도 없으므로 없는 강의 참조는 Task 4가
 * {@code courseExists}로 막는다.
 *
 * <p>Lombok은 {@code @Getter}와 {@code @NoArgsConstructor}만 쓴다.
 * {@code @Data}나 {@code @EqualsAndHashCode}를 붙이면 equals/hashCode가 전체
 * 필드 기반이 되어 JPA의 식별자 의미론과 어긋나고 프록시에서 깨진다.
 * {@code @Setter}도 붙이지 않는다 — 등록 후 변경되지 않아야 한다.
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
