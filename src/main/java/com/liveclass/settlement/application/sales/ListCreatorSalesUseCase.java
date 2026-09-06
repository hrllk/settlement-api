package com.liveclass.settlement.application.sales;

import com.liveclass.settlement.application.access.ActorAccessPolicy;
import com.liveclass.settlement.application.access.ActorContext;
import com.liveclass.settlement.application.port.out.SaleRecord;
import com.liveclass.settlement.application.port.out.SalesQueryPort;
import com.liveclass.settlement.domain.settlement.CancelData;
import com.liveclass.settlement.domain.settlement.RefundStatus;
import com.liveclass.settlement.domain.settlement.SettlementPeriod;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListCreatorSalesUseCase {

    private final ActorAccessPolicy actorAccessPolicy;
    private final SalesQueryPort salesQueryPort;

    /**
     * 환불 상태에 기간 필터를 적용하지 않는다. 기간으로 좁힌 취소로 상태를
     * 만들면 창 밖 취소를 놓쳐 {@code FULL}이 {@code NONE}으로 나온다. 그래서 시간
     * 조건 없는 {@code findCancelsBySaleIds}를 따로 쓴다. 금액 집계만 기간으로 나뉜다.
     *
     * 날짜는 {@code String}으로 받는다. 파싱·검증은 {@link SettlementPeriod}가 소유한다.
     */
    @Transactional(readOnly = true)
    public List<SaleWithRefundStatus> list(ActorContext actor, String creatorId,
                                           String from, String to) {
        actorAccessPolicy.requireSelfOrAdmin(actor, creatorId);

        SettlementPeriod period = SettlementPeriod.ofDateRange(from, to);
        List<SaleRecord> sales = salesQueryPort.findSalesForListing(
                period.fromInclusive(), period.toExclusive(), creatorId);

        // 판매 0건이면 빈 리스트가 들어간다. 어댑터가 쿼리 없이 빈 리스트를
        // 돌려주도록 방어했으므로 여기서 분기하지 않는다.
        List<CancelData> cancels = salesQueryPort.findCancelsBySaleIds(
                sales.stream().map(SaleRecord::saleId).toList());

        Map<String, List<CancelData>> bySale = cancels.stream()
                .collect(Collectors.groupingBy(CancelData::saleId));

        List<SaleWithRefundStatus> result = sales.stream()
                .map(sale -> new SaleWithRefundStatus(sale, refundStatusOf(sale, bySale)))
                .toList();

        log.info("creator sales listed: creatorId={}, from={}, to={}, count={}, actorId={}",
                creatorId, from, to, result.size(), actor.actorId());
        return result;
    }

    /** 2인자 오버로드를 쓴다. 도메인이 {@code SaleRecord}를 알면 의존 방향이 뒤집힌다. */
    private static RefundStatus refundStatusOf(SaleRecord sale,
                                               Map<String, List<CancelData>> bySale) {
        long cancelled = bySale.getOrDefault(sale.saleId(), List.of()).stream()
                .mapToLong(CancelData::amount)
                .sum();
        return RefundStatus.of(sale.amount(), cancelled);
    }
}
