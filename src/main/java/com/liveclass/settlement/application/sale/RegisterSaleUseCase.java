package com.liveclass.settlement.application.sale;

import com.liveclass.settlement.application.actor.ActorAccessPolicy;
import com.liveclass.settlement.application.actor.ActorContext;
import com.liveclass.settlement.application.port.out.SalesQueryPort;
import com.liveclass.settlement.domain.sales.Sale;
import com.liveclass.settlement.domain.sales.SaleRepository;
import com.liveclass.settlement.domain.settlement.CourseNotFound;
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
     * 등록을 ADMIN으로 제한하는 것은 판단이다. 원본 과제에 명시가 없다.
     * 크리에이터가 자기 강의의 판매를 임의로 등록할 수 있으면 정산을 스스로
     * 부풀릴 수 있다. 등록은 결제 시스템이 하는 일이라고 보고 운영자로 좁힌다.
     *
     * <p>{@code courseExists} 검사가 필수다. Task 2가 FK 제약을 걸지 않았으므로
     * 없는 courseId로도 행이 그냥 들어가고, 그 판매는 어떤 크리에이터에도 속하지
     * 않아 정산 조회에서 영원히 안 보인다.
     *
     * <p>식별자를 여기서 만든다. 도메인에 {@code UUID.randomUUID()}를 넣으면
     * 테스트가 결과를 단언할 수 없다. Task 3이 "현재 시각을 읽지 않는다"를
     * 규칙으로 잡은 것과 같은 이유다.
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
