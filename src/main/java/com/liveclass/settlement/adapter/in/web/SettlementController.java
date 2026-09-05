package com.liveclass.settlement.adapter.in.web;

import com.liveclass.settlement.adapter.in.web.dto.AdminSettlementResponse;
import com.liveclass.settlement.adapter.in.web.dto.CreatorPayoutItem;
import com.liveclass.settlement.adapter.in.web.dto.MonthlySettlementResponse;
import com.liveclass.settlement.application.actor.ActorContext;
import com.liveclass.settlement.application.settlement.AdminSettlement;
import com.liveclass.settlement.application.settlement.AdminSettlementUseCase;
import com.liveclass.settlement.application.settlement.CreatorPayout;
import com.liveclass.settlement.application.settlement.MonthlySettlementUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 두 메서드 모두 {@link ActorContext}를 선언한다. 해석기는 필터가 아니라 파라미터
 * 타입 기반 opt-in이라, 빠뜨린 메서드는 헤더 검사도 인가 판정도 없이 열린다.
 * 특히 {@code /api/admin/settlements}는 전체 매출이 나가는 유일한 엔드포인트다.
 * 컴파일러가 못 잡으므로 {@code ControllerActorGuardTest}가 검사한다.
 *
 * 연월과 일자를 {@code String}으로 받는다. {@code YearMonth}/{@code LocalDate}로
 * 바인딩하면 Spring이 {@code MethodArgumentTypeMismatchException}을 먼저 던져
 * {@code 2025-13}의 거부가 도메인이 아니라 프레임워크에서 일어나고, 오류 코드가
 * {@code INVALID_SETTLEMENT_PERIOD}가 아닌 다른 값이 된다.
 *
 * 경로를 나눈 이유는 대상 범위가 경로에 드러나게 하기 위해서다. 하나의
 * 엔드포인트에 {@code creatorId}를 옵션으로 두면 "파라미터를 빼면 전체가 나온다"가
 * 되어 인가 실수의 여지가 커진다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettlementController {

    private final MonthlySettlementUseCase monthlySettlement;
    private final AdminSettlementUseCase adminSettlement;

    @GetMapping("/creators/{creatorId}/settlements/{yearMonth}")
    MonthlySettlementResponse monthly(@PathVariable String creatorId,
                                      @PathVariable String yearMonth,
                                      ActorContext actor) {
        return MonthlySettlementResponse.of(creatorId, yearMonth,
                monthlySettlement.settle(actor, creatorId, yearMonth));
    }

    @GetMapping("/admin/settlements")
    AdminSettlementResponse admin(@RequestParam String from,
                                  @RequestParam String to,
                                  ActorContext actor) {
        AdminSettlement result = adminSettlement.aggregate(actor, from, to);

        List<CreatorPayoutItem> items = result.creators().stream()
                .map(c -> CreatorPayoutItem.of(c.creatorId(), c.summary()))
                .toList();

        // from/to 는 요청 문자열 그대로다. 도메인의 toExclusive 는 하루 뒤라 쓸 수 없다.
        return new AdminSettlementResponse(from, to, items, result.totalPayout());
    }
}
