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
     * 여기에 금액 비교문을 두지 않는다. 초과 환불 거부는 {@code sale.cancel(...)}
     * 안에 있다. {@code findById}가 취소까지 적재하는 것이 그 판정의 전제다.
     *
     * 동시성은 보장하지 않는다 — {@code @Version} 없음. README 가정으로 남긴다.
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
