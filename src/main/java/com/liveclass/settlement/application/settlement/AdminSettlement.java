package com.liveclass.settlement.application.settlement;

import java.util.List;

/** 기간을 담지 않는다. 응답의 from/to 는 컨트롤러가 요청 문자열을 그대로 쓴다. */
public record AdminSettlement(List<CreatorPayout> creators, long totalPayout) {
}
