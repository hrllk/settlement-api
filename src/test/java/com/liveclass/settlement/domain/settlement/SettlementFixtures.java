package com.liveclass.settlement.domain.settlement;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 원본 과제 샘플 데이터. 판매 7건은 과제가 준 값이고, 취소 3건은 과제에
 * 없어 직접 정의했다(금액과 귀속 월은 tasks.json이 확정, 시각은 10:00 KST로 고정 — Task 2 시드와 같은 값이다).
 *
 * <p>KST 문자열을 그대로 쓰고 UTC로 손 변환하지 않는다. sale-5를
 * 2025-01-31T14:30:00Z로 옮겨 적다 한 자리만 틀리면 1월 판매가 2월로
 * 넘어가고 기대값 3행이 동시에 깨진다.
 */
final class SettlementFixtures {

    /**
     * 기대값 계산에 쓰는 요율 20%. 운영 코드는 이 값을 갖지 않고
     * settlement.fee.basis-points 설정에서 주입받는다(Task 5).
     */
    static final int PLATFORM_FEE_BP = 2_000;

    static final String CREATOR_1 = "creator-1";
    static final String CREATOR_2 = "creator-2";
    static final String CREATOR_3 = "creator-3";

    static final SaleData SALE_1 = sale("sale-1", CREATOR_1, 50_000, "2025-03-05T10:00:00+09:00");
    static final SaleData SALE_2 = sale("sale-2", CREATOR_1, 50_000, "2025-03-15T14:30:00+09:00");
    static final SaleData SALE_3 = sale("sale-3", CREATOR_1, 80_000, "2025-03-20T09:00:00+09:00");
    static final SaleData SALE_4 = sale("sale-4", CREATOR_1, 80_000, "2025-03-22T11:00:00+09:00");
    static final SaleData SALE_5 = sale("sale-5", CREATOR_2, 60_000, "2025-01-31T23:30:00+09:00");
    static final SaleData SALE_6 = sale("sale-6", CREATOR_2, 60_000, "2025-03-10T16:00:00+09:00");
    static final SaleData SALE_7 = sale("sale-7", CREATOR_3, 120_000, "2025-02-14T10:00:00+09:00");

    static final CancelData CANCEL_1 = cancel("cancel-1", "sale-3", 80_000, "2025-03-25T10:00:00+09:00");
    static final CancelData CANCEL_2 = cancel("cancel-2", "sale-4", 30_000, "2025-03-26T10:00:00+09:00");
    static final CancelData CANCEL_3 = cancel("cancel-3", "sale-5", 60_000, "2025-02-03T10:00:00+09:00");

    static List<SaleData> salesOf(String creatorId) {
        return Stream.of(SALE_1, SALE_2, SALE_3, SALE_4, SALE_5, SALE_6, SALE_7)
                .filter(sale -> sale.creatorId().equals(creatorId))
                .toList();
    }

    /** 크리에이터별 취소. 원본 판매를 통해 귀속을 판단한다(어댑터가 할 조인을 흉내낸다). */
    static List<CancelData> cancelsOf(String creatorId) {
        Set<String> saleIds = salesOf(creatorId).stream()
                .map(SaleData::saleId)
                .collect(Collectors.toSet());
        return Stream.of(CANCEL_1, CANCEL_2, CANCEL_3)
                .filter(cancel -> saleIds.contains(cancel.saleId()))
                .toList();
    }

    static Instant kst(String iso) {
        return OffsetDateTime.parse(iso).toInstant();
    }

    private static SaleData sale(String id, String creatorId, long amount, String paidAt) {
        return new SaleData(id, creatorId, amount, kst(paidAt));
    }

    private static CancelData cancel(String id, String saleId, long amount, String cancelledAt) {
        return new CancelData(id, saleId, amount, kst(cancelledAt));
    }

    private SettlementFixtures() {
    }
}
