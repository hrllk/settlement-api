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

/** 읽기 전용. 쓰기는 {@code Sale} 애그리게이트와 {@code SaleRepository}가 맡는다. */
@Component
@RequiredArgsConstructor
public class SalesQueryJpaAdapter implements SalesQueryPort {

    private final SaleJpaRepository saleJpaRepository;
    private final CancelJpaRepository cancelJpaRepository;
    private final CreatorJpaRepository creatorJpaRepository;
    private final CourseJpaRepository courseJpaRepository;

    @Override
    public List<SaleData> findSales(Instant fromInclusive, Instant toExclusive, String creatorId) {
        return saleJpaRepository.findByCreatorAndPeriod(creatorId, fromInclusive, toExclusive)
                .stream()
                .map(entity -> toSaleData(entity, creatorId))
                .toList();
    }

    @Override
    public List<CancelData> findCancels(Instant fromInclusive, Instant toExclusive, String creatorId) {
        return cancelJpaRepository.findByCreatorAndPeriod(creatorId, fromInclusive, toExclusive)
                .stream()
                .map(SalesQueryJpaAdapter::toCancelData)
                .toList();
    }

    @Override
    public List<CancelData> findCancelsBySaleIds(Collection<String> saleIds) {
        if (saleIds == null || saleIds.isEmpty()) {
            // 빈 컬렉션은 in () 이 되어 dialect마다 갈린다. 판매 0건이면 실제로 밟는다.
            return List.of();
        }
        return cancelJpaRepository.findBySaleIdIn(saleIds)
                .stream()
                .map(SalesQueryJpaAdapter::toCancelData)
                .toList();
    }

    @Override
    public List<String> findAllCreatorIds() {
        // 정렬을 고정한다. 이 순서가 곧 운영자 응답 배열의 순서가 된다.
        return creatorJpaRepository.findAll(Sort.by("id"))
                .stream()
                .map(CreatorEntity::getId)
                .toList();
    }

    @Override
    public List<SaleRecord> findSalesForListing(Instant fromInclusive, Instant toExclusive,
                                                String creatorId) {
        // findSales와 같은 쿼리다. 매핑만 다르다.
        return saleJpaRepository.findByCreatorAndPeriod(creatorId, fromInclusive, toExclusive)
                .stream()
                .map(SalesQueryJpaAdapter::toSaleRecord)
                .toList();
    }

    @Override
    public boolean courseExists(String courseId) {
        return courseJpaRepository.existsById(courseId);
    }

    private static SaleRecord toSaleRecord(SaleEntity entity) {
        return new SaleRecord(entity.getId(), entity.getCourseId(), entity.getStudentId(),
                entity.getAmount(), entity.getPaidAt());
    }

    /** 인자를 그대로 넣는다. 강의를 다시 조회하면 N+1이 된다. */
    private static SaleData toSaleData(SaleEntity entity, String creatorId) {
        return new SaleData(entity.getId(), creatorId, entity.getAmount(), entity.getPaidAt());
    }

    private static CancelData toCancelData(CancelEntity entity) {
        return new CancelData(entity.getId(), entity.getSaleId(),
                entity.getAmount(), entity.getCancelledAt());
    }
}
