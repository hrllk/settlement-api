package com.liveclass.settlement.domain.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveclass.settlement.domain.settlement.RefundStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 불변식이 도메인에 있으므로 Spring 없이 단위로 잠근다. */
class SaleTest {

    private static Instant kst(String iso) {
        return OffsetDateTime.parse(iso).toInstant();
    }

    private static Sale sale(long amount) {
        return Sale.register("sale-x", "course-1", "student-1", amount,
                kst("2025-06-10T10:00:00+09:00"));
    }

    private static Instant at(String day) {
        return kst("2025-06-" + day + "T12:00:00+09:00");
    }

    @Test
    @DisplayName("원결제보다 적게 취소하면 PARTIAL")
    void partial() {
        Sale sale = sale(80_000);

        sale.cancel("c1", 30_000, at("11"));

        assertThat(sale.cancelledTotal()).isEqualTo(30_000);
        assertThat(RefundStatus.of(sale.amount(), sale.cancelledTotal())).isEqualTo(RefundStatus.PARTIAL);
    }

    /** 단건 비교 구현을 잡는다. amount > this.amount 만 보면 둘 다 통과해 90,000이 환불된다. */
    @Test
    @DisplayName("누적 합계가 원결제를 넘으면 거부한다")
    void rejectsAccumulatedExcess() {
        Sale sale = sale(80_000);
        sale.cancel("c1", 30_000, at("11"));

        assertThatThrownBy(() -> sale.cancel("c2", 60_000, at("12")))
                .isInstanceOf(RefundAmountExceededException.class)
                .hasMessageContaining("80000")
                .hasMessageContaining("30000")
                .hasMessageContaining("60000");
    }

    /** >= 로 잘못 쓰면 여기서 걸린다. 초기 데이터의 cancel-1이 정확히 이 경우다. */
    @Test
    @DisplayName("합계가 원결제와 같은 전액 환불은 통과한다")
    void allowsExactFullRefund() {
        Sale sale = sale(80_000);

        sale.cancel("c1", 80_000, at("11"));

        assertThat(RefundStatus.of(sale.amount(), sale.cancelledTotal())).isEqualTo(RefundStatus.FULL);
    }

    @Test
    @DisplayName("여러 부분 취소의 합이 정확히 원결제여도 통과한다")
    void allowsSplitFullRefund() {
        Sale sale = sale(80_000);

        sale.cancel("c1", 30_000, at("11"));
        sale.cancel("c2", 50_000, at("12"));

        assertThat(sale.cancelledTotal()).isEqualTo(80_000);
        assertThat(RefundStatus.of(sale.amount(), sale.cancelledTotal())).isEqualTo(RefundStatus.FULL);
    }

    @Test
    @DisplayName("cancels()로는 불변식을 우회할 수 없다")
    void cancelsViewIsImmutable() {
        Sale sale = sale(80_000);
        List<Cancel> view = sale.cancels();

        assertThatThrownBy(() -> view.add(new Cancel("c9", 999_999, at("11"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** {@code already + amount} 로 쓰면 오버플로해 음수가 되고 검사를 통과한다. */
    @Test
    @DisplayName("Long.MAX_VALUE 취소가 오버플로로 통과하지 않는다")
    void rejectsOverflowingAmount() {
        Sale sale = sale(80_000);
        sale.cancel("c1", 30_000, at("11"));

        assertThatThrownBy(() -> sale.cancel("c2", Long.MAX_VALUE, at("12")))
                .isInstanceOf(RefundAmountExceededException.class);
    }

    @Test
    @DisplayName("결제보다 이른 취소는 거부한다")
    void cancelBeforePaymentRejected() {
        Sale sale = Sale.register("s1", "c1", "st1", 80_000, at("10"));

        assertThatThrownBy(() -> sale.cancel("c1", 1_000, at("09")))
                .isInstanceOf(CancelBeforePaymentException.class)
                .hasMessageContaining("s1");
        assertThat(sale.cancelledTotal()).isZero();
    }

    @Test
    @DisplayName("결제와 같은 시각의 취소는 허용한다")
    void cancelAtPaymentInstantAllowed() {
        Sale sale = Sale.register("s1", "c1", "st1", 80_000, at("10"));

        sale.cancel("c1", 1_000, at("10"));

        assertThat(sale.cancelledTotal()).isEqualTo(1_000);
    }
}
