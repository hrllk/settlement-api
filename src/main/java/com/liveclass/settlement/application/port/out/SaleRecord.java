package com.liveclass.settlement.application.port.out;

import java.time.Instant;

/**
 * 판매 목록 조회용 읽기 모델.
 *
 * <p>Task 3의 {@code SaleData}를 쓰지 못하는 이유는 {@code courseId}가 없기
 * 때문이다. Task 3이 계산에 안 쓰는 필드를 의도적으로 뺐고 그 결정은 옳다.
 * 판매 목록은 계산이 아니라 조회이므로 자기 읽기 모델을 갖는다.
 */
public record SaleRecord(String saleId, String courseId, long amount, Instant paidAt) {
}
