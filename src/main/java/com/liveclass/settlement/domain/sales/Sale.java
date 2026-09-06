package com.liveclass.settlement.domain.sales;

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
 *
 * 환불 상태 접근자를 두지 않는다. 조회 경로는 애그리게이트를 적재하지 않고
 * (N+1) 읽기 모델에 {@code RefundStatus.of}를 쓴다. 여기 두면 같은 개념에 경로가
 * 둘이 되고, 그중 하나는 아무도 안 부른다.
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

    /**
     * 저장소에서 복원. 취소를 함께 받아야 불변식이 성립한다.
     *
     * <p><b>여기서 불변식을 다시 검사하지 않는다.</b> 저장된 상태가 규칙을 어겼다면
     * (동시 취소 경합 — README 가정 11) 조회가 500이 되는 것보다 값을 보여주는 편이
     * 낫다. {@code RefundStatus.of}가 초과 환불을 {@code FULL}로 닫아 처리한다.
     * 새 취소를 막는 것은 {@link #cancel}이 계속 한다.
     */
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
     * 결제보다 이른 취소도 막는다. 통과시키면 판매가 없던 달에 환불이 귀속된다.
     *
     * @throws RefundAmountExceededException 누적 합계 + amount 가 원결제 금액을 넘을 때
     * @throws CancelBeforePaymentException  cancelledAt 이 paidAt 보다 이를 때
     */
    public Cancel cancel(String cancelId, long amount, Instant cancelledAt) {
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        if (cancelledAt.isBefore(paidAt)) {
            throw new CancelBeforePaymentException(id, paidAt, cancelledAt);
        }
        long already = cancelledTotal();
        if (amount > this.amount - already) {
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

    public long amount() {
        return amount;
    }

    public Instant paidAt() {
        return paidAt;
    }
}
