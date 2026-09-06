package com.liveclass.settlement.application.port.out;

import java.time.Instant;

/** 판매 목록 조회용 읽기 모델. 계산용 {@code SaleData}와 달리 courseId를 갖는다. */
public record SaleRecord(String saleId, String courseId, String studentId,
                         long amount, Instant paidAt) {
}
