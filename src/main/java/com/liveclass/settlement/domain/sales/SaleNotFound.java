package com.liveclass.settlement.domain.sales;

/** 없는 판매를 참조했다. 전역 예외 처리기가 404로 변환한다. */
public class SaleNotFound extends RuntimeException {

    public SaleNotFound(String saleId) {
        super("sale not found: " + saleId);
    }
}
