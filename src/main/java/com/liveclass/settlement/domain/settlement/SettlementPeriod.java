package com.liveclass.settlement.domain.settlement;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * KST 기준 정산 기간. 하한 포함, 상한 배제인 반열린 구간이다.
 *
 *   ofYearMonth("2025-03")
 *     [ 2025-03-01T00:00+09:00 , 2025-04-01T00:00+09:00 )
 *     ^ 포함                     ^ 배제
 *
 * 원본 과제는 "말일 23:59:59"로 적었으나 초 미만 구간이 누락되므로
 * 의도적으로 이탈했다. 근거는 README에 있다.
 */
public record SettlementPeriod(Instant fromInclusive, Instant toExclusive) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public SettlementPeriod {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new InvalidSettlementPeriod(
                    "invalid period: [" + fromInclusive + ", " + toExclusive + ")");
        }
    }

    /** {@code yyyy-MM} 한 달. */
    public static SettlementPeriod ofYearMonth(String yearMonth) {
        YearMonth ym = parseYearMonth(requireText(yearMonth, "yearMonth"));
        return new SettlementPeriod(startOfDay(ym.atDay(1)), startOfDay(ym.plusMonths(1).atDay(1)));
    }

    /** {@code yyyy-MM-dd} 시작일부터 종료일까지. 같은 날은 하루짜리 구간이다. */
    public static SettlementPeriod ofDateRange(String startDate, String endDate) {
        LocalDate start = parseDate(requireText(startDate, "startDate"), "startDate");
        LocalDate end = parseDate(requireText(endDate, "endDate"), "endDate");
        // 팩토리에서 먼저 거부해야 원본 문자열이 메시지에 남는다.
        // compact 생성자까지 흘리면 Instant로만 찍혀 무엇을 잘못 넣었는지 안 보인다.
        if (end.isBefore(start)) {
            throw new InvalidSettlementPeriod(
                    "endDate must not precede startDate: " + startDate + " ~ " + endDate);
        }
        return new SettlementPeriod(startOfDay(start), startOfDay(end.plusDays(1)));
    }

    public boolean contains(Instant at) {
        return !at.isBefore(fromInclusive) && at.isBefore(toExclusive);
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(KST).toInstant();
    }

    /** null·공백을 직접 막는다. {@code YearMonth.parse(null)}은 파싱 예외가 아니라 NPE다. */
    private static String requireText(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidSettlementPeriod(field + " must not be blank: " + raw);
        }
        return raw.strip();
    }

    private static YearMonth parseYearMonth(String text) {
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException e) {
            throw new InvalidSettlementPeriod("yearMonth must be yyyy-MM: " + text);
        }
    }

    private static LocalDate parseDate(String text, String field) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new InvalidSettlementPeriod(field + " must be yyyy-MM-dd: " + text);
        }
    }
}
