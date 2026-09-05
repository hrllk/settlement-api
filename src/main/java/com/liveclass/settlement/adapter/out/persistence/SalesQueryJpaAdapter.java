package com.liveclass.settlement.adapter.out.persistence;

import com.liveclass.settlement.application.port.out.SaleRecord;
import com.liveclass.settlement.application.port.out.SalesQueryPort;
import com.liveclass.settlement.domain.settlement.CancelData;
import com.liveclass.settlement.domain.settlement.SaleData;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * 정산 계산과 판매 목록의 입력을 공급한다. 읽기 전용이다.
 * 쓰기는 {@code Sale} 애그리게이트와 {@code SaleRepository}가 따로 맡는다.
 */
@Component
@RequiredArgsConstructor
public class SalesQueryJpaAdapter implements SalesQueryPort {

    private final SaleJpaRepository sales;
    private final CancelJpaRepository cancels;
    private final CreatorJpaRepository creators;
    private final CourseJpaRepository courses;

    @Override
    public List<SaleData> findSales(Instant fromInclusive, Instant toExclusive, String creatorId) {
        return sales.findByCreatorAndPeriod(creatorId, fromInclusive, toExclusive)
                .stream()
                .map(entity -> toSaleData(entity, creatorId))
                .toList();
    }

    @Override
    public List<CancelData> findCancels(Instant fromInclusive, Instant toExclusive, String creatorId) {
        return cancels.findByCreatorAndPeriod(creatorId, fromInclusive, toExclusive)
                .stream()
                .map(SalesQueryJpaAdapter::toCancelData)
                .toList();
    }

    @Override
    public List<CancelData> findCancelsBySaleIds(Collection<String> saleIds) {
        if (saleIds == null || saleIds.isEmpty()) {
            // 빈 컬렉션을 그대로 JPQL로 내리면 in () 이 되고, 동작이 dialect에
            // 따라 갈린다. 판매 0건인 기간을 조회하면 실제로 이 경로를 밟는다 --
            // creator-3의 2025-03이 그 경우다.
            return List.of();
        }
        return cancels.findBySaleIdIn(saleIds)
                .stream()
                .map(SalesQueryJpaAdapter::toCancelData)
                .toList();
    }

    @Override
    public List<String> findAllCreatorIds() {
        // 정렬을 고정한다. 이 순서가 곧 운영자 응답 배열의 순서가 된다.
        return creators.findAll(Sort.by("id"))
                .stream()
                .map(CreatorEntity::getId)
                .toList();
    }

    @Override
    public List<SaleRecord> findSalesForListing(Instant fromInclusive, Instant toExclusive,
                                                String creatorId) {
        // findSales와 같은 쿼리다. 매핑만 다르다.
        return sales.findByCreatorAndPeriod(creatorId, fromInclusive, toExclusive)
                .stream()
                .map(SalesQueryJpaAdapter::toSaleRecord)
                .toList();
    }

    @Override
    public boolean courseExists(String courseId) {
        return courses.existsById(courseId);
    }

    private static SaleRecord toSaleRecord(SaleEntity entity) {
        return new SaleRecord(entity.getId(), entity.getCourseId(),
                entity.getAmount(), entity.getPaidAt());
    }

    /**
     * {@code creatorId}는 호출 인자를 그대로 넣는다. 항상 크리에이터로 좁히는
     * 계약이라 인자가 곧 정답이다 — 강의를 다시 조회하면 N+1이 된다.
     */
    private static SaleData toSaleData(SaleEntity entity, String creatorId) {
        return new SaleData(entity.getId(), creatorId, entity.getAmount(), entity.getPaidAt());
    }

    private static CancelData toCancelData(CancelEntity entity) {
        return new CancelData(entity.getId(), entity.getSaleId(),
                entity.getAmount(), entity.getCancelledAt());
    }
}
