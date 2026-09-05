package com.liveclass.settlement.application.sale;

import com.liveclass.settlement.application.actor.ActorAccessPolicy;
import com.liveclass.settlement.application.actor.ActorContext;
import com.liveclass.settlement.domain.sales.Cancel;
import com.liveclass.settlement.domain.sales.Sale;
import com.liveclass.settlement.domain.sales.SaleNotFound;
import com.liveclass.settlement.domain.sales.SaleRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterCancelUseCase {

    private final ActorAccessPolicy accessPolicy;
    private final SaleRepository saleRepository;

    /**
     * <b>이 메서드에는 금액 비교문이 없다.</b> 초과 환불 거부는
     * {@code sale.cancel(...)} 안에 있다. 규칙이 유스케이스에 살면 다른 경로가
     * 생겼을 때 비교문을 빠뜨려도 컴파일이 통과한다.
     *
     * <p>{@code findById}가 취소까지 적재하는 것이 판정의 전제다. 부분 적재하면
     * 누적 합계가 실제보다 작게 나와 초과 환불이 통과한다.
     *
     * <p>동시성을 보장하지 않는다. 요청 둘이 동시에 오면 각자 같은 상태를 읽고
     * 각자 검사를 통과한다. 규칙이 도메인에 있다는 것과 원자적으로 적용된다는
     * 것은 다른 문제다. {@code @Version}을 넣지 않으며 README 가정으로 남긴다.
     */
    @Transactional
    public String register(ActorContext actor, String saleId, long amount, Instant cancelledAt) {
        accessPolicy.requireAdmin(actor);

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new SaleNotFound(saleId));

        Cancel cancel = sale.cancel(UUID.randomUUID().toString(), amount, cancelledAt);
        saleRepository.save(sale);

        log.info("cancel registered: cancelId={}, saleId={}, amount={}, actorId={}",
                cancel.id(), saleId, amount, actor.actorId());
        return cancel.id();
    }
}
