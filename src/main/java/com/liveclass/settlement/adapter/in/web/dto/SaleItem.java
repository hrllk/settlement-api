package com.liveclass.settlement.adapter.in.web.dto;

import com.liveclass.settlement.domain.settlement.RefundStatus;
import java.time.OffsetDateTime;

/** {@code refundStatus}는 그 판매의 모든 취소에서 나온다. 조회 기간으로 자르지 않는다. */
public record SaleItem(String saleId, String courseId, String studentId, long amount,
                       OffsetDateTime paidAt, RefundStatus refundStatus) {
}
