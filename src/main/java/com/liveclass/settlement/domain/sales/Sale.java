package com.liveclass.settlement.domain.sales;

import com.liveclass.settlement.domain.settlement.RefundStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 판매 한 건과 그에 딸린 취소들. <b>누적 환불 ≤ 원결제</b>를 스스로 지킨다.
 *
 * <pre>
 *   Sale(80,000)
 *     ├─ Cancel(30,000)
 *     └─ cancel(20,000)  -> 통과   (30,000 + 20,000 = 50,000 ≤ 80,000)
 *        cancel(60,000)  -> 거부   (30,000 + 60,000 = 90,000 > 80,000)
 * </pre>
 *
 * <p>이 프로젝트에서 애그리게이트가 필요한 지점은 정확히 여기 하나다. 이 규칙을
 * application 서비스에 두면 "조회하고, 더하고, 비교하고, 저장한다"가 유스케이스마다
 * 반복되고 누가 빠뜨려도 컴파일이 통과한다. 여기 두면 {@link #cancel}을 부르는
 * 경로가 규칙을 우회할 방법이 없다.
 *
 * <p>정산 계산 쪽에는 애그리게이트를 두지 않았다. 거기는 불변식이 없고 전부 값에
 * 대한 순수 함수다. <b>애그리게이트는 지킬 불변식이 있을 때만 값어치가 있다.</b>
 *
 * <p>Lombok을 쓰지 않는다. {@code @Getter}를 붙이면 {@code cancels} 가변 리스트가
 * 그대로 새어 호출자가 {@code add}로 불변식을 우회한다.
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
     * <p><b>{@code already + amount}로 쓰면 안 된다.</b> {@code @Positive}는
     * {@code Long.MAX_VALUE}를 허용한다. 취소가 하나라도 있는 판매에
     * {@code MAX_VALUE}를 넣으면 합이 오버플로해 음수가 되고 상한 검사를 그냥
     * 통과한다. {@code this.amount - already}는 두 항 모두 음수가 아니라 넘치지
     * 않는다. 애그리게이트를 둔 이유가 이 불변식인데 산술로 우회되면 의미가 없다.
     *
     * <p><b>{@code >}이지 {@code >=}가 아니다.</b> 합계가 원결제액과 정확히 같은
     * 것은 전액 환불이며 허용해야 한다. 시드의 cancel-1이 그 경우다.
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
