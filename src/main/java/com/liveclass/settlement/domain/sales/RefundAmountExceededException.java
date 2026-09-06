package com.liveclass.settlement.domain.sales;

/**
 * 누적 환불액이 원결제 금액을 넘는 취소가 들어왔다. 전역 처리기가 409로 변환한다.
 *
 * 다른 패키지에 같은 이름을 만들지 말 것. 처리기와 애그리게이트가 서로 다른
 * 쪽을 참조하면 컴파일은 통과하고 409 대신 500이 나간다.
 */
public class RefundAmountExceededException extends RuntimeException {

    public RefundAmountExceededException(String saleId, long saleAmount, long already, long requested) {
        super("refund exceeds sale amount: saleId=" + saleId
                + ", saleAmount=" + saleAmount
                + ", alreadyCancelled=" + already
                + ", requested=" + requested);
    }
}
