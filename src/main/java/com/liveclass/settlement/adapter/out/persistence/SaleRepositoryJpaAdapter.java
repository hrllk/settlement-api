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

/** {@link SaleRepository} 구현. 애그리게이트를 조립하고 분해한다. */
@Component
@RequiredArgsConstructor
public class SaleRepositoryJpaAdapter implements SaleRepository {

    private final SaleJpaRepository sales;
    private final CancelJpaRepository cancels;

    /** 판매 1회 + 취소 1회로 조회해 조립한다. 취소를 함께 읽는 것이 계약이다. */
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
     * 판매 행과 아직 저장되지 않은 취소만 반영한다. 취소는 등록 후 불변이라
     * ID 존재 여부만으로 신규를 가린다.
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
