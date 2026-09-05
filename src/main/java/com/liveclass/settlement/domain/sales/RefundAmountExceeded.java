package com.liveclass.settlement.domain.sales;

/**
 * 누적 환불액이 원결제 금액을 넘는 취소가 들어왔다. 전역 처리기가 409로 변환한다.
 *
 * <p>네 값을 메시지에 담는다. 로그만 보고 왜 거부됐는지 재구성할 수 있어야 한다.
 *
 * <p><b>이 예외는 {@code domain.sales}에만 존재한다.</b> {@code domain.settlement}에
 * 같은 이름을 또 만들면 컴파일은 통과하고 런타임이 틀린다 — 전역 처리기가 한쪽을
 * import하고 애그리게이트가 다른 쪽을 던지면 매칭이 안 돼 409 대신 500이 나간다.
 */
public class RefundAmountExceeded extends RuntimeException {

    public RefundAmountExceeded(String saleId, long saleAmount, long already, long requested) {
        super("refund exceeds sale amount: saleId=" + saleId
                + ", saleAmount=" + saleAmount
                + ", alreadyCancelled=" + already
                + ", requested=" + requested);
    }
}
