package com.liveclass.settlement.application.sale;

import com.liveclass.settlement.application.actor.ActorAccessPolicy;
import com.liveclass.settlement.application.actor.ActorContext;
import com.liveclass.settlement.application.port.out.SalesQueryPort;
import com.liveclass.settlement.domain.sales.CourseNotFound;
import com.liveclass.settlement.domain.sales.Sale;
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
public class RegisterSaleUseCase {

    private final ActorAccessPolicy accessPolicy;
    private final SalesQueryPort queryPort;
    private final SaleRepository saleRepository;

    /**
     * 등록을 ADMIN으로 제한한 것은 판단이다 — 크리에이터가 직접 등록하면 정산을
     * 스스로 부풀릴 수 있다. 과제 명세에는 없다.
     *
     * {@code courseExists} 검사는 필수다. FK 제약이 없어 없는 courseId로도 행이
     * 들어가고, 그 판매는 정산 조회에서 영원히 안 보인다.
     *
     * 식별자는 도메인이 아니라 여기서 만든다. 도메인이 만들면 테스트가 단언할 수 없다.
     */
    @Transactional
    public String register(ActorContext actor, String courseId, long amount, Instant paidAt) {
        accessPolicy.requireAdmin(actor);
        if (!queryPort.courseExists(courseId)) {
            throw new CourseNotFound(courseId);
        }

        Sale sale = Sale.register(UUID.randomUUID().toString(), courseId, amount, paidAt);
        saleRepository.save(sale);

        log.info("sale registered: saleId={}, courseId={}, amount={}, actorId={}",
                sale.id(), courseId, amount, actor.actorId());
        return sale.id();
    }
}
