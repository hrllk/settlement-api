package com.liveclass.settlement.domain.sales;

import java.time.Instant;
import java.util.Objects;

/** {@link Sale} 애그리게이트의 구성원. 홀로 존재하지 않는다. */
public record Cancel(String id, long amount, Instant cancelledAt) {

    public Cancel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
    }
}
