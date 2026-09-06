package com.liveclass.settlement.adapter.in.web.dto;

import java.time.OffsetDateTime;

/** 응답도 {@code OffsetDateTime}이다. {@code Instant}면 Z로만 나가 KST를 암산해야 한다. */
public record SaleResponse(String saleId, String courseId, String studentId,
                           long amount, OffsetDateTime paidAt) {
}
