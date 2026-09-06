# Task 3.2 — 값 타입과 환불 상태 명세

부모: [`task-3-settlement-domain-spec.md`](./task-3-settlement-domain-spec.md) · 의존 3.3 (`SettlementSummary.of`가 `FeePolicy`를 받는다) · 20분

## 타입

```java
package com.liveclass.settlement.domain.settlement;

public record SaleData(String saleId, String creatorId, long amount, Instant paidAt) {
    public SaleData {
        Objects.requireNonNull(saleId, "saleId");
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(paidAt, "paidAt");
    }
}

public record CancelData(String cancelId, String saleId, long amount, Instant cancelledAt) {
    public CancelData {
        Objects.requireNonNull(cancelId, "cancelId");
        Objects.requireNonNull(saleId, "saleId");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
    }
}

public record SettlementSummary(
        long grossSales, int saleCount,
        long refunds,    int cancelCount,
        long netSales,   long fee, long payout) {

    public SettlementSummary {
        if (netSales != grossSales - refunds) throw new IllegalArgumentException("netSales != grossSales - refunds");
        if (payout != netSales - fee)         throw new IllegalArgumentException("payout != netSales - fee");
    }

    public static SettlementSummary of(long grossSales, int saleCount,
                                       long refunds, int cancelCount, FeePolicy feePolicy) {
        long net = grossSales - refunds;
        long fee = feePolicy.calculate(net);
        return new SettlementSummary(grossSales, saleCount, refunds, cancelCount, net, fee, net - fee);
    }
}

public enum RefundStatus {
    NONE, PARTIAL, FULL;

    public static RefundStatus of(SaleData sale, Collection<CancelData> cancelsOfSale) {
        Objects.requireNonNull(cancelsOfSale, "cancelsOfSale");
        long total = 0;
        for (CancelData c : cancelsOfSale) {
            if (c.saleId().equals(sale.saleId())) total += c.amount();
        }
        return of(sale.amount(), total);
    }

    public static RefundStatus of(long saleAmount, long cancelledTotal) {
        if (cancelledTotal <= 0)          return NONE;
        if (cancelledTotal >= saleAmount) return FULL;
        return PARTIAL;
    }
}
```

## 결정

`courseId`, `studentId`는 계산에 안 쓰이므로 넣지 않는다. JPA 엔티티를 domain으로 끌어오지 않는다.

식별자는 `String`이다. `CreatorId` / `SaleId` 래퍼는 만들지 않는다. 원본 데이터가 `creator-1` 같은 문자열이라 래핑 이득이 작다. README에 남긴다.

`CancelData`에 `creatorId`가 없는 것은 의도적이다. 포트(3.5)가 취소를 항상 크리에이터로 좁혀 조회한다.

`requireNonNull`에 필드명을 넘긴다. 없으면 어느 필드인지 안 보여 방어 목적을 절반만 달성한다. `amount` 부호는 검증하지 않는다 — 등록 시점 규칙이라 Task 4 소관이다.

`netSales`, `fee`, `payout`은 파생값이다. record 표준 생성자가 public이라 팩토리만으로는 어긋난 인스턴스를 못 막는다. compact 생성자가 있어야 "세 값이 어긋난 인스턴스는 존재할 수 없다"가 참이 된다.

`RefundStatus`가 `>=`인 이유: 초과 등록은 Task 4가 거부하지만 Task 4는 나중이다. "방어하지 않는다"는 코드로 표현할 수 없으므로 `>=`로 분기를 닫는다.

**합산을 도메인에 두는 것이 이 서브태스크의 핵심이다.** 미리 더한 숫자만 받으면 "그 판매의 모든 취소를 본다"는 규칙이 Task 4 코드로 밀려나고, 거기서 기간 필터를 잘못 적용하면 sale-5가 `NONE`으로 나온다. 첫 오버로드가 `saleId`만 보고 시각은 전혀 안 보므로 규칙이 도메인 안에서 강제되고 테스트로 잠긴다. 시그니처에 `SettlementPeriod`가 없는 것이 그 보장이다.

## 테스트

### `SettlementSummaryTest` 2건

| 입력 | 기대 |
| --- | --- |
| `new SettlementSummary(100, 1, 30, 1, 99, 0, 99)` | `IllegalArgumentException` (첫 검증) |
| `new SettlementSummary(100, 1, 30, 1, 70, 14, 99)` | `IllegalArgumentException` (둘째 검증) |

인자를 전부 적어 어느 검증이 발동하는지 분리한다. 첫 케이스는 `payout=99=99-0`이라 둘째를 통과하고, 둘째는 `netSales=70=100-30`이라 첫째를 통과한다. 한 케이스가 둘 다 걸리면 어느 쪽이 도는지 증명하지 못한다. 정상 경로는 3.4가 덮는다.

### `RefundStatusTest` 7건

| 케이스 | 입력 | 기대 |
| --- | --- | --- |
| 취소 없음 | sale-1, 빈 목록 | `NONE` |
| 부분 환불 | sale-4 (80,000), 30,000 | `PARTIAL` |
| 전액 환불 | sale-3 (80,000), 80,000 | `FULL` |
| 초과 입력 | 80,000, 합계 90,000 | `FULL` |
| **기간 무관** | sale-5 (1/31 결제), cancel-3 (2/3 취소) | `FULL` |
| 타 판매 배제 | sale-1 + sale-3의 취소 | `NONE` |
| null 목록 | sale-1, `null` | `NullPointerException("cancelsOfSale")` |

**기간 무관이 이 태스크 최우선 회귀 방지 테스트다.** 판매와 취소의 월이 다른데도 `FULL`이어야 한다. 타 판매 배제는 `saleId` 조건이 실제로 걸려 있는지 본다.

## 파일

`domain/settlement/`에 `SaleData`, `CancelData`, `SettlementSummary`, `RefundStatus`. 테스트는 `SettlementSummaryTest`, `RefundStatusTest`. 3.1보다 먼저 끝나면 `domain/.gitkeep`을 지운다.

## 완료 기준

1. 9건 통과.
2. 환불 상태의 기간 무관 단언이 있다.
3. `RefundStatus.of` 시그니처에 `SettlementPeriod`가 없다.
4. 네 타입 전부 불변식을 갖는다.
5. Spring 애노테이션과 로깅이 없다.
