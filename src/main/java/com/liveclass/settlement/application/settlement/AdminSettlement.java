package com.liveclass.settlement.application.settlement;

import java.util.List;

/**
 * 기간을 담지 않는다. 응답의 {@code from}/{@code to}는 요청에 온 문자열을
 * 컨트롤러가 그대로 담는다 — {@code SettlementPeriod.toExclusive()}는
 * {@code to=2025-03-31} 요청에 대해 {@code 2025-04-01}이라 쓸 수 없다.
 */
public record AdminSettlement(List<CreatorPayout> creators, long totalPayout) {
}
