package com.liveclass.settlement.domain.settlement;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** KST 반열린 구간 [from, to). 과제의 "말일 23:59:59"를 의도적으로 이탈했다 — 근거는 README. */
public record SettlementPeriod(Instant fromInclusive, Instant toExclusive) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public SettlementPeriod {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new InvalidSettlementPeriodException(
                    "invalid period: [" + fromInclusive + ", " + toExclusive + ")");
        }
    }

    /** {@code yyyy-MM} 한 달. */
    public static SettlementPeriod ofYearMonth(String yearMonth) {
        YearMonth ym = parseYearMonth(requireText(yearMonth, "yearMonth"));
        return new SettlementPeriod(startOfDay(ym.atDay(1)),
                startOfDay(nextMonthStart(ym, yearMonth)));
    }

    /** {@code yyyy-MM-dd} 시작일부터 종료일까지. 같은 날은 하루짜리 구간이다. */
    public static SettlementPeriod ofDateRange(String startDate, String endDate) {
        LocalDate start = parseDate(requireText(startDate, "startDate"), "startDate");
        LocalDate end = parseDate(requireText(endDate, "endDate"), "endDate");
        // 팩토리에서 먼저 거부해야 원본 문자열이 메시지에 남는다.
        if (end.isBefore(start)) {
            throw new InvalidSettlementPeriodException(
                    "endDate must not precede startDate: " + startDate + " ~ " + endDate);
        }
        return new SettlementPeriod(startOfDay(start), startOfDay(nextDay(end, endDate)));
    }

    public boolean contains(Instant at) {
        return !at.isBefore(fromInclusive) && at.isBefore(toExclusive);
    }

    /** 상한을 여는 +1 산술이 넘치면 400으로 막는다. 안 감싸면 500이 된다. */
    private static LocalDate nextDay(LocalDate date, String raw) {
        try {
            return date.plusDays(1);
        } catch (DateTimeException e) {
            throw new InvalidSettlementPeriodException("endDate is out of supported range: " + raw);
        }
    }

    private static LocalDate nextMonthStart(YearMonth month, String raw) {
        try {
            return month.plusMonths(1).atDay(1);
        } catch (DateTimeException e) {
            throw new InvalidSettlementPeriodException("yearMonth is out of supported range: " + raw);
        }
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(KST).toInstant();
    }

    /** null·공백을 직접 막는다. {@code YearMonth.parse(null)}은 파싱 예외가 아니라 NPE다. */
    private static String requireText(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidSettlementPeriodException(field + " must not be blank: " + raw);
        }
        return raw.strip();
    }

    private static YearMonth parseYearMonth(String text) {
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException e) {
            throw new InvalidSettlementPeriodException("yearMonth must be yyyy-MM: " + text);
        }
    }

    private static LocalDate parseDate(String text, String field) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new InvalidSettlementPeriodException(field + " must be yyyy-MM-dd: " + text);
        }
    }
}
