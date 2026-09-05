package com.liveclass.settlement.adapter.out.persistence;

import com.liveclass.settlement.application.port.out.SalesQueryPort;
import com.liveclass.settlement.domain.settlement.CancelData;
import com.liveclass.settlement.domain.settlement.SaleData;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * 정산 계산의 입력을 공급한다. 읽기 전용이다.
 *
 * <p>쓰기는 Task 4가 {@code domain.sales}의 {@code Sale} 애그리게이트와 도메인
 * {@code SaleRepository}로 따로 맡는다. 같은 테이블을 보지만 모델이 다르다 —
 * 정산은 평평한 값의 합산이고 등록은 불변식이 필요하다.
 *
 * <p>Task 4가 이 클래스에 {@code findSalesForListing}과 {@code courseExists}를
 * 더한다. 인터페이스가 자라면 구현체도 자라야 한다. Task 2 완료 시점에는
 * 인터페이스에 네 개뿐이라 컴파일이 통과한다.
 *
 * <p>{@code adapter.out}이므로 Spring 애노테이션이 허용된다. Spring 금지는
 * {@code domain}과 {@code application.port.out}에만 적용된다.
 */
@Component
public class SalesQueryJpaAdapter implements SalesQueryPort {

    private final SaleJpaRepository sales;
    private final CancelJpaRepository cancels;
    private final CreatorJpaRepository creators;

    public SalesQueryJpaAdapter(SaleJpaRepository sales,
                                CancelJpaRepository cancels,
                                CreatorJpaRepository creators) {
        this.sales = sales;
        this.cancels = cancels;
        this.creators = creators;
    }

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

    /**
     * {@code creatorId}는 호출 인자를 그대로 넣는다. 조회 계약이 "항상
     * 크리에이터로 좁힌다"이므로 결과의 모든 판매가 그 크리에이터의 것이고,
     * 인자가 곧 정답이다. 강의를 다시 조회하면 판매마다 한 번씩 돌아 N+1이 된다.
     *
     * <p>전체 크리에이터를 한 번에 긁는 조회 경로가 생기면 이 가정이 깨진다.
     * 그런 경로를 만들지 않는 것이 포트 계약이다.
     */
    private static SaleData toSaleData(SaleEntity entity, String creatorId) {
        return new SaleData(entity.getId(), creatorId, entity.getAmount(), entity.getPaidAt());
    }

    private static CancelData toCancelData(CancelEntity entity) {
        return new CancelData(entity.getId(), entity.getSaleId(),
                entity.getAmount(), entity.getCancelledAt());
    }
}
