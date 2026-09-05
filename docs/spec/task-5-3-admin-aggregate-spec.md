# Task 5.3 — 운영자 기간 집계 명세

부모: [`task-5-settlement-query-api-spec.md`](./task-5-settlement-query-api-spec.md) · 의존 5.1 · 20분 · 테스트 3

**이 서브태스크가 Task 5의 핵심이다.** 여기서 정한 계산 방식이 평가자에게 가장 설명하기 어려운 동작을 만든다.

## 유스케이스

```java
@Service
public class AdminSettlementUseCase {

    public AdminSettlement aggregate(ActorContext actor, String from, String to) {
        accessPolicy.requireAdmin(actor);

        SettlementPeriod period = SettlementPeriod.ofDateRange(from, to);

        List<CreatorPayout> items = new ArrayList<>();
        long totalPayout = 0;

        for (String creatorId : dataPort.findAllCreatorIds()) {     // 실적 0도 포함, 정렬됨
            SettlementSummary s = query.summarize(period, creatorId);   // 5.2와 같은 경로
            items.add(new CreatorPayout(creatorId, s));
            totalPayout += s.payout();
        }

        log.info(...);
        return new AdminSettlement(period, items, totalPayout);
    }
}
```

## 기간 전체를 단일 구간으로 계산한다

**월별로 계산해 더하지 않는다.** 계산기를 기간마다 한 번씩 부르는 것이 아니라, 전체 기간에 대해 **크리에이터당 한 번씩** 부른다.

creator-2의 2025-01-01~03-31이 두 방식을 갈라놓는다.

| 방식 | 과정 | 결과 |
| --- | --- | --- |
| **단일 구간 (채택)** | 총 판매 120,000 (sale-5 + sale-6) − 환불 60,000 (cancel-3) = 순 60,000 → 수수료 12,000 | **48,000** |
| 월별 합산 | 1월 48,000 + 2월 (−60,000) + 3월 48,000 | 36,000 |

12,000원 차이의 원인은 수수료 0원 제한이다. 월별로 자르면 2월의 순 판매액이 −60,000이 되어 수수료가 0으로 막히고, 1월에 이미 뗀 12,000원이 상쇄되지 않는다. 기간 전체로 보면 1월 매출과 2월 환불이 먼저 상쇄되고 남은 60,000에만 수수료가 붙는다.

전체 합계로는 단일 구간 **264,000**, 월별 합산 252,000이다.

**월별 합산을 안 쓰는 이유.** 음수 월마다 제한이 반복 적용되어 크리에이터에게 불리하고, "왜 월별 합과 다른가"를 설명할 수 없다. 단일 구간은 계산기를 기간만 바꿔 그대로 재사용하므로 "순수 계산기" 설계와도 맞는다.

**이 판단의 최대 리스크는 정책이 아니라 미문서화다.** 평가자가 creator-2를 월별로 세 번 조회하고 운영자 조회와 비교하면 48,000과 36,000으로 갈린다. Task 7 README 최우선 항목이다.

## 크리에이터 목록 순서를 고정한다

`findAllCreatorIds()`의 순서가 곧 응답 배열의 순서다. Task 2의 `CreatorJpaRepository.findAll()`은 정렬을 안 주므로 SQL이 돌려주는 대로 나오고, 그건 보장된 순서가 아니다.

**어댑터에서 `creatorId` 오름차순으로 정렬한다.** 전체 합계는 순서와 무관하지만 목록은 순서가 결과의 일부다. 정렬이 없으면 README curl 예시의 응답이 실행마다 달라지고, Task 6.3의 "두 번 연속 같은 결과" 점검도 흔들린다.

## 5.2와 같은 경로를 탄다

`query.summarize`는 5.2가 쓰는 것과 동일한 `SettlementQuery`다. 월별 조회와 운영자 집계가 **같은 코드로 계산된다는 것이 코드에 드러난다.**

둘이 갈라져 있으면 조회 순서나 기간 처리가 바뀔 때 한쪽만 고쳐 응답이 조용히 달라진다. 이 프로젝트에서 가장 설명하기 어려운 동작(월별 합 ≠ 기간 집계)이 걸린 경로라 특히 위험하다. **차이는 기간 하나뿐이어야 하고, 그것을 구조로 보장한다.**

## `findAllCreatorIds()`가 필요한 이유

creator-3은 2025-03에 판매도 취소도 없다. 판매·취소 자료만 훑으면 그 존재를 알 방법이 없어 목록에서 통째로 빠진다. 그러면 운영자 화면에 크리에이터가 두 명만 보이고, 세 번째가 왜 없는지 설명할 수 없다.

크리에이터 테이블을 따로 읽어야 실적 0인 사람도 0원으로 목록에 넣을 수 있다.

## N+1을 감수한다

크리에이터마다 포트를 두 번 부른다. 3명이면 조회 7회다. 한 번에 전부 긁어 메모리에서 그룹핑하는 방법도 있지만, 그러면 포트에 "전체 조회" 경로가 하나 더 생긴다.

Task 3이 포트 계약을 "`creatorId`는 항상 필수"로 잡았다. 조회 경로가 하나뿐이라 계약이 단순하다. 크리에이터가 3명이라 실측 차이가 0이다. **이 선택과 그 한계를 README에 남긴다** — 크리에이터가 수천 명이면 다시 봐야 할 지점이다.

## 테스트

`SettlementControllerTest`에 둔다.

| # | 케이스 | 기대 |
| --- | --- | --- |
| 1 | ADMIN, 2025-03-01~03-31 | 목록 3건, `totalPayout` **168,000** |
| 2 | ADMIN, 2025-01-01~03-31 | 목록 3건, `totalPayout` **264,000** |
| 3 | 2025-03 목록의 creator-3 | `payout` 0, 목록에 존재 |

**2번이 이 서브태스크의 유일한 회귀 방어선이다.** 월별 합산으로 잘못 구현해도 1번(2025-03)은 정답이 나온다. 3월에는 음수 월이 없기 때문이다. 2번이 없으면 이 버그가 통과한다.

## 파일

`application/settlement/AdminSettlementUseCase.java`. 반환 타입 `AdminSettlement`, `CreatorPayout`도 같은 패키지에 record로 둔다.

## 완료 기준

1. 기간 전체를 단일 구간으로 계산한다. 월별 루프가 없다.
1-b. 5.2와 같은 `SettlementQuery`를 쓴다. 조회·계산을 복제하지 않는다.
2. `findAllCreatorIds()`로 목록을 받는다.
3. 2025-01~03 전체 합계가 264,000이다.
4. 실적 0인 크리에이터가 0원으로 포함된다.
4-b. 목록이 `creatorId` 오름차순으로 나온다.
5. `requireAdmin`을 호출한다.
6. 테스트 3건이 통과한다.
