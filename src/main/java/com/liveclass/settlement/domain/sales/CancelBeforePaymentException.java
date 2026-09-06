package com.liveclass.settlement.domain.sales;

import java.time.Instant;

/** 결제보다 이른 취소. 409 — 막지 않으면 판매 없던 달에 환불이 귀속된다. */
public class CancelBeforePaymentException extends RuntimeException {

    public CancelBeforePaymentException(String saleId, Instant paidAt, Instant cancelledAt) {
        super("cancel precedes payment: saleId=" + saleId
                + ", paidAt=" + paidAt
                + ", cancelledAt=" + cancelledAt);
    }
}
