package com.liveclass.settlement.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

/**
 * 시드가 의도한 값 그대로 들어갔는지만 본다. 정산 계산 결과는 검증하지 않는다 —
 * 그건 Task 3이 계산기 단위 테스트로 소유한다. 이 테스트는 "Task 3이 맞다고
 * 가정할 때 그 계산기에 들어갈 입력이 맞는가"를 잡는다.
 *
 * {@code @Import}는 쓰지 않아도 붙인다. {@code SalesQueryJpaAdapterTest}와
 * 애노테이션이 다르면 컨텍스트가 둘 생기고 같은 H2 인스턴스를 create-drop으로
 * 밟는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(SalesQueryJpaAdapter.class)
class SeedDataTest {

    @Autowired
    SaleJpaRepository sales;
    @Autowired
    CancelJpaRepository cancels;
    @Autowired
    CreatorJpaRepository creators;
    @Autowired
    CourseJpaRepository courses;

    private static Instant kst(String iso) {
        return OffsetDateTime.parse(iso).toInstant();
    }

    @Test
    @DisplayName("행 수는 크리에이터 3, 강의 4, 판매 7, 취소 3")
    void rowCounts() {
        assertThat(creators.count()).isEqualTo(3);
        assertThat(courses.count()).isEqualTo(4);
        assertThat(sales.count()).isEqualTo(7);
        assertThat(cancels.count()).isEqualTo(3);
    }

    /**
     * 매핑 타입을 잡는다. 엔티티를 LocalDateTime으로 잘못 매핑하면 저장값이
     * 2025-01-31T23:30이 되어 여기서 어긋난다.
     */
    @Test
    @DisplayName("sale-5의 저장값은 UTC 14:30이다")
    void saleFiveStoredValue() {
        SaleEntity saleFive = sales.findById("sale-5").orElseThrow();

        assertThat(saleFive.getPaidAt()).isEqualTo(Instant.parse("2025-01-31T14:30:00Z"));
    }

    /**
     * 해석을 잡는다. 타입이 맞아도 시드에 KST 벽시계 값을 잘못 적을 수 있다.
     * sale-5는 이 프로젝트에서 유일하게 KST와 UTC의 날짜가 갈리는 레코드이며,
     * 여기가 틀리면 creator-2의 1월과 2월 정산이 통째로 뒤집힌다.
     */
    @Test
    @DisplayName("sale-5는 KST 1월에 귀속되고 2월에는 안 들어간다")
    void saleFiveBelongsToJanuaryInKst() {
        Instant paidAt = sales.findById("sale-5").orElseThrow().getPaidAt();

        // 구간을 손으로 계산해 둔다. Task 3의 SettlementPeriod를 쓰면 그쪽
        // 버그가 시드 버그를 가려버린다. 여기서는 독립 검증이 목적이다.
        Instant januaryFrom = kst("2025-01-01T00:00:00+09:00");   // 2024-12-31T15:00Z
        Instant februaryFrom = kst("2025-02-01T00:00:00+09:00");  // 2025-01-31T15:00Z
        Instant marchFrom = kst("2025-03-01T00:00:00+09:00");

        boolean inJanuary = !paidAt.isBefore(januaryFrom) && paidAt.isBefore(februaryFrom);
        boolean inFebruary = !paidAt.isBefore(februaryFrom) && paidAt.isBefore(marchFrom);

        assertThat(inJanuary).as("KST 1월 구간에 들어간다").isTrue();
        assertThat(inFebruary).as("KST 2월 구간에는 안 들어간다").isFalse();
    }

    @Test
    @DisplayName("취소 세 건이 각각 올바른 판매에 연결된다")
    void cancelLinks() {
        assertThat(cancels.findById("cancel-1").orElseThrow())
                .satisfies(c -> {
                    assertThat(c.getSaleId()).isEqualTo("sale-3");
                    assertThat(c.getAmount()).isEqualTo(80_000L);
                });
        assertThat(cancels.findById("cancel-2").orElseThrow())
                .satisfies(c -> {
                    assertThat(c.getSaleId()).isEqualTo("sale-4");
                    assertThat(c.getAmount()).isEqualTo(30_000L);
                });
        assertThat(cancels.findById("cancel-3").orElseThrow())
                .satisfies(c -> {
                    assertThat(c.getSaleId()).isEqualTo("sale-5");
                    assertThat(c.getAmount()).isEqualTo(60_000L);
                });
    }
}
