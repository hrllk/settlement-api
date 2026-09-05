package com.liveclass.settlement.domain.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SettlementPeriodTest {

    private static Instant kst(String iso) {
        return OffsetDateTime.parse(iso).toInstant();
    }

    @Nested
    @DisplayName("정규화")
    class Normalize {

        @Test
        @DisplayName("연월은 다음 달 1일 00:00 미만까지의 구간이 된다")
        void yearMonth() {
            SettlementPeriod period = SettlementPeriod.ofYearMonth("2025-03");

            assertThat(period.fromInclusive()).isEqualTo(kst("2025-03-01T00:00:00+09:00"));
            assertThat(period.toExclusive()).isEqualTo(kst("2025-04-01T00:00:00+09:00"));
        }

        @Test
        @DisplayName("일자 범위는 종료일 다음 날 00:00 미만까지의 구간이 된다")
        void dateRange() {
            SettlementPeriod period = SettlementPeriod.ofDateRange("2025-03-01", "2025-03-31");

            assertThat(period.fromInclusive()).isEqualTo(kst("2025-03-01T00:00:00+09:00"));
            assertThat(period.toExclusive()).isEqualTo(kst("2025-04-01T00:00:00+09:00"));
        }

        @Test
        @DisplayName("시작일과 종료일이 같으면 하루짜리 구간이 된다")
        void singleDay() {
            SettlementPeriod period = SettlementPeriod.ofDateRange("2025-03-05", "2025-03-05");

            assertThat(period.fromInclusive()).isEqualTo(kst("2025-03-05T00:00:00+09:00"));
            assertThat(period.toExclusive()).isEqualTo(kst("2025-03-06T00:00:00+09:00"));
        }
    }

    /**
     * 세 방향이 각각 다른 구현 오류를 잡는다.
     * 시작 포함  -> 하한을 연 구현 (at > from)
     * 종료 포함  -> 원본 과제 문구를 그대로 옮긴 구현 (at <= 말일 23:59:59)
     * 종료 배제  -> 상한을 닫은 구현 (at <= to)
     * 앞의 둘만으로는 상한을 나노초까지 닫은 구현이 통과한다.
     */
    @Nested
    @DisplayName("반열린 구간 경계")
    class Boundary {

        @Test
        @DisplayName("시작 경계는 포함한다")
        void startIncluded() {
            SettlementPeriod march = SettlementPeriod.ofYearMonth("2025-03");

            assertThat(march.contains(kst("2025-03-01T00:00:00.000+09:00"))).isTrue();
        }

        @Test
        @DisplayName("말일 23:59:59.999도 그 달에 포함한다")
        void endOfLastDayIncluded() {
            SettlementPeriod january = SettlementPeriod.ofYearMonth("2025-01");

            assertThat(january.contains(kst("2025-01-31T23:59:59.999+09:00"))).isTrue();
        }

        @Test
        @DisplayName("다음 달 1일 00:00은 배제한다")
        void nextMonthStartExcluded() {
            SettlementPeriod january = SettlementPeriod.ofYearMonth("2025-01");
            SettlementPeriod february = SettlementPeriod.ofYearMonth("2025-02");
            Instant boundary = kst("2025-02-01T00:00:00.000+09:00");

            assertThat(january.contains(boundary)).isFalse();
            assertThat(february.contains(boundary)).isTrue();
        }
    }

    @Nested
    @DisplayName("잘못된 입력")
    class Invalid {

        @Test
        @DisplayName("종료일이 시작일보다 이르면 원본 문자열을 담아 거부한다")
        void reversedRange() {
            assertThatThrownBy(() -> SettlementPeriod.ofDateRange("2025-03-31", "2025-03-01"))
                    .isInstanceOf(InvalidSettlementPeriod.class)
                    .hasMessageContaining("2025-03-31")
                    .hasMessageContaining("2025-03-01");
        }

        @Test
        @DisplayName("월 범위를 넘으면 거부한다")
        void monthOutOfRange() {
            assertThatThrownBy(() -> SettlementPeriod.ofYearMonth("2025-13"))
                    .isInstanceOf(InvalidSettlementPeriod.class);
        }

        @ParameterizedTest(name = "형식 불일치: \"{0}\"")
        @ValueSource(strings = {"2025/03", "202503", "2025-3"})
        void malformed(String yearMonth) {
            assertThatThrownBy(() -> SettlementPeriod.ofYearMonth(yearMonth))
                    .isInstanceOf(InvalidSettlementPeriod.class);
        }

        /**
         * YearMonth.parse(null)은 DateTimeParseException이 아니라 진입부
         * requireNonNull의 NPE를 던진다. 파싱 예외만 잡으면 NPE가 그대로
         * 500으로 나가므로 가드가 파싱보다 먼저여야 한다.
         */
        @ParameterizedTest(name = "빈 값: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void blank(String yearMonth) {
            assertThatThrownBy(() -> SettlementPeriod.ofYearMonth(yearMonth))
                    .isInstanceOf(InvalidSettlementPeriod.class);
        }

        @Test
        @DisplayName("팩토리를 우회한 표준 생성자도 역전을 거부한다")
        void constructorBypass() {
            Instant later = kst("2025-03-31T00:00:00+09:00");
            Instant earlier = kst("2025-03-01T00:00:00+09:00");

            assertThatThrownBy(() -> new SettlementPeriod(later, earlier))
                    .isInstanceOf(InvalidSettlementPeriod.class);
        }
    }
}
