# Task 2.4 — Spring Data 리포지토리 4종 명세

부모: [`task-2-sales-seed-data-spec.md`](./task-2-sales-seed-data-spec.md) · 의존 2.1, 2.2 · 10분

## 타입

```java
public interface SaleRepository extends JpaRepository<SaleEntity, String> {

    @Query("""
        select s from SaleEntity s
        where s.courseId in (select c.id from CourseEntity c where c.creatorId = :creatorId)
          and s.paidAt >= :fromInclusive and s.paidAt < :toExclusive
        order by s.paidAt, s.id
        """)
    List<SaleEntity> findByCreatorAndPeriod(@Param("creatorId") String creatorId,
                                            @Param("fromInclusive") Instant fromInclusive,
                                            @Param("toExclusive") Instant toExclusive);
}

public interface CancelRepository extends JpaRepository<CancelEntity, String> {

    @Query("""
        select x from CancelEntity x
        where x.saleId in (
              select s.id from SaleEntity s
              where s.courseId in (select c.id from CourseEntity c where c.creatorId = :creatorId))
          and x.cancelledAt >= :fromInclusive and x.cancelledAt < :toExclusive
        order by x.cancelledAt, x.id
        """)
    List<CancelEntity> findByCreatorAndPeriod(...);

    List<CancelEntity> findBySaleIdIn(Collection<String> saleIds);

    List<CancelEntity> findBySaleId(String saleId);   // Task 4의 누적 합계 판정용
}

public interface CreatorRepository extends JpaRepository<CreatorEntity, String> { }

public interface CourseRepository extends JpaRepository<CourseEntity, String> { }
```

## 계약

**반열린 구간을 쿼리에 직접 쓴다.** `>= :fromInclusive and < :toExclusive`다. `between`을 쓰면 양끝이 닫혀 종료 경계가 포함된다. Task 3이 반열린 구간을 핵심 설계 판단으로 잡았고 경계 테스트 3방향으로 잠갔는데, 여기서 `between`을 쓰면 그 판단이 어댑터에서 무너진다.

**크리에이터 필터를 서브쿼리로 표현한다.** 조인 대신 `in (select ...)`을 쓰는 이유는 엔티티에 `@ManyToOne`이 없어 JPQL 조인 경로가 없기 때문이다. 명시적 서브쿼리가 어떤 테이블을 거치는지 그대로 보여준다.

**취소는 원본 판매가 조회 창 밖일 수 있다.** `cancel-3`이 그 경우다 — 2월 취소인데 원본 `sale-5`는 1월 판매다. 그래서 `findByCreatorAndPeriod`가 `cancelledAt`으로 기간을 좁히고 크리에이터는 판매를 거쳐 찾는다. 두 조건의 기준 컬럼이 다르다.

**`findBySaleIdIn`에는 기간 조건이 없다.** 환불 상태는 기간 필터를 적용하지 않기 때문이다. 여기에 시간 조건을 넣으면 1월 판매 목록에서 `sale-5`가 `FULL`이 아니라 `NONE`으로 나온다.

**목록을 돌려주는 쿼리는 정렬을 고정한다.** `findByCreatorAndPeriod`는 `order by s.paidAt, s.id`, 취소 쪽은 `order by x.cancelledAt, x.id`를 붙인다. SQL은 정렬을 안 주면 순서를 보장하지 않는다. 정산 **금액**은 순서와 무관하지만 판매 **목록 응답**은 순서가 곧 결과라, 같은 요청이 실행마다 다른 순서로 나갈 수 있다. `id` 보조 정렬은 같은 시각의 두 행을 가르기 위한 것이다.

**`findBySaleId`는 Task 4 전용이다.** 누적 초과 환불 판정에서 한 판매의 기존 취소 합계를 구한다. Task 2는 선언만 하고 쓰지 않는다.

**`CreatorRepository`와 `CourseRepository`는 비어 있다.** `findAll`과 `existsById`가 `JpaRepository`에서 온다. 전자는 `findAllCreatorIds()`가, 후자는 Task 4의 `CourseNotFound` 판정이 쓴다. **강의 리포지토리가 없으면 Task 4가 없는 강의를 걸러낼 수 없어 FK 부재와 맞물려 판매가 그냥 등록된다.**

## 파일

`adapter/out/persistence/` 아래 인터페이스 4개.

테스트는 없다. 2.5 어댑터 테스트가 이 쿼리들을 통과시킨다.

## 완료 기준

1. 컴파일되고 컨텍스트 기동 시 쿼리 파생·검증이 통과한다.
2. 기간 조건이 `>=` / `<`다. `between`이 없다.
3. `findBySaleIdIn`에 시간 조건이 없다.
3-b. 목록 반환 쿼리에 `order by`가 있다.
4. 리포지토리가 4종이다.
