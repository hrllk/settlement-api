package com.liveclass.settlement.adapter.out.persistence;

import java.time.Instant;

/**
 * 판매 한 건과 그 판매가 속한 크리에이터. 운영자 집계 전용입니다.
 *
 * 크리에이터를 조회 결과에 실어 오는 이유는 N+1 때문입니다. 크리에이터마다 따로 조회하면
 * 명수만큼 쿼리가 늘어납니다. 조인 결과에 `creatorId`를 함께 실으면 한 번으로 끝납니다.
 */
public interface CreatorScopedSale {
    String getCreatorId();
    String getSaleId();
    long getAmount();
    Instant getPaidAt();
}
