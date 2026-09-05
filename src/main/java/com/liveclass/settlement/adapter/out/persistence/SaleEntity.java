package com.liveclass.settlement.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

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
 */
@Entity
@Table(name = "sales", indexes = {
        @Index(name = "idx_sales_course_paid", columnList = "course_id, paid_at")
})
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

    /** Hibernate 전용. 애플리케이션 코드가 빈 엔티티를 만들지 못하게 protected로 둔다. */
    protected SaleEntity() {
    }

    public SaleEntity(String id, String courseId, long amount, Instant paidAt) {
        this.id = id;
        this.courseId = courseId;
        this.amount = amount;
        this.paidAt = paidAt;
    }

    public String getId() {
        return id;
    }

    public String getCourseId() {
        return courseId;
    }

    public long getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
