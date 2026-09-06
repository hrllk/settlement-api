# 유즈케이스 시퀀스

엔드포인트 5개의 흐름. 각 다이어그램은 성공 경로만 그리고, 실패 분기는 표로 뺐다.
분기를 다이어그램에 넣으면 그림이 커지고 정작 중요한 순서가 묻힌다.

**모든 요청은 `ActorContextArgumentResolver`를 먼저 지난다.** `X-Actor-Id`와
`X-Actor-Role` 헤더를 `ActorContext`로 바꾸고, 없거나 형식이 어긋나면 그 자리에서
400으로 끊는다. UC-1에만 그리고 나머지는 생략한다.

**접근 판정은 컨트롤러가 아니라 유스케이스가 한다.** 컨트롤러에 두면 유스케이스를
직접 테스트할 때 경계가 빠지고, 엔드포인트가 늘 때마다 같은 호출을 복제하게 된다.

---

## UC-1. 판매 등록

`POST /api/sales` · ADMIN 전용

```mermaid
sequenceDiagram
    autonumber
    actor C as 클라이언트
    participant R as ArgumentResolver
    participant Ctl as SaleController
    participant UC as RegisterSaleUseCase
    participant Pol as ActorAccessPolicy
    participant Q as SalesQueryPort
    participant S as Sale 애그리게이트
    participant Repo as SaleRepository

    C->>R: POST /api/sales + X-Actor-*
    R-->>Ctl: ActorContext(actorId, ADMIN)
    Ctl->>Ctl: @Valid RegisterSaleRequest
    Ctl->>UC: register(actor, courseId, amount, paidAt)
    UC->>Pol: requireAdmin(actor)
    UC->>Q: courseExists(courseId)
    Q-->>UC: true
    UC->>S: Sale.register(UUID, courseId, amount, paidAt)
    UC->>Repo: save(sale)
    UC-->>Ctl: saleId
    Ctl-->>C: 201 Created + Location
```

`courseExists` 검사가 필수다. FK 제약이 없어 없는 `courseId`로도 행이 들어가고,
그 판매는 `course → creator` 조인을 타는 정산 조회에서 영원히 안 보인다.

식별자는 도메인이 아니라 유스케이스가 만든다. 도메인이 `UUID.randomUUID()`를
부르면 테스트가 결과를 단언할 수 없다.

| 실패 | 지점 | 응답 |
| --- | --- | --- |
| 헤더 누락·형식 오류 | ArgumentResolver | 400 `INVALID_ACTOR_HEADER` |
| 금액 0 이하, 필수 필드 누락 | Bean Validation | 400 `VALIDATION_FAILED` |
| 오프셋 없는 `paidAt` | Jackson 역직렬화 | 400 `MALFORMED_REQUEST` |
| CREATOR가 호출 | ActorAccessPolicy | 403 `ACTOR_ACCESS_DENIED` |
| 없는 강의 | SalesQueryPort | 404 `COURSE_NOT_FOUND` |

---

## UC-2. 취소(환불) 등록

`POST /api/sales/{saleId}/cancellations` · ADMIN 전용

```mermaid
sequenceDiagram
    autonumber
    actor C as 클라이언트
    participant Ctl as SaleController
    participant UC as RegisterCancelUseCase
    participant Repo as SaleRepository
    participant S as Sale 애그리게이트

    C->>Ctl: POST /api/sales/{saleId}/cancellations
    Ctl->>UC: register(actor, saleId, amount, cancelledAt)
    UC->>UC: requireAdmin(actor)
    UC->>Repo: findById(saleId)
    Repo-->>UC: Sale + 기존 취소 전량
    UC->>S: cancel(UUID, amount, cancelledAt)
    Note over S: 누적 환불 ≤ 원결제 판정
    S-->>UC: Cancel
    UC->>Repo: save(sale)
    UC-->>Ctl: cancelId
    Ctl-->>C: 201 Created
```

**유스케이스에 금액 비교문이 없다.** 초과 환불 거부는 애그리게이트 안에 있다.

```java
if (amount > this.amount - already) throw new RefundAmountExceededException(...);
```

`already + amount > this.amount`로 쓰지 않은 것은 오버플로 때문이다.
`Long.MAX_VALUE`가 들어오면 덧셈이 음수로 돌아 검사를 그냥 통과한다. 뺄셈은 두
항 모두 음수가 아니라 넘치지 않는다. `>=`가 아니라 `>`인 것도 의도다. 합계가
원결제와 정확히 같으면 전액 환불이므로 허용해야 한다.

`findById`가 기존 취소를 **전량** 적재하는 것이 이 판정의 전제다. 부분 적재하면
누적 합계가 실제보다 작게 나와 초과 환불이 통과한다.

동시성은 보장하지 않는다. 요청 둘이 동시에 오면 각자 같은 잔여액을 읽고 각자
검사를 통과한다. README 가정 11.

| 실패 | 지점 | 응답 |
| --- | --- | --- |
| 금액 0 이하 | Bean Validation | 400 `VALIDATION_FAILED` |
| CREATOR가 호출 | ActorAccessPolicy | 403 `ACTOR_ACCESS_DENIED` |
| 없는 판매 | SaleRepository | 404 `SALE_NOT_FOUND` |
| 누적 환불 초과 | Sale 애그리게이트 | 409 `REFUND_AMOUNT_EXCEEDED` |

---

## UC-3. 크리에이터 기간별 판매 목록

`GET /api/creators/{creatorId}/sales?from=&to=` · 본인 CREATOR 또는 ADMIN

```mermaid
sequenceDiagram
    autonumber
    actor C as 클라이언트
    participant Ctl as SaleController
    participant UC as ListCreatorSalesUseCase
    participant Per as SettlementPeriod
    participant Q as SalesQueryPort

    C->>Ctl: GET .../sales?from&to
    Ctl->>UC: list(actor, creatorId, from, to)
    UC->>UC: requireSelfOrAdmin(actor, creatorId)
    UC->>Per: ofDateRange(from, to)
    Per-->>UC: [fromInclusive, toExclusive)
    UC->>Q: findSalesForListing(구간, creatorId)
    Note over Q: paid_at 으로 기간 필터
    Q-->>UC: List~SaleRecord~
    UC->>Q: findCancelsBySaleIds(saleIds)
    Note over Q: 기간 조건 없음
    Q-->>UC: List~CancelData~
    UC->>UC: 판매별 합산 → RefundStatus
    UC-->>Ctl: List~SaleWithRefundStatus~
    Ctl-->>C: 200 OK
```

**이 유즈케이스의 유일한 함정은 두 번째 조회에 기간 필터가 없다는 점이다.**

시드의 `sale-5`가 그 이유다. 1월 31일 판매인데 취소는 2월 3일이다. 1월 목록을
조회하면서 취소도 1월로 좁히면 환불 상태가 `FULL`이어야 할 것이 `NONE`으로
나온다. 금액 집계는 기간으로 나뉘고 환불 상태는 나뉘지 않는다.

판매가 0건이면 어댑터가 쿼리 없이 빈 리스트를 돌려준다. 빈 컬렉션을 그대로
JPQL로 내리면 `in ()`이 되고 동작이 dialect마다 갈린다.

| 실패 | 지점 | 응답 |
| --- | --- | --- |
| `from` 또는 `to` 누락 | Spring MVC | 400 `MISSING_PARAMETER` |
| 날짜 형식 오류, 종료일 < 시작일 | SettlementPeriod | 400 `INVALID_SETTLEMENT_PERIOD` |
| CREATOR가 타인 조회 | ActorAccessPolicy | 403 `ACTOR_ACCESS_DENIED` |

없는 크리에이터는 404가 아니라 200 + 빈 배열이다.

---

## UC-4. 크리에이터 월별 정산

`GET /api/creators/{creatorId}/settlements/{yearMonth}` · 본인 CREATOR 또는 ADMIN

```mermaid
sequenceDiagram
    autonumber
    actor C as 클라이언트
    participant Ctl as SettlementController
    participant UC as MonthlySettlementUseCase
    participant Per as SettlementPeriod
    participant SQ as SettlementQuery
    participant Q as SalesQueryPort
    participant Calc as SettlementCalculator

    C->>Ctl: GET .../settlements/2025-03
    Ctl->>UC: settle(actor, creatorId, yearMonth)
    UC->>UC: requireSelfOrAdmin(actor, creatorId)
    UC->>Per: ofYearMonth("2025-03")
    Per-->>UC: [03-01 00:00 KST, 04-01 00:00 KST)
    UC->>SQ: summarize(period, creatorId)
    SQ->>Q: findSales(구간, creatorId)
    Note over Q: paid_at 기준
    SQ->>Q: findCancels(구간, creatorId)
    Note over Q: cancelled_at 기준
    SQ->>Calc: calculate(period, sales, cancels)
    Calc-->>SQ: SettlementSummary
    SQ-->>UC: SettlementSummary
    UC-->>Ctl: SettlementSummary
    Ctl-->>C: 200 OK
```

**유스케이스에 산술이 한 줄도 없다.** 계산은 순수 함수인 계산기가 소유한다.

```text
netSales = grossSales - refunds
fee      = netSales <= 0 ? 0 : netSales * 2000 / 10000
payout   = netSales - fee
```

음수 판정이 나눗셈보다 먼저인 것이 정책 자체다. `long` 나눗셈은 0 방향 절단이라
순서가 바뀌면 −60,000에서 −12,000이 나와 플랫폼이 크리에이터에게 수수료를
돌려주는 셈이 된다.

판매도 취소도 없는 달은 404가 아니라 전 항목 0원 정상 응답이다. 404로 두면
"정산이 없다"와 "크리에이터가 없다"를 클라이언트가 구분할 수 없다.

| 실패 | 지점 | 응답 |
| --- | --- | --- |
| `2025-13` 같은 잘못된 연월 | SettlementPeriod | 400 `INVALID_SETTLEMENT_PERIOD` |
| CREATOR가 타인 조회 | ActorAccessPolicy | 403 `ACTOR_ACCESS_DENIED` |

연월을 `String`으로 받는 이유가 이 표에 있다. `@PathVariable YearMonth`로
바인딩하면 Spring이 `MethodArgumentTypeMismatchException`을 먼저 던져 거부가
도메인이 아니라 프레임워크에서 일어나고 오류 코드가 달라진다.

---

## UC-5. 운영자 기간 정산 집계

`GET /api/admin/settlements?from=&to=` · ADMIN 전용

```mermaid
sequenceDiagram
    autonumber
    actor C as 클라이언트
    participant Ctl as SettlementController
    participant UC as AdminSettlementUseCase
    participant Per as SettlementPeriod
    participant Q as SalesQueryPort
    participant SQ as SettlementQuery

    C->>Ctl: GET /api/admin/settlements?from&to
    Ctl->>UC: aggregate(actor, from, to)
    UC->>UC: requireAdmin(actor)
    UC->>Per: ofDateRange(from, to)
    Per-->>UC: 단일 구간
    UC->>Q: findAllCreatorIds()
    Note over Q: 실적 0인 크리에이터 포함, id 오름차순
    Q-->>UC: [creator-1, creator-2, creator-3]
    loop 크리에이터마다
        UC->>SQ: summarize(period, creatorId)
        SQ-->>UC: SettlementSummary
        UC->>UC: totalPayout += payout
    end
    UC-->>Ctl: AdminSettlement(목록, 합계)
    Ctl-->>C: 200 OK
```

**기간 전체를 단일 구간으로 계산한다. 월별로 계산해 더하지 않는다.**

이 시스템에서 가장 설명하기 어려운 동작이다. `creator-2`의 2025-01~03이 두
방식을 갈라놓는다.

| 방식 | 계산 | 결과 |
| --- | --- | ---: |
| 단일 구간 | 판매 120,000 − 환불 60,000 = 60,000, 수수료 12,000 | 48,000 |
| 월별 합산 | 1월 48,000 + 2월 (−60,000) + 3월 48,000 | 36,000 |

차이 12,000원은 월별 합산일 때 2월의 음수 순매출에 수수료 0원 제한이 걸려 1월에
이미 뗀 수수료가 상쇄되지 않기 때문이다. 전체 합계로는 **264,000 대 252,000**이다.

월별 합산을 안 쓰는 이유는 음수 월마다 수수료 0원 제한이 반복 적용돼 크리에이터에게
불리하고, "왜 월별 합과 다른가"를 설명할 수 없기 때문이다.

`findAllCreatorIds()`가 따로 필요한 이유는 `creator-3`이다. 3월에 판매도 취소도
없어서 판매·취소 자료만 훑으면 존재 자체를 알 수 없고 목록에서 통째로 빠진다.

크리에이터마다 포트를 두 번 부른다(N+1). 3명이라 실측 차이가 0이고, 포트 계약을
"`creatorId` 항상 필수"로 단순하게 유지하기 위한 선택이다. 수천 명이면 다시 볼
지점이다.

| 실패 | 지점 | 응답 |
| --- | --- | --- |
| `from` 또는 `to` 누락 | Spring MVC | 400 `MISSING_PARAMETER` |
| 날짜 형식 오류, 종료일 < 시작일 | SettlementPeriod | 400 `INVALID_SETTLEMENT_PERIOD` |
| CREATOR가 호출 | ActorAccessPolicy | 403 `ACTOR_ACCESS_DENIED` |

---

## 다섯 흐름을 관통하는 것

**접근 경계.** 해석기는 필터가 아니라 파라미터 타입 기반 opt-in이다. `ActorContext`를
선언하지 않은 컨트롤러 메서드는 헤더 검사도 인가 판정도 없이 조용히 열린다.
컴파일러가 못 잡으므로 `ControllerActorGuardTest`가 리플렉션으로 검사한다.
`/api/admin/settlements`가 전체 매출이 나가는 유일한 엔드포인트라 특히 그렇다.

**시간.** KST 반열린 구간 `[시작 00:00, 종료 다음날 00:00)`. 원본 과제의
"말일 23:59:59"는 초 미만이 누락돼 의도적으로 이탈했다. 시간대 지식은
`SettlementPeriod` 한 클래스에만 산다. 나머지는 전부 `Instant`다.

**오류.** `GlobalExceptionHandler`가 전부 RFC 9457 Problem Details로 바꾼다.
`Exception` catch-all을 두지 않아 `NullPointerException` 같은 자체 버그가 400으로
위장되지 않는다.
