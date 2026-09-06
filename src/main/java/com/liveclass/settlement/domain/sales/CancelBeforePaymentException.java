package com.liveclass.settlement.domain.sales;

import java.time.Instant;

/**
 * 취소 시각이 결제 시각보다 이르다. 전역 처리기가 409로 변환한다.
 *
 * 막지 않으면 판매가 없던 달에 환불이 귀속돼 그 달 정산 예정액이 근거 없이
 * 음수가 된다.
 */
public class CancelBeforePaymentException extends RuntimeException {

    public CancelBeforePaymentException(String saleId, Instant paidAt, Instant cancelledAt) {
        super("cancel precedes payment: saleId=" + saleId
                + ", paidAt=" + paidAt
                + ", cancelledAt=" + cancelledAt);
    }
}
