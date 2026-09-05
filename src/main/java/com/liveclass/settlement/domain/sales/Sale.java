package com.liveclass.settlement.domain.sales;

import com.liveclass.settlement.domain.settlement.RefundStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 판매 한 건과 그에 딸린 취소들. 누적 환불 ≤ 원결제를 스스로 지킨다.
 *
 *   Sale(80,000)
 *     ├─ Cancel(30,000)
 *     ├─ cancel(20,000)  -> 통과   (50,000 ≤ 80,000)
 *     └─ cancel(60,000)  -> 거부   (90,000 > 80,000)
 *
 * 프로젝트에서 애그리게이트가 필요한 지점은 여기 하나다. 정산 계산 쪽은 불변식이
 * 없는 순수 함수라 두지 않았다.
 *
 * Lombok을 쓰지 않는다. {@code @Getter}는 {@code cancels} 가변 리스트를 그대로
 * 새게 해 불변식을 우회시킨다.
 */
public class Sale {

    private final String id;
    private final String courseId;
    private final long amount;
    private final Instant paidAt;
    private final List<Cancel> cancels;

    private Sale(String id, String courseId, long amount, Instant paidAt, List<Cancel> cancels) {
        this.id = Objects.requireNonNull(id, "id");
        this.courseId = Objects.requireNonNull(courseId, "courseId");
        this.amount = amount;
        this.paidAt = Objects.requireNonNull(paidAt, "paidAt");
        this.cancels = new ArrayList<>(Objects.requireNonNull(cancels, "cancels"));
    }

    /** 신규 등록. 식별자는 밖에서 만들어 넘긴다. */
    public static Sale register(String id, String courseId, long amount, Instant paidAt) {
        return new Sale(id, courseId, amount, paidAt, List.of());
    }

    /** 저장소에서 복원. 취소를 함께 받아야 불변식이 성립한다. */
    public static Sale restore(String id, String courseId, long amount, Instant paidAt,
                               List<Cancel> cancels) {
        return new Sale(id, courseId, amount, paidAt, cancels);
    }

    /**
     * 취소를 추가한다. 누적 합계가 원결제 금액을 넘으면 거부한다.
     *
     * {@code already + amount}로 쓰면 안 된다. {@code Long.MAX_VALUE}가
     * 들어오면 오버플로로 음수가 돼 검사를 통과한다. 뺄셈은 두 항 모두 음수가
     * 아니라 넘치지 않는다.
     *
     * {@code >}이지 {@code >=}가 아니다. 정확히 같은 합계는 전액 환불이라 허용한다.
     *
     * @throws RefundAmountExceeded 누적 합계 + amount 가 원결제 금액을 넘을 때
     */
    public Cancel cancel(String cancelId, long amount, Instant cancelledAt) {
        long already = cancelledTotal();
        if (amount > this.amount - already) {
            throw new RefundAmountExceeded(id, this.amount, already, amount);
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

    /** Task 3의 enum을 그대로 쓴다. 같은 개념을 두 벌 만들지 않는다. */
    public RefundStatus refundStatus() {
        return RefundStatus.of(amount, cancelledTotal());
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

    public long amount() {
        return amount;
    }

    public Instant paidAt() {
        return paidAt;
    }
}
