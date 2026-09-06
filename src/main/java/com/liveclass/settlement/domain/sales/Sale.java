package com.liveclass.settlement.domain.sales;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 판매 한 건과 딸린 취소들. 누적 환불 ≤ 원결제를 스스로 지킨다. */
public class Sale {

    private final String id;
    private final String courseId;
    private final String studentId;
    private final long amount;
    private final Instant paidAt;
    private final List<Cancel> cancels;

    private Sale(String id, String courseId, String studentId, long amount,
                 Instant paidAt, List<Cancel> cancels) {
        this.id = Objects.requireNonNull(id, "id");
        this.courseId = Objects.requireNonNull(courseId, "courseId");
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.amount = amount;
        this.paidAt = Objects.requireNonNull(paidAt, "paidAt");
        this.cancels = new ArrayList<>(Objects.requireNonNull(cancels, "cancels"));
    }

    /** 신규 등록. 식별자는 밖에서 만들어 넘긴다. */
    public static Sale register(String id, String courseId, String studentId,
                                long amount, Instant paidAt) {
        return new Sale(id, courseId, studentId, amount, paidAt, List.of());
    }

    /** 저장소에서 복원. 불변식을 재검사하지 않는다 — 조회가 500이 되는 것보다 낫다. */
    public static Sale restore(String id, String courseId, String studentId, long amount,
                               Instant paidAt, List<Cancel> cancels) {
        return new Sale(id, courseId, studentId, amount, paidAt, cancels);
    }

    public Cancel cancel(String cancelId, long amount, Instant cancelledAt) {
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        if (cancelledAt.isBefore(paidAt)) {
            throw new CancelBeforePaymentException(id, paidAt, cancelledAt);
        }
        long already = cancelledTotal();
        // 뺄셈이다. already + amount 는 Long.MAX_VALUE 에서 오버플로해 통과한다.
        if (amount > this.amount - already) {   // >= 가 아니다. 전액 환불은 허용한다.
            throw new RefundAmountExceededException(id, this.amount, already, amount);
        }
        Cancel added = new Cancel(cancelId, amount, cancelledAt);
        cancels.add(added);
        return added;
    }

    public long cancelledTotal() {
        long total = 0;
        for (Cancel cancel : cancels) {
            total += cancel.amount();
        }
        return total;
    }

    /** 불변 뷰. 외부에서 불변식을 우회할 수 없다. */
    public List<Cancel> cancels() {
        return Collections.unmodifiableList(cancels);
    }

    public String id() {
        return id;
    }

    public String courseId() {
        return courseId;
    }

    public String studentId() {
        return studentId;
    }

    public long amount() {
        return amount;
    }

    public Instant paidAt() {
        return paidAt;
    }
}
