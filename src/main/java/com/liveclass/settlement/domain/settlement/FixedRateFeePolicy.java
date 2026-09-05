package com.liveclass.settlement.domain.settlement;

/**
 * 고정 요율 수수료. basis point로 표현해 double을 쓰지 않는다. 20%는 2000bp다.
 *
 * <p>요율 값은 이 클래스가 갖지 않는다. 도메인이 "현재 플랫폼 요율이 20%"라는
 * 사업 사실을 알 이유가 없다. 값은 {@code settlement.fee.basis-points} 설정에
 * 있고 Task 5의 조립 지점에서 주입한다.
 *
 * <p>요율을 런타임에 바꿀 수 있게 만들면 시점별 적용이 따라와야 한다. 이력
 * 없이 값만 바뀌면 이미 조회한 과거 정산이 조용히 달라진다. 그 단계는
 * 구현하지 않으며, 가려면 이 인터페이스를 FeePolicyResolver#resolve(period)로
 * 바꾼다. 근거는 README에 남긴다.
 */
public record FixedRateFeePolicy(int basisPoints) implements FeePolicy {

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
