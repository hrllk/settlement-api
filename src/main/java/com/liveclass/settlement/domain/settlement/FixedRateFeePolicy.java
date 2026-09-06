package com.liveclass.settlement.domain.settlement;

/** 고정 요율 수수료. basis point라 double을 쓰지 않는다. 20% = 2000bp. */
public record FixedRateFeePolicy(int basisPoints) implements FeePolicy {

    public FixedRateFeePolicy {
        if (basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("basisPoints must be 0..10000: " + basisPoints);
        }
    }

    /** 음수 매출에는 부과하지 않는다. 판정이 나눗셈보다 먼저여야 한다. */
    @Override
    public long calculate(long netSales) {
        if (netSales <= 0) {
            return 0L;
        }
        return netSales * basisPoints / 10_000L;
    }
}
