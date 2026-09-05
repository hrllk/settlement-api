package com.liveclass.settlement.domain.settlement;

/** 순 판매액에서 플랫폼 수수료를 계산한다. 요율 정책 교체 지점. */
public interface FeePolicy {

    long calculate(long netSales);
}
