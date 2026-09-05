# Task 3.4 — `SettlementCalculator` 명세

부모: [`task-3-settlement-domain-spec.md`](./task-3-settlement-domain-spec.md) · 의존 3.1, 3.2, 3.3 · 15분

## 타입

```java
package com.liveclass.settlement.domain.settlement;

public final class SettlementCalculator {

    private final FeePolicy feePolicy;

    public SettlementCalculator(FeePolicy feePolicy) { this.feePolicy = feePolicy; }

    public SettlementSummary calculate(SettlementPeriod period,
                                       List<SaleData> sales, List<CancelData> cancels) {
        long gross = 0; int saleCount = 0;
        for (SaleData s : sales) {
            if (period.contains(s.paidAt())) { gross += s.amount(); saleCount++; }
        }
        long refunds = 0; int cancelCount = 0;
        for (CancelData c : cancels) {
            if (period.contains(c.cancelledAt())) { refunds += c.amount(); cancelCount++; }
        }
        return SettlementSummary.of(gross, saleCount, refunds, cancelCount, feePolicy);
    }
}
```

## 결정

판매는 `paidAt`, 환불은 `cancelledAt`으로 귀속 기간을 판단한다. **금액뿐 아니라 건수도 같은 기준을 따른다** — creator-2의 2025-02는 판매 건수 0, 취소 건수 1이다.

계산기가 기간 필터를 다시 적용하는 것은 의도적이다. 포트가 이미 창으로 좁혀 주지만, `(기간, 판매, 취소)`의 순수 함수여야 단위 테스트가 포트 없이 성립한다. 포트가 넓게 반환해도 결과가 같다.

크리에이터별 그룹핑은 하지 않는다. 입력이 이미 한 크리에이터의 것이라고 가정한다. 목록 조립과 전체 합계는 Task 5의 몫이다.

로그를 남기지 않는다. 넣으면 도메인에 SLF4J가 딸려온다. Spring 애노테이션도 쓰지 않는다.

## 입력 픽스처

| 판매 | 크리에이터 | 금액 | `paidAt` |
| --- | --- | ---: | --- |
| sale-1 | creator-1 | 50,000 | `2025-03-05T10:00:00+09:00` |
| sale-2 | creator-1 | 50,000 | `2025-03-15T14:30:00+09:00` |
| sale-3 | creator-1 | 80,000 | `2025-03-20T09:00:00+09:00` |
| sale-4 | creator-1 | 80,000 | `2025-03-22T11:00:00+09:00` |
| sale-5 | creator-2 | 60,000 | `2025-01-31T23:30:00+09:00` |
| sale-6 | creator-2 | 60,000 | `2025-03-10T16:00:00+09:00` |
| sale-7 | creator-3 | 120,000 | `2025-02-14T10:00:00+09:00` |

| 취소 | 원본 | 금액 | `cancelledAt` |
| --- | --- | ---: | --- |
| cancel-1 | sale-3 | 80,000 | `2025-03-25T12:00:00+09:00` |
| cancel-2 | sale-4 | 30,000 | `2025-03-26T12:00:00+09:00` |
| cancel-3 | sale-5 | 60,000 | `2025-02-03T12:00:00+09:00` |

판매 7건은 원본 과제 샘플이다. 취소 3건은 원본에 없어 직접 정의했다 — 금액과 귀속 월은 `tasks.json`이 확정했고 시각은 정오로 고정했다. 월 경계에서 떨어뜨려 경계 검증(3.1)과 집계 검증을 분리한다.

**`Instant`를 손으로 변환하지 않는다.**

```java
private static Instant kst(String iso) { return OffsetDateTime.parse(iso).toInstant(); }
```

위 문자열을 그대로 넘긴다. UTC로 바꿔 적으면 sale-5가 `2025-01-31T14:30:00Z`인데 한 자리만 틀려도 1월 판매가 2월로 넘어가고 기준표 3행이 동시에 깨진다.

## 기대값

### 월별 6행

| 크리에이터 | 월 | 총 판매(건) | 환불(건) | 순 판매 | 수수료 | 정산 예정 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| creator-1 | 2025-03 | 260,000 (4) | 110,000 (2) | 150,000 | 30,000 | 120,000 |
| creator-2 | 2025-01 | 60,000 (1) | 0 (0) | 60,000 | 12,000 | 48,000 |
| creator-2 | 2025-02 | 0 (0) | 60,000 (1) | -60,000 | 0 | -60,000 |
| creator-2 | 2025-03 | 60,000 (1) | 0 (0) | 60,000 | 12,000 | 48,000 |
| creator-3 | 2025-02 | 120,000 (1) | 0 (0) | 120,000 | 24,000 | 96,000 |
| creator-3 | 2025-03 | 0 (0) | 0 (0) | 0 | 0 | 0 |

creator-1의 2025-03은 원본 과제 명시 기대값이다. 평가자가 가장 먼저 보는 숫자라 여기가 틀리면 나머지가 다 맞아도 의미가 없다. creator-2의 2025-02는 수수료 0원 제한이 걸리는 유일한 케이스다. creator-3의 2025-03은 빈 월이며 404가 아니라 0원 응답이다.

### 크리에이터별 기간 정산 예정액 6값

| 기간 | creator-1 | creator-2 | creator-3 |
| --- | ---: | ---: | ---: |
| 2025-03-01 ~ 2025-03-31 | 120,000 | 48,000 | 0 |
| 2025-01-01 ~ 2025-03-31 | 120,000 | 48,000 | 96,000 |

**1~3월의 creator-2가 48,000원이다.** 월별 세 값을 더하면 36,000원이다. 차이 12,000원은 월별 합산일 때 2월 음수 순 판매액에 수수료 0원 제한이 걸려 1월 수수료가 상쇄되지 않기 때문이다. 기간 전체를 단일 구간으로 계산하는 것이 확정 정책이다. 목록 조립과 전체 합계(168,000 / 264,000)는 Task 5가 단언한다.

## 테스트 — `SettlementCalculatorTest` 12건

위 6행 + 6값에 둘을 더한다.

| 케이스 | 입력 | 기대 |
| --- | --- | --- |
| 빈 입력 | 판매 0건, 취소 0건 | 전 항목 0, 건수 0 |
| 동일 판매 다수 부분 취소 | 원결제 80,000에 30,000 + 20,000 | 환불 50,000, 취소 건수 2 |
| **요율 교체** | 1000bp로 creator-1의 2025-03 | 수수료 15,000, 정산 예정 135,000 |
| 정책 누락 | `new SettlementCalculator(null)` | `NullPointerException("feePolicy")` |

월별 6건은 각 행을 별도 테스트로, 기간 6값은 기간별 1건씩 총 2건으로 묶는다.

픽스처는 테스트 코드 안에서만 만든다. Task 2의 시드에 넣으면 creator-1의 3월 기대값 120,000원이 깨진다. 현재 시각을 읽지 않는다.

Task 6은 이 단언을 반복하지 않는다. Task 6이 추가하는 것은 누적 초과 환불 거부, 운영자 목록 조립과 전체 합계, HTTP 통합 1건이다.

## 파일

`domain/settlement/SettlementCalculator.java`, `test/.../SettlementCalculatorTest.java`.

## 완료 기준

1. 12건 통과, Spring 컨텍스트와 H2 없이 돈다.
2. creator-1의 2025-03 정산 예정액이 120,000원이다.
3. creator-2의 2025-02에서 수수료 0원, 정산 예정액 -60,000원이다.
4. 건수가 금액과 같은 이중 기준을 따른다.
5. 테스트가 현재 시각을 읽지 않는다.
6. 요율을 바꾸면 결과가 따라 바뀐다. 이 케이스가 없으면 `SettlementSummary.of`가 20%를 하드코딩해도 전부 통과한다.
7. Spring 애노테이션과 로깅이 없다.
