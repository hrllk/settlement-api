package com.liveclass.settlement.domain.settlement;

/**
 * 없는 판매를 참조했다. 전역 예외 처리기가 404로 변환한다.
 *
 * <p>{@code @ResponseStatus}를 붙이지 않는다. 붙이면 도메인이 HTTP를 알게 되고
 * 상태 코드가 처리기와 애노테이션 두 곳에 흩어진다.
 */
public class SaleNotFound extends RuntimeException {

    public SaleNotFound(String saleId) {
        super("sale not found: " + saleId);
    }
}
