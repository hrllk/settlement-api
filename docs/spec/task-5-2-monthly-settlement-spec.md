# Task 5.2 — 크리에이터 월별 정산 명세

부모: [`task-5-settlement-query-api-spec.md`](./task-5-settlement-query-api-spec.md) · 의존 5.1 · 15분 · 테스트 2

## 유스케이스

조회와 계산은 5.3과 똑같으므로 **공유 협력자로 뺀다.**

```java
package com.liveclass.settlement.application.settlement;

@Component
public class SettlementQuery {                       // 5.2와 5.3이 함께 쓴다

    public SettlementSummary summarize(SettlementPeriod period, String creatorId) {
        return calculator.calculate(
                period,
                queryPort.findSales(period.fromInclusive(), period.toExclusive(), creatorId),
                queryPort.findCancels(period.fromInclusive(), period.toExclusive(), creatorId));
    }
}

@Service
public class MonthlySettlementUseCase {

    @Transactional(readOnly = true)
    public SettlementSummary settle(ActorContext actor, String creatorId, String yearMonth) {
        accessPolicy.requireSelfOrAdmin(actor, creatorId);
        SettlementPeriod period = SettlementPeriod.ofYearMonth(yearMonth);
        SettlementSummary summary = query.summarize(period, creatorId);
        log.info(...);
        return summary;
    }
}
```

**`SettlementQuery`를 빼는 이유는 세 줄을 아끼려는 게 아니다.** 이 세 줄이 프로젝트의 핵심 계산 진입점이고, 5.2와 5.3에 각자 복사돼 있으면 조회 순서나 기간 처리가 바뀔 때 한쪽만 고치는 사고가 난다. 그러면 같은 크리에이터의 월별 응답과 운영자 응답이 조용히 달라진다.

유스케이스에 산술이 한 줄도 없어야 한다. 계산은 Task 3의 계산기가 소유한다.

**`@Transactional(readOnly = true)`를 붙인다.** 안 붙이면 `findSales`와 `findCancels`가 각자 트랜잭션을 연다. 한 응답의 두 조회가 서로 다른 시점을 볼 수 있고, 커넥션도 두 번 빌린다. Task 4의 `ListCreatorSalesUseCase`가 같은 규칙이다.

## 연월을 `String`으로 받는다

`SettlementPeriod.ofYearMonth(String)`가 파싱과 검증을 소유한다. `2025-13`, `2025/03`, 빈 값이 `InvalidSettlementPeriod`가 되고 Task 4의 처리기가 400으로 바꾼다.

컨트롤러가 `@PathVariable YearMonth`로 바인딩하면 Spring이 `MethodArgumentTypeMismatchException`을 먼저 던져 도메인 판정이 영영 안 걸린다.

## 빈 월은 200이다

creator-3의 2025-03은 판매도 취소도 없다. 포트가 빈 리스트 둘을 돌려주고 계산기가 전 항목 0인 요약을 만든다. **404가 아니다.**

원본 과제가 "0원 응답 또는 처리 방침 일관성 확인"을 요구한다. 0원으로 통일한다. 404로 두면 "정산이 없다"와 "크리에이터가 없다"를 클라이언트가 구분할 수 없다.

## 없는 크리에이터도 200이다

`GET /api/creators/없는아이디/settlements/2025-03`을 ADMIN이 부르면 **404가 아니라 200 + 전 항목 0**이다. 존재 여부를 확인하지 않는다.

Task 4의 판매 등록은 `courseExists`로 404를 낸다. 기준이 다른 것이 아니라 **쓰기와 조회의 위험이 다르다.** 없는 강의로 판매를 넣으면 어떤 크리에이터에도 속하지 않는 유령 행이 영구히 남는다. 없는 크리에이터를 조회하면 0이 나오고 끝이다.

조회 두 엔드포인트(`/sales`, `/settlements/{ym}`)가 같은 규칙을 쓴다. 한쪽만 404로 바꾸면 그때 진짜 불일치가 생긴다. README 가정에 한 줄 남긴다.

## 이중 집계 기준

판매는 `paidAt`, 환불은 `cancelledAt`으로 각각 귀속 기간을 판단한다. 포트 메서드 두 개가 서로 다른 컬럼으로 같은 구간을 자른다.

**금액뿐 아니라 건수도 같은 기준을 따른다.** creator-2의 2025-02는 판매 건수 0, 취소 건수 1이다. 판매가 없는데 취소만 있는 달이 실재한다.

이 달이 음수 순 판매액에 수수료 0원 제한이 걸리는 유일한 케이스다. 계산기가 그 규칙을 소유하고 Task 3이 단언한다. 여기서 다시 단언하지 않는다.

## 테스트

`SettlementControllerTest`에 둔다. `@SpringBootTest` + `@AutoConfigureMockMvc`.

| # | 케이스 | 기대 |
| --- | --- | --- |
| 1 | creator-1 본인이 2025-03 조회 | 200, `payout` 120,000, `saleCount` 4, `cancelCount` 2 |
| 2 | creator-3 본인이 2025-03 조회 (빈 월) | 200, 7개 필드 전부 0 |

1번은 계산 재검증이 아니라 **배선 검증**이다. 포트 → 계산기 → 응답 DTO가 이어졌는지 본다. 계산 자체의 정확성은 Task 3이 단위로 잠갔다. 값을 단언하는 이유는 200만 보면 배선이 끊겨 0이 나와도 통과하기 때문이다.

## 파일

`application/settlement/SettlementQuery.java`, `MonthlySettlementUseCase.java`.

## 완료 기준

1. 유스케이스에 산술이 없다.
1-b. 조회와 계산이 `SettlementQuery` 한 곳에만 있고 5.3이 같은 것을 쓴다.
2. 연월이 `String`이고 `ofYearMonth`가 파싱한다.
3. 빈 월이 200 + 전 항목 0이다.
4. `requireSelfOrAdmin`을 호출한다.
4-b. `@Transactional(readOnly = true)`가 붙어 있다.
5. 테스트 2건이 통과한다.
