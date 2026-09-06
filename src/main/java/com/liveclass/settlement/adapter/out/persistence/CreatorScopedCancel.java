package com.liveclass.settlement.adapter.out.persistence;

import java.time.Instant;

/** 취소 한 건과 그 취소가 속한 크리에이터. 운영자 집계 전용입니다. */
public interface CreatorScopedCancel {
    String getCreatorId();
    String getCancelId();
    String getSaleId();
    long getAmount();
    Instant getCancelledAt();
}
