package com.liveclass.settlement.application.port.out;

import com.liveclass.settlement.domain.settlement.CancelData;
import com.liveclass.settlement.domain.settlement.SaleData;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 원본 자료만 읽는다. 집계는 하지 않고, null 대신 빈 리스트를 돌려준다. */
public interface SalesQueryPort {

    /** 기간 내 결제된 판매. creatorId 필수. */
    List<SaleData> findSales(Instant fromInclusive, Instant toExclusive, String creatorId);

    /** 기간 내 취소된 건. creatorId 필수. 원본 판매는 기간 밖일 수 있다. */
    List<CancelData> findCancels(Instant fromInclusive, Instant toExclusive, String creatorId);

    /** 판매별 모든 취소. 환불 상태 전용이라 기간 필터가 없다. 빈 입력이면 빈 리스트. */
    List<CancelData> findCancelsBySaleIds(Collection<String> saleIds);

    /** 실적이 없는 크리에이터도 포함한다. */
    List<String> findAllCreatorIds();

    /** 판매 목록 조회용. paidAt 오름차순. */
    List<SaleRecord> findSalesForListing(Instant fromInclusive, Instant toExclusive, String creatorId);

    boolean courseExists(String courseId);
}
