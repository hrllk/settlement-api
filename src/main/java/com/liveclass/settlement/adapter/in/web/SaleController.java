package com.liveclass.settlement.adapter.in.web;

import com.liveclass.settlement.adapter.in.web.dto.CancelResponse;
import com.liveclass.settlement.adapter.in.web.dto.CreatorSalesResponse;
import com.liveclass.settlement.adapter.in.web.dto.RegisterCancelRequest;
import com.liveclass.settlement.adapter.in.web.dto.RegisterSaleRequest;
import com.liveclass.settlement.adapter.in.web.dto.SaleItem;
import com.liveclass.settlement.adapter.in.web.dto.SaleResponse;
import com.liveclass.settlement.application.actor.ActorContext;
import com.liveclass.settlement.application.sale.ListCreatorSalesUseCase;
import com.liveclass.settlement.application.sale.RegisterCancelUseCase;
import com.liveclass.settlement.application.sale.RegisterSaleUseCase;
import com.liveclass.settlement.application.sale.SaleWithRefundStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>모든 메서드가 {@link ActorContext} 파라미터를 선언한다.</b>
 *
 * <p>Task 1의 해석기는 필터가 아니라 opt-in이다.
 * {@code ActorContextArgumentResolver.supportsParameter}가 파라미터 타입을 보고
 * 동작하므로, 파라미터를 선언하지 않은 메서드는 헤더 검사를 통째로 건너뛴다.
 * 그 엔드포인트는 인증 흔적도 없이 조용히 열린다. 컴파일러가 잡아주지 않는
 * 유일한 구조적 위험이라 Task 5.6의 리플렉션 가드 테스트가 검사한다.
 *
 * <p>컨트롤러가 하는 일은 DTO 변환과 상태 코드뿐이다. 접근 판정은 유스케이스가
 * 한다 -- 컨트롤러에 두면 Task 4와 Task 5가 같은 호출을 각자 복제하고 유스케이스를
 * 직접 테스트할 때 경계가 빠진다. 로깅도 두지 않는다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SaleController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RegisterSaleUseCase registerSale;
    private final RegisterCancelUseCase registerCancel;
    private final ListCreatorSalesUseCase listSales;

    @PostMapping("/sales")
    ResponseEntity<SaleResponse> register(@Valid @RequestBody RegisterSaleRequest request,
                                          ActorContext actor) {
        String saleId = registerSale.register(
                actor, request.courseId(), request.amount(), request.paidAt().toInstant());

        return ResponseEntity.created(URI.create("/api/sales/" + saleId))
                .body(new SaleResponse(saleId, request.courseId(),
                        request.amount(), request.paidAt()));
    }

    /** 취소에는 조회 엔드포인트가 없으므로 {@code Location}을 붙이지 않는다. */
    @PostMapping("/sales/{saleId}/cancellations")
    ResponseEntity<CancelResponse> cancel(@PathVariable String saleId,
                                          @Valid @RequestBody RegisterCancelRequest request,
                                          ActorContext actor) {
        String cancelId = registerCancel.register(
                actor, saleId, request.amount(), request.cancelledAt().toInstant());

        return ResponseEntity.status(201)
                .body(new CancelResponse(cancelId, saleId,
                        request.amount(), request.cancelledAt()));
    }

    /**
     * 날짜를 {@code String}으로 받는다. {@code LocalDate}로 바인딩하면 Spring이
     * {@code MethodArgumentTypeMismatchException}을 먼저 던져 {@code 2025-13}의
     * 거부가 도메인이 아니라 프레임워크에서 일어난다.
     */
    @GetMapping("/creators/{creatorId}/sales")
    CreatorSalesResponse list(@PathVariable String creatorId,
                              @RequestParam String from,
                              @RequestParam String to,
                              ActorContext actor) {
        List<SaleWithRefundStatus> found = listSales.list(actor, creatorId, from, to);

        List<SaleItem> items = found.stream()
                .map(s -> new SaleItem(
                        s.sale().saleId(), s.sale().courseId(), s.sale().amount(),
                        OffsetDateTime.ofInstant(s.sale().paidAt(), KST), s.refundStatus()))
                .toList();

        return new CreatorSalesResponse(creatorId, items);
    }
}
