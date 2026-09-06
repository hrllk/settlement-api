package com.liveclass.settlement.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveclass.settlement.domain.settlement.CancelData;
import com.liveclass.settlement.domain.settlement.SaleData;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

/** 애노테이션은 {@code SeedDataTest}와 같아야 한다. 다르면 컨텍스트가 둘 생겨 서로 드롭한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(SalesQueryJpaAdapter.class)
class SalesQueryJpaAdapterTest {

    @Autowired
    SalesQueryJpaAdapter adapter;

    private static Instant kst(String iso) {
        return OffsetDateTime.parse(iso).toInstant();
    }

    private static final Instant MARCH_FROM = kst("2025-03-01T00:00:00+09:00");
    private static final Instant MARCH_TO = kst("2025-04-01T00:00:00+09:00");
    private static final Instant FEBRUARY_FROM = kst("2025-02-01T00:00:00+09:00");
    private static final Instant FEBRUARY_TO = kst("2025-03-01T00:00:00+09:00");

    @Test
    @DisplayName("creator-1의 2025-03 판매는 4건 합 260,000원")
    void findSales() {
        List<SaleData> found = adapter.findSales(MARCH_FROM, MARCH_TO, "creator-1");

        // saleId로 순서를 직접 본다. 금액으로 보면 값이 같을 때 정렬이 틀려도 통과한다.
        assertThat(found).extracting(SaleData::saleId)
                .containsExactly("sale-1", "sale-2", "sale-3", "sale-4");
        assertThat(found).allSatisfy(s -> assertThat(s.creatorId()).isEqualTo("creator-1"));
        assertThat(found.stream().mapToLong(SaleData::amount).sum()).isEqualTo(260_000L);
    }

    /** 3단 서브쿼리를 확인한다. 이 쿼리가 대표 숫자인 환불 110,000원을 만든다. */
    @Test
    @DisplayName("creator-1의 2025-03 취소는 2건 합 110,000원이고 시각 순이다")
    void findCancelsForCreatorOne() {
        List<CancelData> found = adapter.findCancels(MARCH_FROM, MARCH_TO, "creator-1");

        assertThat(found).extracting(CancelData::cancelId).containsExactly("cancel-1", "cancel-2");
        assertThat(found.stream().mapToLong(CancelData::amount).sum()).isEqualTo(110_000L);
    }

    /** 원본 판매가 조회 창 밖(1월)인데도 취소는 2월로 잡혀야 한다. */
    @Test
    @DisplayName("creator-2의 2025-02 취소는 원본 판매가 1월이어도 잡힌다")
    void findCancelsAcrossMonthBoundary() {
        List<CancelData> found = adapter.findCancels(FEBRUARY_FROM, FEBRUARY_TO, "creator-2");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).cancelId()).isEqualTo("cancel-3");
        assertThat(found.get(0).saleId()).isEqualTo("sale-5");
        assertThat(found.get(0).amount()).isEqualTo(60_000L);
    }

    /** 환불 상태 산출용 경로. 시간 조건이 없어야 2월 취소가 1월 판매에 붙는다. */
    @Test
    @DisplayName("판매 ID 조회에는 기간 조건이 없다")
    void findCancelsBySaleIdsIgnoresPeriod() {
        List<CancelData> found = adapter.findCancelsBySaleIds(List.of("sale-5"));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).cancelId()).isEqualTo("cancel-3");
    }

    @Test
    @DisplayName("빈 컬렉션은 쿼리 없이 빈 리스트를 돌려준다")
    void findCancelsBySaleIdsEmpty() {
        assertThat(adapter.findCancelsBySaleIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("크리에이터 목록은 실적 없는 크리에이터를 포함하고 오름차순이다")
    void findAllCreatorIds() {
        assertThat(adapter.findAllCreatorIds())
                .containsExactly("creator-1", "creator-2", "creator-3");
    }

    /** 빈 컬렉션 방어가 실제로 필요해지는 시나리오. */
    @Test
    @DisplayName("creator-3의 2025-03은 판매가 없어 빈 리스트다")
    void findSalesEmptyMonth() {
        assertThat(adapter.findSales(MARCH_FROM, MARCH_TO, "creator-3")).isEmpty();
    }
}
