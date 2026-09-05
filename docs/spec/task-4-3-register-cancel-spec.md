# Task 4.3 — 취소 등록과 누적 초과 환불 거부 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 의존 4.2 · 15분

## 유스케이스

```java
@Service
public class RegisterCancelUseCase {

    public String register(ActorContext actor, String saleId, long amount, Instant cancelledAt) {
        accessPolicy.requireAdmin(actor);

        SaleRecord sale = salePort.findSaleById(saleId)
                                  .orElseThrow(() -> new SaleNotFound(saleId));

        long already = salePort.sumCancelledAmount(saleId);
        if (already + amount > sale.amount()) {
            throw new RefundAmountExceeded(saleId, sale.amount(), already, amount);
        }

        String cancelId = salePort.saveCancel(saleId, amount, cancelledAt);
        log.info(...);                                   // 4.7  cancelId 포함
        return cancelId;                                 // 4.5의 CancelResponse가 쓴다
    }
}
```

## 누적 판정이 핵심이다

**단건 비교가 아니다.** `amount > sale.amount()`만 보면 80,000원 판매에 30,000원과 60,000원을 차례로 넣을 때 둘 다 통과해 총 90,000원이 환불된다. 기존 합계를 더해서 봐야 한다.

`30,000 + 60,000 > 80,000` 케이스가 이 판정만 잡는 시나리오다. 4.8이 단언한다.

**`>=`가 아니라 `>`다.** 합계가 원결제액과 정확히 같은 것은 전액 환불이며 허용해야 한다. `cancel-1`이 그 경우다 — `sale-3`의 80,000원 전액. `>=`로 쓰면 시드가 들어가지 않는다.

## Task 3과의 경계

Task 3의 `RefundStatus.of`는 취소 합계가 원결제 **이상**이면 `FULL`로 본다. `>=`다. 여기서 초과를 막으므로 실제로 초과 상태가 저장될 일은 없지만, Task 3은 "방어하지 않는다"를 코드로 표현할 수 없어 분기를 닫아 둔 것이다. 두 곳의 부등호가 다른 것은 의도적이다. 하나는 **입력 거부**, 하나는 **표현 분류**다.

계산기는 이미 등록된 취소를 그대로 집계할 뿐 유효성을 검증하지 않는다. 검증은 등록 시점의 책임이다.

## 취소 시각을 검증하지 않는다

`cancelledAt`이 `paidAt`보다 이른 값도 받는다. 등록 순서를 강제하지 않으므로 과거로 소급된 취소가 들어갈 수 있고, 그러면 결제 전에 환불된 것처럼 보인다. 3시간 예산에서 막지 않고 **README 가정 17로 남긴다.** 실무라면 `cancelledAt >= paidAt`을 거부할 지점이다.

## 동시성을 보장하지 않는다

`sumCancelledAmount` 조회와 `saveCancel` 저장 사이에 다른 요청이 끼면 둘 다 검사를 통과할 수 있다. 판매 행 잠금이나 `@Version`을 넣지 않는다. **README 가정 11로 남긴다.**

이 한계를 문서에 적는 것과 모르는 것은 다르다. 평가자가 보는 것은 "동시성을 처리했나"가 아니라 "동시성 문제를 인지했나"다.

## 파일

`application/sale/RegisterCancelUseCase.java`.

테스트는 없다. 4.8이 검증한다.

## 완료 기준

1. 없는 판매가 `SaleNotFound`를 던진다.
2. `30,000 + 60,000 > 80,000`이 `RefundAmountExceeded`를 던진다.
3. 합계가 원결제액과 **같은** 전액 환불은 통과한다.
4. CREATOR가 호출하면 `ActorAccessDenied`가 난다.
