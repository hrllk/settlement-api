package com.liveclass.settlement.domain.settlement;

/**
 * 고정 요율 수수료. basis point로 표현해 double을 쓰지 않는다. 20%는 2000bp다.
 * 요율 값은 설정에서 주입받는다.
 */
public record FixedRateFeePolicy(int basisPoints) implements FeePolicy {

    public FixedRateFeePolicy {
        if (basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("basisPoints must be 0..10000: " + basisPoints);
        }
    }

    /**
     * 음수 매출에는 수수료를 부과하지 않는다 — 판정이 나눗셈보다 먼저여야 한다.
     * 양수 구간의 long 나눗셈 절단이 곧 버림 정책이다.
     */
    @Override
    public long calculate(long netSales) {
        if (netSales <= 0) {
            return 0L;
        }
        return netSales * basisPoints / 10_000L;
    }
}
