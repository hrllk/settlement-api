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

/** 판매 영속성 표현. 연관 매핑을 걸지 않는다 — N+1을 부른다. */
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

    /** 정산 계산에는 안 쓰인다. 과제가 명시한 필드라 판매 내역으로 보관한다. */
    @Column(name = "student_id", length = 64, nullable = false)
    private String studentId;

    /** 원 단위. {@code Long}이 아니라 {@code long}이라 null이 애초에 불가능하다. */
    @Column(nullable = false)
    private long amount;

    /** UTC 순간. {@code LocalDateTime}을 쓰면 오프셋이 사라져 귀속 월을 알 수 없다. */
    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    public SaleEntity(String id, String courseId, String studentId, long amount, Instant paidAt) {
        this.id = id;
        this.courseId = courseId;
        this.studentId = studentId;
        this.amount = amount;
        this.paidAt = paidAt;
    }
}
