# Task 2.5 — `SettlementDataPort` JPA 어댑터 명세

부모: [`task-2-sales-seed-data-spec.md`](./task-2-sales-seed-data-spec.md) · 의존 2.4, Task 3.5 · 15분 · 테스트 5

**Task 3의 `SettlementDataPort`가 컴파일된 뒤에만 착수한다.**

## 타입

```java
package com.liveclass.settlement.adapter.out.persistence;

@Component
public class SettlementDataJpaAdapter implements SettlementDataPort {

    private final SaleRepository sales;
    private final CancelRepository cancels;
    private final CreatorRepository creators;

    @Override
    public List<SaleData> findSales(Instant fromInclusive, Instant toExclusive, String creatorId) {
        return sales.findByCreatorAndPeriod(creatorId, fromInclusive, toExclusive)
                    .stream().map(SettlementDataJpaAdapter::toSaleData).toList();
    }

    @Override
    public List<CancelData> findCancels(Instant fromInclusive, Instant toExclusive, String creatorId) { ... }

    @Override
    public List<CancelData> findCancelsBySaleIds(Collection<String> saleIds) {
        if (saleIds == null || saleIds.isEmpty()) {
            return List.of();                      // 쿼리를 던지지 않는다
        }
        return cancels.findBySaleIdIn(saleIds).stream().map(...).toList();
    }

    @Override
    public List<String> findAllCreatorIds() {
        return creators.findAll(Sort.by("id")).stream()   // 정렬 고정
                       .map(CreatorEntity::getId).toList();
    }

    private static SaleData toSaleData(SaleEntity e) { ... }   // creatorId는 강의를 거쳐 채운다
}
```

## 계약

**빈 컬렉션 방어가 필수다.** `findCancelsBySaleIds(List.of())`를 그대로 JPQL로 내리면 `in ()`이 된다. Hibernate 버전과 dialect에 따라 조용히 도는 것도 있고 문법 오류를 내는 것도 있어, 동작이 환경에 따라 갈린다. **판매 0건인 기간을 조회하면 실제로 이 경로를 밟는다.** creator-3의 2025-03이 그 경우다. 어댑터에서 명시적으로 막는다.

**어떤 메서드도 `null`을 반환하지 않는다.** 결과가 없으면 빈 리스트다. Task 3 계산기는 `null` 방어를 하지 않으며 이 계약 위반은 구현체의 결함이다.

**`SaleData.creatorId`를 채우는 방법을 정해야 한다.** `SaleEntity`에는 `courseId`만 있다. 세 가지가 가능하다.

| 방법 | 판단 |
| --- | --- |
| 판매마다 강의를 조회 | N+1. 7건이라 실측은 무해하지만 코드가 그렇게 읽힌다 |
| **호출 인자의 `creatorId`를 그대로 넣는다** | **채택.** `findSales`는 이미 그 크리에이터로 좁혀 조회했으므로 결과의 모든 판매가 그 크리에이터의 것이다 |
| 강의를 한 번에 로드해 맵으로 | 불필요한 복잡도 |

두 번째를 택한다. 조회 계약이 "항상 크리에이터로 좁힌다"이므로 인자가 곧 정답이다. 이 추론이 성립하는 이유를 코드 주석에 남긴다 — 나중에 전체 조회 경로가 생기면 깨지는 가정이다.

**이 어댑터는 정산 계산의 입력만 담당한다.** 판매 등록과 판매 목록 조회는 Task 4의 `SalePort`와 `SaleJpaAdapter`가 따로 맡는다. 같은 테이블을 보지만 목적이 달라 읽기 모델이 다르다.

**`CancelData`에는 `creatorId`가 없다.** Task 3이 의도적으로 뺐다. 취소를 항상 크리에이터로 좁혀 조회하므로 값 자체가 귀속 정보를 들 필요가 없다.

**`@Component`를 붙인다.** 어댑터는 `adapter.out`에 있으므로 Spring 애노테이션이 허용된다. Spring 금지는 `domain`과 `application.port.out`에만 적용된다.

## 테스트

`SettlementDataJpaAdapterTest` — `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import(SettlementDataJpaAdapter.class)`.

| # | 케이스 | 기대 |
| --- | --- | --- |
| 1 | `findSales` creator-1, 2025-03 KST 구간 | 4건, 합 260,000 |
| 2 | `findCancels` creator-2, 2025-02 KST 구간 | `cancel-3` 1건. 원본 판매가 1월인데도 잡힌다 |
| 3 | `findCancelsBySaleIds(["sale-5"])` | `cancel-3` 1건. 기간 조건이 없다 |
| 4 | `findCancelsBySaleIds([])` | 빈 리스트. 예외 없음 |
| 5 | `findAllCreatorIds()` | 3건. 실적 없는 크리에이터 포함. `creator-1, 2, 3` 순서 |

`Instant`는 `OffsetDateTime.parse("2025-03-01T00:00:00+09:00").toInstant()`로 만든다. UTC로 손 변환하지 않는다.

## 파일

`adapter/out/persistence/SettlementDataJpaAdapter.java`.

## 완료 기준

1. 포트 메서드 4개를 전부 구현한다.
2. 빈 컬렉션 입력이 쿼리 없이 빈 리스트를 돌려준다.
3. 어떤 경로도 `null`을 반환하지 않는다.
3-b. `findAllCreatorIds()`가 `creatorId` 오름차순이다.
4. 테스트 5건이 통과한다.
5. `domain`이 이 클래스를 참조하지 않는다.
