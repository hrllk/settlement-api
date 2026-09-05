package com.liveclass.settlement.domain.settlement;

import java.util.List;
import java.util.Objects;

/**
 * 판매와 취소를 서로 다른 기준으로 집계해 정산 요약을 만든다.
 *
 * <pre>
 *   sales   --[ paidAt      in period ]--> grossSales, saleCount
 *   cancels --[ cancelledAt in period ]--> refunds,    cancelCount
 *                                            |
 *                                            v
 *                                     SettlementSummary.of(...)
 *
 *   기간이 달라도 계산은 같다. 월별 조회와 운영자 기간 조회가
 *   이 함수 하나를 SettlementPeriod만 바꿔 재사용한다.
 * </pre>
 *
 * <p>기간 필터를 여기서 다시 거는 것은 의도적이다. 포트가 이미 창으로
 * 좁혀 주지만, (기간, 판매, 취소)의 순수 함수여야 단위 테스트가 포트 없이
 * 성립한다. 포트가 넓게 반환해도 결과가 같다.
 *
 * <p>크리에이터별 그룹핑은 하지 않는다. 입력이 이미 한 크리에이터의
 * 것이라고 가정한다. 목록 조립과 전체 합계는 Task 5의 몫이다.
 *
 * <p>로그를 남기지 않는다. 넣으면 도메인에 SLF4J가 딸려온다.
 */
public final class SettlementCalculator {

    private final FeePolicy feePolicy;

    public SettlementCalculator(FeePolicy feePolicy) {
        this.feePolicy = Objects.requireNonNull(feePolicy, "feePolicy");
    }

    public SettlementSummary calculate(SettlementPeriod period,
                                       List<SaleData> sales,
                                       List<CancelData> cancels) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(sales, "sales");
        Objects.requireNonNull(cancels, "cancels");

        long grossSales = 0;
        int saleCount = 0;
        for (SaleData sale : sales) {
            if (period.contains(sale.paidAt())) {
                grossSales += sale.amount();
                saleCount++;
            }
        }

        long refunds = 0;
        int cancelCount = 0;
        for (CancelData cancel : cancels) {
            if (period.contains(cancel.cancelledAt())) {
                refunds += cancel.amount();
                cancelCount++;
            }
        }

        return SettlementSummary.of(grossSales, saleCount, refunds, cancelCount, feePolicy);
    }
}
