package com.liveclass.settlement.adapter.out.persistence;

import com.liveclass.settlement.domain.sales.Cancel;
import com.liveclass.settlement.domain.sales.Sale;
import com.liveclass.settlement.domain.sales.SaleRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link SaleRepository} 구현. 애그리게이트를 조립하고 분해한다.
 *
 * <p>이름 셋이 비슷해 헷갈리기 쉬우므로 정리한다.
 *
 * <pre>
 *   domain.sales.SaleRepository            도메인 인터페이스. Sale 애그리게이트를 다룬다
 *   adapter...SaleRepositoryJpaAdapter     그 구현 (이 클래스)
 *   adapter...SaleJpaRepository            Spring Data 인터페이스. SaleEntity를 다룬다
 * </pre>
 *
 * <p>Spring Data 쪽에 {@code Jpa} 접미사가 붙은 이유는 도메인이 깨끗한 이름을
 * 갖기 위해서다. 둘 다 {@code SaleRepository}면 이 클래스가 한쪽을 FQN으로 써야
 * 한다.
 */
@Component
@RequiredArgsConstructor
public class SaleRepositoryJpaAdapter implements SaleRepository {

    private final SaleJpaRepository sales;
    private final CancelJpaRepository cancels;

    /**
     * 판매 1회 + 취소 1회로 조회해 조립한다. 엔티티에 연관 매핑이 없으므로
     * 두 번 간다. 단건 경로라 N+1이 아니다.
     *
     * <p>취소를 함께 읽는 것이 계약이다. 부분 적재하면 {@code cancelledTotal()}이
     * 거짓말을 해 초과 환불이 통과한다.
     */
    @Override
    public Optional<Sale> findById(String saleId) {
        return sales.findById(saleId).map(entity -> {
            List<Cancel> loaded = cancels.findBySaleId(saleId).stream()
                    .map(c -> new Cancel(c.getId(), c.getAmount(), c.getCancelledAt()))
                    .toList();
            return Sale.restore(entity.getId(), entity.getCourseId(),
                    entity.getAmount(), entity.getPaidAt(), loaded);
        });
    }

    /**
     * 판매 행과 아직 저장되지 않은 취소만 반영한다.
     *
     * <p>이미 있는 취소를 다시 저장하면 불필요한 UPDATE가 나간다. 애그리게이트가
     * 어느 취소가 새것인지 알려주지 않으므로 ID로 거른다. 취소는 등록 후 변경되지
     * 않으므로 존재 여부만 보면 된다.
     */
    @Override
    public void save(Sale sale) {
        sales.save(new SaleEntity(sale.id(), sale.courseId(), sale.amount(), sale.paidAt()));

        Set<String> persisted = cancels.findBySaleId(sale.id()).stream()
                .map(CancelEntity::getId)
                .collect(Collectors.toSet());

        List<CancelEntity> added = sale.cancels().stream()
                .filter(c -> !persisted.contains(c.id()))
                .map(c -> new CancelEntity(c.id(), sale.id(), c.amount(), c.cancelledAt()))
                .toList();

        if (!added.isEmpty()) {
            cancels.saveAll(added);
        }
    }
}
