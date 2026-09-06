package com.liveclass.settlement.application.sales;

import com.liveclass.settlement.application.access.ActorAccessPolicy;
import com.liveclass.settlement.application.access.ActorContext;
import com.liveclass.settlement.application.port.out.SalesQueryPort;
import com.liveclass.settlement.domain.sales.CourseNotFoundException;
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

    private final ActorAccessPolicy actorAccessPolicy;
    private final SalesQueryPort salesQueryPort;
    private final SaleRepository saleRepository;

    /** ADMIN 제한은 판단이다. {@code courseExists}는 필수 — FK가 없어 유령 행이 남는다. */
    @Transactional
    public String register(ActorContext actor, String courseId, long amount, Instant paidAt) {
        actorAccessPolicy.requireAdmin(actor);
        if (!salesQueryPort.courseExists(courseId)) {
            throw new CourseNotFoundException(courseId);
        }

        Sale sale = Sale.register(UUID.randomUUID().toString(), courseId, amount, paidAt);
        saleRepository.save(sale);

        log.info("sale registered: saleId={}, courseId={}, amount={}, actorId={}",
                sale.id(), courseId, amount, actor.actorId());
        return sale.id();
    }
}
