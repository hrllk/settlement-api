# Task 3.3 — `FeePolicy` 명세

부모: [`task-3-settlement-domain-spec.md`](./task-3-settlement-domain-spec.md) · 의존 없음 (3.1과 병렬, 3.2보다 먼저) · 10분

## 타입

```java
package com.liveclass.settlement.domain.settlement;

public interface FeePolicy {
    long calculate(long netSales);
}

public record FixedRateFeePolicy(int basisPoints) implements FeePolicy {

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

**요율 값은 도메인이 갖지 않는다.** 도메인이 "현재 플랫폼 요율이 20%"라는 사업 사실을 알 이유가 없다. 값은 `application.yml`의 `settlement.fee.basis-points`에 두고 Task 4가 `@ConfigurationProperties`로 바인딩해 조립한다. Task 4가 Spring 배선이 생기는 첫 Task라 도메인 빈을 먼저 필요로 한다. 정적 팩토리도 두지 않는다.

## 결정

원본 과제가 "수수료율 변경 가능성을 설계에 반영하면 가산점"을 명시했다. 세 단계로 나뉜다.

| 단계 | 내용 | 채택 |
| --- | --- | --- |
| 1 | `net * 0.2`를 계산식에 박는다 | 아니오 |
| 2 | 정책 인터페이스 + 설정 주입 | **예** |
| 3 | 요율 이력 테이블 + 시점별 적용 | 아니오 (선택 구현) |

**3단계를 절반만 하면 안 된다.** 요율을 저장만 하고 이력을 안 두면, 요율이 바뀌는 순간 이미 조회한 과거 정산이 조용히 달라진다. 3월을 조회해 120,000원을 받았는데 요율 변경 후 다시 조회하면 112,500원이 나오는 식이다. 원본 과제도 선택 구현 항목에 "과거 정산은 당시 수수료율 적용"을 괄호로 달아 이 함정을 짚었다.

2단계에서 멈춘 것은 의도적이다. 3단계로 가는 경로는 `FeePolicy`를 `FeePolicyResolver.resolve(period)`로 바꾸는 것이며, 근거와 함께 README에 남긴다.

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

테스트는 요율 20%를 `SettlementFixtures.PLATFORM_FEE_BP`에서 가져온다. 운영 코드가 값을 갖지 않으므로 기대값 계산용 상수는 테스트 쪽에 둔다.

버림 케이스가 필요한 이유는 샘플 금액이 전부 5의 배수라 20%가 정수로 떨어지기 때문이다. 제공 시나리오만으로는 반올림 정책이 검증되지 않는다. 원본 과제가 가산점을 건 항목이다.

## 파일

`domain/settlement/`에 `FeePolicy.java`, `FixedRateFeePolicy.java`. 테스트는 `FixedRateFeePolicyTest.java`.

## 완료 기준

1. 6건 통과.
2. 음수 판정이 나눗셈보다 먼저 실행된다.
3. `double`이나 `BigDecimal`을 쓰지 않는다.
4. 요율 값이 도메인 코드에 없다. `application.yml`에 있고 Task 4가 주입한다.
5. 요율 이력이나 시점별 적용을 만들지 않는다.
