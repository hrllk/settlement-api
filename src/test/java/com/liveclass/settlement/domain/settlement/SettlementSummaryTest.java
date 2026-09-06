package com.liveclass.settlement.domain.settlement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 파생값 불변식 위반만 본다. 인자를 전부 적어 두 검증을 분리한다. */
class SettlementSummaryTest {

    @Test
    @DisplayName("순 판매액이 총 판매액에서 환불액을 뺀 값과 다르면 거부한다")
    void netSalesMismatch() {
        // payout(99) == netSales(99) - fee(0) 이라 둘째 검증은 통과한다
        assertThatThrownBy(() -> new SettlementSummary(100, 1, 30, 1, 99, 0, 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("netSales");
    }

    @Test
    @DisplayName("정산 예정액이 순 판매액에서 수수료를 뺀 값과 다르면 거부한다")
    void payoutMismatch() {
        // netSales(70) == 100 - 30 이라 첫째 검증은 통과한다
        assertThatThrownBy(() -> new SettlementSummary(100, 1, 30, 1, 70, 14, 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payout");
    }
}
