package com.liveclass.settlement.domain.settlement;

/**
 * 고정 요율 수수료. basis point로 표현해 double을 쓰지 않는다.
 * 20%는 2000bp다.
 */
public record FixedRateFeePolicy(int basisPoints) implements FeePolicy {

    /** 플랫폼 기본 요율 20%. */
    public static final int PLATFORM_DEFAULT_BP = 2_000;

    public FixedRateFeePolicy {
        if (basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("basisPoints must be 0..10000: " + basisPoints);
        }
    }

    /**
     * 음수 판정이 나눗셈보다 먼저인 것은 정책이다. 음수 매출에는 수수료를
     * 부과하지 않는다. 나중에 판정하면 -60,000에서 -12,000이 나와 플랫폼이
     * 크리에이터에게 수수료를 돌려주는 셈이 된다.
     *
     * <p>양수 구간에서 long 나눗셈의 절단이 곧 버림 정책이다.
     */
    @Override
    public long calculate(long netSales) {
        if (netSales <= 0) {
            return 0L;
        }
        return netSales * basisPoints / 10_000L;
    }
}
