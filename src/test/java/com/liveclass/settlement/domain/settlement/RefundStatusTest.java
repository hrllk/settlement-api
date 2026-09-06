package com.liveclass.settlement.domain.settlement;

import static com.liveclass.settlement.domain.settlement.SettlementFixtures.CANCEL_1;
import static com.liveclass.settlement.domain.settlement.SettlementFixtures.CANCEL_2;
import static com.liveclass.settlement.domain.settlement.SettlementFixtures.CANCEL_3;
import static com.liveclass.settlement.domain.settlement.SettlementFixtures.SALE_1;
import static com.liveclass.settlement.domain.settlement.SettlementFixtures.SALE_3;
import static com.liveclass.settlement.domain.settlement.SettlementFixtures.SALE_4;
import static com.liveclass.settlement.domain.settlement.SettlementFixtures.SALE_5;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefundStatusTest {

    @Test
    @DisplayName("취소가 없으면 NONE")
    void none() {
        assertThat(RefundStatus.of(SALE_1, List.of())).isEqualTo(RefundStatus.NONE);
    }

    @Test
    @DisplayName("원결제보다 적게 취소되면 PARTIAL")
    void partial() {
        assertThat(RefundStatus.of(SALE_4, List.of(CANCEL_2))).isEqualTo(RefundStatus.PARTIAL);
    }

    @Test
    @DisplayName("원결제와 같은 금액이 취소되면 FULL")
    void full() {
        assertThat(RefundStatus.of(SALE_3, List.of(CANCEL_1))).isEqualTo(RefundStatus.FULL);
    }

    /** 초과 등록은 Task 4가 막지만 지금은 분기가 열려 있으면 안 된다. */
    @Test
    @DisplayName("원결제를 넘겨 취소돼도 FULL로 닫힌다")
    void overRefundClosesToFull() {
        assertThat(RefundStatus.of(80_000, 90_000)).isEqualTo(RefundStatus.FULL);
    }

    /** sale-5 는 1월 판매, cancel-3 은 2월 취소다. 월이 달라도 FULL 이어야 한다. */
    @Test
    @DisplayName("판매와 취소의 월이 달라도 기간을 보지 않는다")
    void ignoresPeriod() {
        assertThat(SALE_5.paidAt()).isBefore(CANCEL_3.cancelledAt());
        assertThat(RefundStatus.of(SALE_5, List.of(CANCEL_3))).isEqualTo(RefundStatus.FULL);
    }

    @Test
    @DisplayName("다른 판매의 취소는 합산하지 않는다")
    void ignoresOtherSales() {
        assertThat(RefundStatus.of(SALE_1, List.of(CANCEL_1))).isEqualTo(RefundStatus.NONE);
    }

    @Test
    @DisplayName("취소 목록이 null이면 어느 인자인지 알려주며 거부한다")
    void rejectsNullCancels() {
        assertThatThrownBy(() -> RefundStatus.of(SALE_1, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cancelsOfSale");
    }
}
