package com.liveclass.settlement.adapter.in.web;

import com.liveclass.settlement.adapter.in.web.dto.AdminSettlementResponse;
import com.liveclass.settlement.adapter.in.web.dto.CreatorPayoutItem;
import com.liveclass.settlement.adapter.in.web.dto.MonthlySettlementResponse;
import com.liveclass.settlement.application.access.ActorContext;
import com.liveclass.settlement.application.settlement.AdminSettlement;
import com.liveclass.settlement.application.settlement.AdminSettlementUseCase;
import com.liveclass.settlement.application.settlement.MonthlySettlementUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 두 메서드 모두 {@link ActorContext}를 선언한다. 빠뜨리면 검사 없이 열린다. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettlementController {

    private final MonthlySettlementUseCase monthlySettlementUseCase;
    private final AdminSettlementUseCase adminSettlementUseCase;

    /**
     * 크리에이터 월별 정산 조회 API
     * 한 달치 정산 요약을 돌려준다. 본인 또는 운영자만 볼 수 있다.
     * 판매도 취소도 없는 달은 404가 아니라 200에 전 항목 0이다.
     */
    @GetMapping("/creators/{creatorId}/settlements/{yearMonth}")
    MonthlySettlementResponse monthly(@PathVariable String creatorId,
                                      @PathVariable String yearMonth,
                                      ActorContext actor) {
        return MonthlySettlementResponse.of(creatorId, yearMonth,
                monthlySettlementUseCase.settle(actor, creatorId, yearMonth));
    }

    /**
     * 운영자 기간 정산 집계 API
     * 기간 내 전체 크리에이터의 정산을 한 번에 돌려준다. 운영자 전용이다.
     * 기간 전체를 단일 구간으로 계산하므로 월별 조회의 합과 다르다 — 근거는 README.
     */
    @GetMapping("/admin/settlements")
    AdminSettlementResponse admin(@RequestParam String from,
                                  @RequestParam String to,
                                  ActorContext actor) {
        AdminSettlement result = adminSettlementUseCase.aggregate(actor, from, to);

        List<CreatorPayoutItem> items = result.creators().stream()
                .map(c -> CreatorPayoutItem.of(c.creatorId(), c.summary()))
                .toList();

        // from/to 는 요청 문자열 그대로다. 도메인의 toExclusive 는 하루 뒤라 쓸 수 없다.
        return new AdminSettlementResponse(from, to, items, result.totalPayout());
    }
}
