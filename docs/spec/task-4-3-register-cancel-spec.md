# Task 4.3 — 취소 등록 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 의존 4.2 · 10분

## 유스케이스

```java
@Service
public class RegisterCancelUseCase {

    @Transactional                                   // 애그리게이트 한 번의 변경 = 한 트랜잭션
    public String register(ActorContext actor, String saleId, long amount, Instant cancelledAt) {
        accessPolicy.requireAdmin(actor);

        Sale sale = saleRepository.findById(saleId)
                                  .orElseThrow(() -> new SaleNotFoundException(saleId));

        Cancel cancel = sale.cancel(UUID.randomUUID().toString(), amount, cancelledAt);
        saleRepository.save(sale);

        log.info(...);                                   // 4.7  cancelId 포함
        return cancel.id();                              // 4.5의 CancelResponse가 쓴다
    }
}
```

## 유스케이스가 판정하지 않는다

**초과 환불 거부는 `sale.cancel(...)` 안에 있다.** 이 유스케이스에는 비교문도 합계 계산도 없다. 4.2의 애그리게이트가 규칙을 소유하기 때문이다.

이전 설계는 여기서 `sumCancelledAmount`를 조회해 `already + amount > sale.amount()`를 비교했다. 그러면 규칙이 유스케이스에 살고, 다른 경로가 생겼을 때 비교문을 빠뜨려도 컴파일이 통과한다. 애그리게이트로 옮기면 `cancel(...)`을 부르는 모든 경로가 규칙을 통과한다.

**로딩이 온전해야 판정이 성립한다.** `findById`가 취소까지 함께 적재하는 이유가 이것이다. 부분 적재하면 `cancelledTotal()`이 실제보다 작게 나와 초과 환불이 통과한다. 4.2의 리포지토리 계약이 이를 보장한다.

## Task 3과의 부등호 차이

Task 3의 `RefundStatus.of`는 취소 합계가 원결제 **이상**이면 `FULL`로 본다(`>=`). 애그리게이트는 초과를 **거부**한다(`>`). 두 부등호가 다른 것은 의도적이다.

| | 부등호 | 역할 |
| --- | --- | --- |
| `Sale.cancel` | `already + amount > amount` 이면 거부 | **입력 거부** |
| `RefundStatus.of` | `cancelledTotal >= saleAmount` 이면 `FULL` | **표현 분류** |

여기서 초과를 막으므로 실제로 초과 상태가 저장될 일은 없다. Task 3이 분기를 닫아 둔 것은 "방어하지 않는다"를 코드로 표현할 수 없기 때문이다. 계산기는 이미 등록된 취소를 그대로 집계할 뿐 유효성을 검증하지 않는다. 검증은 등록 시점의 책임이다.

## 취소 시각을 검증하지 않는다

`cancelledAt`이 `paidAt`보다 이르면 **애그리게이트가 거부한다**(`CancelBeforePaymentException` → 409 `CANCEL_BEFORE_PAYMENT`). 통과시키면 판매가 없던 달에 환불이 귀속돼 그 달 정산 예정액이 근거 없이 음수가 된다. 같은 시각은 허용한다 — 즉시 취소는 정상 거래다. 미래 방향은 기준 시각이 없어 열어 둔다.

애그리게이트에 넣는다면 `cancel(...)`의 두 번째 불변식이 될 자리다. 넣지 않은 것은 예산 판단이지 설계 누락이 아니라는 점을 README에 명시한다.

## 동시성을 보장하지 않는다

**애그리게이트가 동시성을 풀어주지는 않는다.** 요청 둘이 동시에 오면 각자 `findById`로 같은 상태를 읽고 각자 검사를 통과한 뒤 둘 다 저장한다. 규칙이 도메인에 있다는 것과 그 규칙이 원자적으로 적용된다는 것은 다른 문제다.

막으려면 판매 행 잠금(`SELECT ... FOR UPDATE`)이 필요하다. **`@Version`은 듣지 않는다** — 충돌하는 쓰기가 `sales` UPDATE가 아니라 `cancels` INSERT라 부모 행 버전이 오르지 않고, 어댑터가 로드한 엔티티 대신 같은 값의 새 엔티티를 merge해 dirty check도 UPDATE를 내지 않는다. 넣지 않으며 **README 가정 11로 남긴다.**

이 한계를 문서에 적는 것과 모르는 것은 다르다. 평가자가 보는 것은 "동시성을 처리했나"가 아니라 "동시성 문제를 인지했나"다.

## 파일

`application/sales/RegisterCancelUseCase.java`.

테스트는 없다. 불변식 자체는 4.2의 `SaleTest`가 단위로 잠갔고, HTTP 경로는 4.8이 검증한다.

## 완료 기준

1. 없는 판매가 `SaleNotFoundException`를 던진다.
2. 유스케이스에 금액 비교문이 없다. 판정이 전부 애그리게이트에 있다.
3. `30,000 + 60,000 > 80,000`이 `RefundAmountExceededException`로 거부된다.
4. 합계가 원결제액과 **같은** 전액 환불은 통과한다.
5. 결제보다 이른 취소는 409 `CANCEL_BEFORE_PAYMENT`로 거부한다. 결제와 같은 시각은 통과한다.
6. CREATOR가 취소를 시도하면 403 `ACTOR_ACCESS_DENIED`다.
5. CREATOR가 호출하면 `ActorAccessDeniedException`가 난다.
