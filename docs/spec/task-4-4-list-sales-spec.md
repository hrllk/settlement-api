# Task 4.4 — 크리에이터별 기간 판매 목록 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 의존 4.1, **4.2** (`SaleRecord`와 `findSalesForListing`을 4.2가 선언한다) · 15분

## 유스케이스

```java
@Service
public class ListCreatorSalesUseCase {

    public List<SaleWithRefundStatus> list(ActorContext actor, String creatorId,
                                           String from, String to) {
        accessPolicy.requireSelfOrAdmin(actor, creatorId);

        SettlementPeriod period = SettlementPeriod.ofDateRange(from, to);
        // 포트는 하나다. 판매와 취소를 같은 SalesQueryPort에서 가져온다.
        List<SaleRecord> sales = queryPort.findSalesForListing(
                period.fromInclusive(), period.toExclusive(), creatorId);

        List<CancelData> cancels = queryPort.findCancelsBySaleIds(
                sales.stream().map(SaleRecord::saleId).toList());

        Map<String, List<CancelData>> bySale =
                cancels.stream().collect(groupingBy(CancelData::saleId));

        return sales.stream()
                .map(s -> new SaleWithRefundStatus(s, refundStatusOf(s, bySale)))
                .toList();
    }
}
```

## `RefundStatus`는 2인자 오버로드를 쓴다

Task 3의 실제 시그니처는 둘뿐이다.

```java
public static RefundStatus of(SaleData sale, Collection<CancelData> cancelsOfSale)
public static RefundStatus of(long saleAmount, long cancelledTotal)
```

**`SaleRecord`를 받는 오버로드는 없다.** 목록 조회가 `SaleData`에서 `SaleRecord`로 바뀌면서 첫 번째 오버로드를 쓸 수 없게 됐다. 취소 금액을 합해 2인자 쪽을 부른다.

```java
private static RefundStatus refundStatusOf(SaleRecord s, Map<String, List<CancelData>> bySale) {
    long cancelled = bySale.getOrDefault(s.saleId(), List.of())
                           .stream().mapToLong(CancelData::amount).sum();
    return RefundStatus.of(s.amount(), cancelled);
}
```

Task 3에 `SaleRecord` 오버로드를 추가하지 않는다. `SaleRecord`는 `application.port.out`의 읽기 모델이고 도메인이 그걸 알면 방향이 뒤집힌다.

## 반환 타입

```java
package com.liveclass.settlement.application.sale;

public record SaleWithRefundStatus(SaleRecord sale, RefundStatus refundStatus) { }
```

4.6 컨트롤러가 이걸 4.5의 `SaleItem`으로 옮긴다. `sale`을 통째로 들고 있어 `saleId`·`courseId`·`amount`·`paidAt`을 다 꺼낼 수 있다.

## `findSales`가 아니라 `findSalesForListing`을 쓴다

응답의 `SaleItem`에는 `courseId`가 들어간다. 그런데 Task 3의 `SaleData`에는 `courseId`가 없다.

```java
public record SaleData(String saleId, String creatorId, long amount, Instant paidAt) { }
```

Task 3이 계산에 안 쓰는 필드를 의도적으로 뺀 것이고 그 결정은 옳다. 판매 목록은 계산이 아니라 조회이므로 **4.2의 `SalesQueryPort.findSalesForListing`를 쓴다.** 그쪽 `SaleRecord`가 `courseId`를 갖는다. 목록은 애그리게이트를 쓰지 않는다 — N개를 로딩하면 각각 자기 취소를 딸고 와 N+1이 된다.

취소도 같은 포트의 `findCancelsBySaleIds`를 쓴다. Task 3이 환불 상태 산출을 위해 만든 메서드이고 `CancelData`에 부족한 필드가 없다.

**주입받는 의존은 `SalesQueryPort` 하나와 `ActorAccessPolicy` 둘뿐이다.** 조회 포트를 정산용과 판매용으로 나누지 않은 이유는 `findSales`와 `findSalesForListing`이 인자가 같고 반환 모델만 다르기 때문이다.

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

`application/sale/ListCreatorSalesUseCase.java`, `SaleWithRefundStatus.java`.

테스트는 없다. 4.8이 검증한다.

## 완료 기준

1. `sale-5`를 1월 구간으로 조회해도 환불 상태가 `FULL`이다.
2. `findCancelsBySaleIds`를 쓴다. `findCancels`로 환불 상태를 만들지 않는다.
2-b. 판매 조회는 `SalesQueryPort.findSalesForListing`를 쓴다. `courseId`가 응답에 담긴다. 애그리게이트를 로딩하지 않는다.
2-d. 조회 포트를 하나만 주입받는다. 판매와 취소를 서로 다른 빈에서 가져오지 않는다.
2-c. `RefundStatus.of(long, long)` 2인자 오버로드를 쓴다. Task 3에 오버로드를 추가하지 않는다.
3. `2025-13` 같은 잘못된 값이 `InvalidSettlementPeriod`를 던진다.
4. CREATOR가 타인 목록을 조회하면 `ActorAccessDenied`가 난다.
5. 판매 0건이어도 예외 없이 빈 목록을 돌려준다.
