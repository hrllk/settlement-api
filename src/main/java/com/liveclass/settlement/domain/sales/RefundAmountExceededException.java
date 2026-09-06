package com.liveclass.settlement.domain.sales;

/** 누적 환불액이 원결제를 넘었다. 409. */
public class RefundAmountExceededException extends RuntimeException {

    public RefundAmountExceededException(String saleId, long saleAmount, long already, long requested) {
        super("refund exceeds sale amount: saleId=" + saleId
                + ", saleAmount=" + saleAmount
                + ", alreadyCancelled=" + already
                + ", requested=" + requested);
    }
}
