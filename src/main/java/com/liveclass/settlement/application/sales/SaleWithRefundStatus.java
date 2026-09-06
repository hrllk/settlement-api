package com.liveclass.settlement.application.sales;

import com.liveclass.settlement.application.port.out.SaleRecord;
import com.liveclass.settlement.domain.settlement.RefundStatus;

/** 판매 목록 한 줄. 컨트롤러가 응답 DTO로 옮긴다. */
public record SaleWithRefundStatus(SaleRecord sale, RefundStatus refundStatus) {
}
