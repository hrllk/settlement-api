package com.liveclass.settlement.application.sale;

import com.liveclass.settlement.application.port.out.SaleRecord;
import com.liveclass.settlement.domain.settlement.RefundStatus;

/**
 * 판매 목록 한 줄. 컨트롤러가 이걸 응답 DTO로 옮긴다.
 *
 * <p>{@code sale}을 통째로 들고 있어 saleId·courseId·amount·paidAt을 다 꺼낼 수 있다.
 */
public record SaleWithRefundStatus(SaleRecord sale, RefundStatus refundStatus) {
}
