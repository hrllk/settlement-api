# Task 4.4 — 크리에이터별 기간 판매 목록 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 의존 4.1 · 15분

## 유스케이스

```java
@Service
public class ListCreatorSalesUseCase {

    public List<SaleWithRefundStatus> list(ActorContext actor, String creatorId,
                                           String from, String to) {
        accessPolicy.requireSelfOrAdmin(actor, creatorId);

        SettlementPeriod period = SettlementPeriod.ofDateRange(from, to);
        List<SaleRecord> sales = salePort.findSalesByCreator(          // SettlementQueryPort가 아니다
                period.fromInclusive(), period.toExclusive(), creatorId);

        List<CancelData> cancels = dataPort.findCancelsBySaleIds(
                sales.stream().map(SaleRecord::saleId).toList());

        Map<String, List<CancelData>> bySale =
                cancels.stream().collect(groupingBy(CancelData::saleId));

        return sales.stream()
                .map(s -> new SaleWithRefundStatus(
                        s, RefundStatus.of(s, bySale.getOrDefault(s.saleId(), List.of()))))
                .toList();
    }
}
```

## `SettlementQueryPort`가 아니라 `SaleQueryPort`로 조회한다

응답의 `SaleItem`에는 `courseId`가 들어간다. 그런데 Task 3의 `SaleData`에는 `courseId`가 없다.

```java
public record SaleData(String saleId, String creatorId, long amount, Instant paidAt) { }
```

Task 3이 계산에 안 쓰는 필드를 의도적으로 뺀 것이고 그 결정은 옳다. 판매 목록은 계산이 아니라 조회이므로 **4.2의 `SaleQueryPort.findSalesByCreator`를 쓴다.** 그쪽 `SaleRecord`가 `courseId`를 갖는다. 목록은 애그리게이트를 쓰지 않는다 — N개를 로딩하면 각각 자기 취소를 딸고 와 N+1이 된다.

취소는 여전히 `SettlementQueryPort.findCancelsBySaleIds`를 쓴다. Task 3이 환불 상태 산출을 위해 만든 메서드이고 `CancelData`에 부족한 필드가 없다.

## 환불 상태에 기간 필터를 적용하지 않는다

**이 서브태스크의 유일한 함정이다.** 환불 상태는 그 판매에 연결된 **모든** 취소를 본다. 기간으로 자르지 않는다.

`sale-5`가 그 증거다. 1월 판매인데 취소는 2월 3일이다. 1월 판매 목록을 조회하면 `findCancels(1/1, 2/1, creator-2)`에는 `cancel-3`이 안 잡힌다. 그 경로로 환불 상태를 만들면 `sale-5`가 `FULL`이 아니라 `NONE`으로 나온다.

그래서 Task 3이 `findCancelsBySaleIds`를 따로 만들었다. **시간 조건이 없는 조회다.** 이 메서드를 쓰지 않으면 규칙을 지킬 수 없다.

정산 **금액** 집계는 기간으로 나뉘고 환불 **상태**는 나뉘지 않는다. 둘이 다른 규칙을 따른다는 점이 이 프로젝트에서 가장 헷갈리는 지점이다.

## 빈 목록 처리

판매가 0건이면 `findCancelsBySaleIds`에 빈 리스트가 들어간다. Task 2.5 어댑터가 쿼리 없이 빈 리스트를 돌려주도록 방어했다. 이 유스케이스는 그 계약을 믿고 별도 분기를 두지 않는다.

## 날짜를 `String`으로 받는다

`SettlementPeriod.ofDateRange(String, String)`가 파싱과 검증을 소유한다. 컨트롤러가 `@RequestParam LocalDate`로 바인딩하면 Spring이 `MethodArgumentTypeMismatchException`을 먼저 던져 `InvalidSettlementPeriod`가 걸리지 않는다. `2025-13`의 거부는 도메인 규칙이므로 도메인이 판정하게 둔다.

## 접근 경계

`requireSelfOrAdmin(actor, creatorId)`. CREATOR는 `X-Actor-Id`가 경로의 `creatorId`와 같을 때만, ADMIN은 전부 허용한다. **판정 코드는 4.1이 소유하고, 이 엔드포인트에 그 규칙이 붙는다는 사실은 Task 5의 역할 매트릭스가 소유한다.**

## 파일

`application/sale/ListCreatorSalesUseCase.java`. 반환 타입 `SaleWithRefundStatus`도 같은 패키지에 record로 둔다.

테스트는 없다. 4.8이 검증한다.

## 완료 기준

1. `sale-5`를 1월 구간으로 조회해도 환불 상태가 `FULL`이다.
2. `findCancelsBySaleIds`를 쓴다. `findCancels`로 환불 상태를 만들지 않는다.
2-b. 판매 조회는 `SaleQueryPort.findSalesByCreator`를 쓴다. `courseId`가 응답에 담긴다. 애그리게이트를 로딩하지 않는다.
3. `2025-13` 같은 잘못된 값이 `InvalidSettlementPeriod`를 던진다.
4. CREATOR가 타인 목록을 조회하면 `ActorAccessDenied`가 난다.
5. 판매 0건이어도 예외 없이 빈 목록을 돌려준다.
