# Task 3.5 — `SettlementDataPort` 명세

부모: [`task-3-settlement-domain-spec.md`](./task-3-settlement-domain-spec.md) · 의존 3.2 · 5분

선언만 한다. 구현체는 Task 2가 만든다.

**이 포트는 조회 전용이다.** 저장과 ID 조회는 Task 4가 `SaleCommandPort`와 그 JPA 어댑터를 따로 만들어 소유한다. 읽기와 쓰기를 한 인터페이스에 섞지 않는다.

## 타입

```java
package com.liveclass.settlement.application.port.out;

public interface SettlementDataPort {

    List<SaleData>   findSales  (Instant fromInclusive, Instant toExclusive, String creatorId);
    List<CancelData> findCancels(Instant fromInclusive, Instant toExclusive, String creatorId);
    List<CancelData> findCancelsBySaleIds(Collection<String> saleIds);
    List<String>     findAllCreatorIds();
}
```

## 계약

**`creatorId`는 항상 필수다.** 전체를 한 번에 긁는 경로는 두지 않는다. 운영자 기간 집계는 `findAllCreatorIds()`로 목록을 받아 크리에이터마다 계산기를 한 번씩 돌린다. 3명이라 N+1이 문제되지 않고 조회 경로가 하나뿐이라 계약이 단순해진다.

**`findAllCreatorIds()`는 creator-3 때문에 필요하다.** 3월에 판매도 취소도 없는 크리에이터를 운영자 목록에 0원으로 넣으려면 판매·취소 자료만으로는 존재를 알 수 없다.

**`findCancelsBySaleIds()`는 환불 상태 전용이다.** 환불 상태는 기간 필터를 적용하지 않으므로 `cancelledAt` 창 조회로는 산출할 수 없다. sale-5는 1월 판매인데 취소가 2월 3일이라 `[1/1, 2/1)` 창에 안 잡힌다. 이게 없으면 1월 판매 목록에서 sale-5가 `FULL`이 아니라 `NONE`으로 나온다. Task 4의 판매 목록 조회가 쓴다.

**어떤 메서드도 `null`을 반환하지 않는다.** 결과가 없으면 빈 리스트다. 계산기는 `null` 방어를 하지 않으며 계약 위반은 구현체의 결함이다. 이 규칙을 Javadoc에 적는다.

시간 인자는 `Instant`다. `SettlementPeriod`가 KST를 이미 `Instant` 구간으로 바꿔 주므로 포트와 어댑터는 시간대를 모른다. 호출자는 `period.fromInclusive()`와 `period.toExclusive()`를 그대로 넘긴다.

포트가 `domain.settlement`의 값 타입을 참조하므로 `application` → `domain` 의존이 생긴다. 헥사고날에서 정상 방향이다. 반대는 만들지 않는다.

## Task 2에 넘기는 구현 요구

- 엔티티 시간 컬럼을 `Instant`로 매핑한다.
- `findCancels`는 취소의 원본 판매가 조회 창 밖일 수 있다. cancel-3이 그 경우다(2월 취소, 1월 판매). 크리에이터로 좁히려면 취소에서 판매로 조인해야 한다.
- `findCancelsBySaleIds`에 빈 컬렉션이 오면 빈 리스트를 반환한다. `IN ()`를 생성해 SQL 오류를 내지 않는다.
- 인덱스는 `tasks.json` 현행 정의를 유지한다. 크리에이터별 조회는 `course_id` 선행이, 운영자 기간 조회는 `paid_at` 선행이 유리해 두 경로가 상반된 순서를 원한다. 7건에서 실측 차이가 0이므로 하나만 두고 관찰만 README에 남긴다.

## 파일

`application/port/out/SettlementDataPort.java`. `application/.gitkeep`을 지운다. 테스트는 없다 — 인터페이스 선언이다. Task 2의 구현체와 Task 4·5 호출부에서 검증한다.

## 완료 기준

1. 컴파일된다. 메서드 4개가 전부 있다.
2. `null` 미반환 계약이 Javadoc에 있다.
3. Spring 애노테이션이 없다.
4. `domain`이 `application`을 참조하지 않는다.
