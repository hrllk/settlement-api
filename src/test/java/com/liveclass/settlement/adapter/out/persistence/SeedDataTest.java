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

/** 시드 값만 본다. 계산 결과는 Task 3 소유다. 애노테이션은 어댑터 테스트와 같아야 한다. */
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

    /** 매핑 타입을 잡는다. LocalDateTime 으로 매핑하면 여기서 어긋난다. */
    @Test
    @DisplayName("sale-5의 저장값은 UTC 14:30이다")
    void saleFiveStoredValue() {
        SaleEntity saleFive = sales.findById("sale-5").orElseThrow();

        assertThat(saleFive.getPaidAt()).isEqualTo(Instant.parse("2025-01-31T14:30:00Z"));
    }

    /** 해석을 잡는다. 여기가 틀리면 creator-2 의 1월과 2월 정산이 통째로 뒤집힌다. */
    @Test
    @DisplayName("sale-5는 KST 1월에 귀속되고 2월에는 안 들어간다")
    void saleFiveBelongsToJanuaryInKst() {
        Instant paidAt = sales.findById("sale-5").orElseThrow().getPaidAt();

        // 구간을 손으로 계산한다. SettlementPeriod 를 쓰면 그쪽 버그가 시드 버그를 가린다.
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
