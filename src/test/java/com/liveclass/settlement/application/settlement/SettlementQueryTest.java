package com.liveclass.settlement.application.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveclass.settlement.application.access.ActorContext;
import com.liveclass.settlement.application.access.ActorRole;
import com.liveclass.settlement.domain.settlement.SettlementPeriod;
import com.liveclass.settlement.domain.settlement.SettlementSummary;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 운영자 집계는 크리에이터 수와 무관하게 조회 2회입니다. 크리에이터마다 조회하면
 * 명수만큼 늘어나고(N+1), 그 회귀는 결과값이 같아서 다른 테스트가 잡지 못합니다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class SettlementQueryTest {

    private static final List<String> ALL = List.of("creator-1", "creator-2", "creator-3");

    @Autowired SettlementQuery settlementQuery;
    @Autowired EntityManagerFactory entityManagerFactory;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    @DisplayName("일괄 요약은 크리에이터 수와 무관하게 조회 2회다")
    void batchedSummaryIssuesTwoQueries() {
        SettlementPeriod period = SettlementPeriod.ofDateRange("2025-01-01", "2025-03-31");
        Statistics st = statistics();
        st.clear();

        settlementQuery.summarizeAll(period, ALL);

        assertThat(st.getPrepareStatementCount())
                .as("판매 1회 + 취소 1회. 크리에이터마다 부르면 명수만큼 늘어난다")
                .isEqualTo(2);
    }

    /**
     * 조회 방식이 갈려도 계산은 한 곳이어야 합니다. 두 경로가 같은 기간에서 다른 값을 내면
     * "운영자 집계가 월별 합과 다르다"의 원인이 하나 더 생겨 설명할 수 없게 됩니다.
     */
    @Test
    @DisplayName("일괄 요약과 단건 요약이 같은 값을 낸다")
    void batchedMatchesSingle() {
        SettlementPeriod period = SettlementPeriod.ofDateRange("2025-01-01", "2025-03-31");

        var batched = settlementQuery.summarizeAll(period, ALL);

        for (String creatorId : ALL) {
            SettlementSummary single = settlementQuery.summarize(period, creatorId);
            assertThat(batched.get(creatorId))
                    .as("%s 의 일괄 결과가 단건 결과와 달라졌다", creatorId)
                    .isEqualTo(single);
        }
    }

    @Test
    @DisplayName("실적 없는 크리에이터도 전 항목 0인 요약을 받는다")
    void creatorWithoutActivityGetsZeroSummary() {
        SettlementPeriod march = SettlementPeriod.ofYearMonth("2025-03");

        SettlementSummary zero = settlementQuery.summarizeAll(march, ALL).get("creator-3");

        assertThat(zero.grossSales()).isZero();
        assertThat(zero.refunds()).isZero();
        assertThat(zero.payout()).isZero();
    }
}
