package com.liveclass.settlement.adapter.in.web;

import com.liveclass.settlement.adapter.in.web.dto.CancelResponse;
import com.liveclass.settlement.adapter.in.web.dto.CreatorSalesResponse;
import com.liveclass.settlement.adapter.in.web.dto.RegisterCancelRequest;
import com.liveclass.settlement.adapter.in.web.dto.RegisterSaleRequest;
import com.liveclass.settlement.adapter.in.web.dto.SaleItem;
import com.liveclass.settlement.adapter.in.web.dto.SaleResponse;
import com.liveclass.settlement.application.access.ActorContext;
import com.liveclass.settlement.application.sales.ListCreatorSalesUseCase;
import com.liveclass.settlement.application.sales.RegisterCancelUseCase;
import com.liveclass.settlement.application.sales.RegisterSaleUseCase;
import com.liveclass.settlement.application.sales.SaleWithRefundStatus;
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

/** 모든 메서드가 {@link ActorContext}를 선언해야 한다. 빠뜨리면 검사 없이 열린다. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SaleController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RegisterSaleUseCase registerSaleUseCase;
    private final RegisterCancelUseCase registerCancelUseCase;
    private final ListCreatorSalesUseCase listCreatorSalesUseCase;

    /**
     * 판매 등록 API
     * 결제가 완료되면 호출된다. 운영자만 등록할 수 있고, 없는 강의는 404다.
     */
    @PostMapping("/sales")
    ResponseEntity<SaleResponse> register(@Valid @RequestBody RegisterSaleRequest request,
                                          ActorContext actor) {
        String saleId = registerSaleUseCase.register(
                actor, request.courseId(), request.amount(), request.paidAt().toInstant());

        return ResponseEntity.created(URI.create("/api/sales/" + saleId))
                .body(new SaleResponse(saleId, request.courseId(),
                        request.amount(), request.paidAt()));
    }

    /**
     * 취소 등록 API
     * 환불이 발생하면 호출된다. 누적 취소액이 원결제를 넘으면 409로 거부한다.
     * 취소에는 조회 엔드포인트가 없어 {@code Location}을 붙이지 않는다.
     */
    @PostMapping("/sales/{saleId}/cancellations")
    ResponseEntity<CancelResponse> cancel(@PathVariable String saleId,
                                          @Valid @RequestBody RegisterCancelRequest request,
                                          ActorContext actor) {
        String cancelId = registerCancelUseCase.register(
                actor, saleId, request.amount(), request.cancelledAt().toInstant());

        return ResponseEntity.status(201)
                .body(new CancelResponse(cancelId, saleId,
                        request.amount(), request.cancelledAt()));
    }

    /**
     * 크리에이터 판매 목록 조회 API
     * 기간 내 판매를 환불 상태와 함께 돌려준다. 본인 또는 운영자만 볼 수 있다.
     * {@code refundStatus}는 기간과 무관하게 그 판매의 모든 취소에서 나온다.
     */
    @GetMapping("/creators/{creatorId}/sales")
    CreatorSalesResponse list(@PathVariable String creatorId,
                              @RequestParam String from,
                              @RequestParam String to,
                              ActorContext actor) {
        List<SaleWithRefundStatus> found = listCreatorSalesUseCase.list(actor, creatorId, from, to);

        List<SaleItem> items = found.stream()
                .map(s -> new SaleItem(
                        s.sale().saleId(), s.sale().courseId(), s.sale().amount(),
                        OffsetDateTime.ofInstant(s.sale().paidAt(), KST), s.refundStatus()))
                .toList();

        return new CreatorSalesResponse(creatorId, items);
    }
}
