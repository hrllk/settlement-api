# Task 3.3 — `FeePolicy` 명세

부모: [`task-3-settlement-domain-spec.md`](./task-3-settlement-domain-spec.md) · 의존 없음 (3.1과 병렬, 3.2보다 먼저) · 10분

## 타입

```java
package com.liveclass.settlement.domain.settlement;

public interface FeePolicy {
    long calculate(long netSales);
}

public record FixedRateFeePolicy(int basisPoints) implements FeePolicy {

    public static final int PLATFORM_DEFAULT_BP = 2_000;   // 20%

    public FixedRateFeePolicy {
        if (basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("basisPoints must be 0..10000: " + basisPoints);
        }
    }

    @Override
    public long calculate(long netSales) {
        if (netSales <= 0) return 0L;
        return netSales * basisPoints / 10_000L;
    }
}
```

호출부는 `new FixedRateFeePolicy(PLATFORM_DEFAULT_BP)`를 쓴다. 별도 정적 팩토리는 두지 않는다.

## 결정

인터페이스를 두는 이유는 원본 과제가 "수수료율 변경 가능성을 설계에 반영하면 가산점"을 명시했기 때문이다. 요율 이력과 시점별 적용은 만들지 않는다. 교체 지점만 열어 둔다.

basis point는 `double` 없이 요율을 정수로 표현하려는 것이다. 20%를 `0.2`로 두면 부동소수점이 금액 계산에 끼어든다.

**음수 판정이 나눗셈보다 먼저인 이유는 정책이다.** 음수 매출에는 수수료를 부과하지 않는다. 나중에 판정하면 `-60,000`에서 `-12,000`이 나와 플랫폼이 수수료를 돌려주는 셈이 된다. 양수 구간에서 `long` 나눗셈의 절단이 곧 버림이다.

수수료는 0으로 막고 정산 예정액은 음수를 허용하는 비대칭을 README에 적는다. 수수료는 매출에 부과하는 값이라 매출이 음수면 부과 대상이 없다. 정산 예정액은 채권·채무라 음수가 의미를 갖는다.

이 정책이 실제로 걸리는 유일한 시나리오는 creator-2의 2025-02다. 1월 판매분이 2월에 취소되어 순 판매액이 `-60,000`, 수수료 0원, 정산 예정액 `-60,000`이 된다.

## 테스트 — `FixedRateFeePolicyTest` 6건

요율 범위 케이스가 두 값으로 파라미터화되어 실행 시 6건이 된다.

| 입력 | 기대 |
| ---: | ---: |
| 150,000 | 30,000 |
| 0 | 0 |
| -60,000 | 0 |
| 33,333 | 6,666 (실제 6,666.6) |
| `new FixedRateFeePolicy(-1)`, `(10001)` | `IllegalArgumentException` |

버림 케이스가 필요한 이유는 샘플 금액이 전부 5의 배수라 20%가 정수로 떨어지기 때문이다. 제공 시나리오만으로는 반올림 정책이 검증되지 않는다. 원본 과제가 가산점을 건 항목이다.

## 파일

`domain/settlement/`에 `FeePolicy.java`, `FixedRateFeePolicy.java`. 테스트는 `FixedRateFeePolicyTest.java`.

## 완료 기준

1. 6건 통과.
2. 음수 판정이 나눗셈보다 먼저 실행된다.
3. `double`이나 `BigDecimal`을 쓰지 않는다.
4. 요율 이력이나 시점별 적용을 만들지 않는다.
