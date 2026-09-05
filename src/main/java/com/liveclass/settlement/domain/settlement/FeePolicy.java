package com.liveclass.settlement.domain.settlement;

/**
 * 순 판매액에서 플랫폼 수수료를 계산한다.
 *
 * <p>인터페이스로 둔 이유는 요율 변경 가능성을 설계에 반영하기 위해서다.
 * 요율 이력 테이블과 시점별 적용은 만들지 않는다. 교체 지점만 열어 둔다.
 */
public interface FeePolicy {

    long calculate(long netSales);
}
