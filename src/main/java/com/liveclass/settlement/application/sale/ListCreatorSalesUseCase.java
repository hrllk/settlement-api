package com.liveclass.settlement.application.sale;

import com.liveclass.settlement.application.actor.ActorAccessPolicy;
import com.liveclass.settlement.application.actor.ActorContext;
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

    private final ActorAccessPolicy accessPolicy;
    // 포트는 하나다. 판매와 취소를 같은 SalesQueryPort에서 가져온다.
    private final SalesQueryPort queryPort;

    /**
     * <b>환불 상태에 기간 필터를 적용하지 않는다.</b> 이 유스케이스의 유일한
     * 함정이다.
     *
     * <p>sale-5가 증거다. 1월 판매인데 취소는 2월 3일이다.
     * {@code findCancels(1/1, 2/1, creator-2)}에는 cancel-3이 안 잡히므로 그
     * 경로로 상태를 만들면 {@code FULL}이 아니라 {@code NONE}이 나온다. 그래서
     * Task 3이 시간 조건 없는 {@code findCancelsBySaleIds}를 따로 만들었다.
     *
     * <p>정산 <b>금액</b> 집계는 기간으로 나뉘고 환불 <b>상태</b>는 나뉘지 않는다.
     * 둘이 다른 규칙을 따른다는 점이 이 프로젝트에서 가장 헷갈리는 지점이다.
     *
     * <p>날짜를 {@code String}으로 받는다. 파싱과 검증은 {@link SettlementPeriod}가
     * 소유한다. 컨트롤러가 타입 바인딩을 하면 Spring이
     * {@code MethodArgumentTypeMismatchException}을 먼저 던져
     * {@code InvalidSettlementPeriod}가 걸리지 않는다.
     */
    @Transactional(readOnly = true)
    public List<SaleWithRefundStatus> list(ActorContext actor, String creatorId,
                                           String from, String to) {
        accessPolicy.requireSelfOrAdmin(actor, creatorId);

        SettlementPeriod period = SettlementPeriod.ofDateRange(from, to);
        List<SaleRecord> sales = queryPort.findSalesForListing(
                period.fromInclusive(), period.toExclusive(), creatorId);

        // 판매 0건이면 빈 리스트가 들어간다. 어댑터가 쿼리 없이 빈 리스트를
        // 돌려주도록 방어했으므로 여기서 분기하지 않는다.
        List<CancelData> cancels = queryPort.findCancelsBySaleIds(
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

    /**
     * Task 3의 2인자 오버로드를 쓴다. {@code SaleRecord}를 받는 오버로드는 없다 --
     * {@code SaleRecord}는 {@code application.port.out}의 읽기 모델이고 도메인이
     * 그걸 알면 의존 방향이 뒤집힌다.
     */
    private static RefundStatus refundStatusOf(SaleRecord sale,
                                               Map<String, List<CancelData>> bySale) {
        long cancelled = bySale.getOrDefault(sale.saleId(), List.of()).stream()
                .mapToLong(CancelData::amount)
                .sum();
        return RefundStatus.of(sale.amount(), cancelled);
    }
}
