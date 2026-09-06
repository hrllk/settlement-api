package com.liveclass.settlement.domain.sales;

/** 없는 판매를 참조했다. 전역 예외 처리기가 404로 변환한다. */
public class SaleNotFoundException extends RuntimeException {

    public SaleNotFoundException(String saleId) {
        super("sale not found: " + saleId);
    }
}
