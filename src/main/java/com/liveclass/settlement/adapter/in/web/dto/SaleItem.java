package com.liveclass.settlement.adapter.in.web.dto;

import com.liveclass.settlement.domain.settlement.RefundStatus;
import java.time.OffsetDateTime;

/**
 * {@code refundStatus}는 그 판매의 모든 취소에서 계산된다. 조회 기간으로
 * 자르지 않는다. sale-5를 1월 구간으로 조회해도 2월 취소가 반영돼 {@code FULL}이다.
 */
public record SaleItem(String saleId, String courseId, long amount,
                       OffsetDateTime paidAt, RefundStatus refundStatus) {
}
