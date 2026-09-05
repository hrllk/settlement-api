package com.liveclass.settlement.domain.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FixedRateFeePolicyTest {

    private final FeePolicy policy =
            new FixedRateFeePolicy(FixedRateFeePolicy.PLATFORM_DEFAULT_BP);

    @Test
    @DisplayName("순 판매액의 20%를 수수료로 뗀다")
    void positive() {
        assertThat(policy.calculate(150_000)).isEqualTo(30_000);
    }

    @Test
    @DisplayName("순 판매액이 0이면 수수료도 0")
    void zero() {
        assertThat(policy.calculate(0)).isZero();
    }

    /**
     * 음수 판정이 나눗셈보다 먼저라야 한다. 순서를 바꾸면 -12,000이 나와
     * 플랫폼이 크리에이터에게 수수료를 돌려주는 셈이 된다.
     */
    @Test
    @DisplayName("순 판매액이 음수면 수수료를 0으로 막는다")
    void negativeClampsToZero() {
        assertThat(policy.calculate(-60_000)).isZero();
    }

    /**
     * 샘플 데이터 금액이 전부 5의 배수라 20%가 정수로 떨어진다.
     * 제공 시나리오만으로는 버림 정책이 검증되지 않아 케이스를 추가했다.
     */
    @Test
    @DisplayName("나누어떨어지지 않으면 원 단위로 버린다")
    void truncates() {
        assertThat(policy.calculate(33_333)).isEqualTo(6_666); // 실제 6,666.6
    }

    @ParameterizedTest(name = "basisPoints={0}")
    @ValueSource(ints = {-1, 10_001})
    @DisplayName("요율이 0..10000 밖이면 생성을 거부한다")
    void rejectsOutOfRangeRate(int basisPoints) {
        assertThatThrownBy(() -> new FixedRateFeePolicy(basisPoints))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
