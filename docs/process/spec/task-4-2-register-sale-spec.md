# Task 4.2 — `Sale` 애그리게이트와 판매 등록 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 의존 4.1, Task 2·3 · 25분 · 테스트 6

## 왜 애그리게이트인가

이 프로젝트에서 애그리게이트가 필요한 지점은 정확히 하나다. **누적 취소 금액이 원결제 금액을 넘을 수 없다**는 불변식이다. 이건 판매 한 건과 그에 딸린 취소들이 **함께** 지켜야 하는 규칙이라 교과서적인 애그리게이트 경계다.

이 규칙을 application 서비스에 두면 "조회하고, 더하고, 비교하고, 저장한다"가 유스케이스마다 반복되고 누가 빠뜨려도 컴파일이 통과한다. 애그리게이트 안에 두면 `sale.cancel(...)`을 부르는 경로가 규칙을 우회할 방법이 없다.

정산 계산 쪽(Task 3)에는 애그리게이트를 두지 않았다. 거기는 불변식이 없고 전부 값에 대한 순수 함수다. **애그리게이트는 지킬 불변식이 있을 때만 값어치가 있다.**

## 읽기 하나, 쓰기 하나

조회 포트는 **하나다.** 읽기 모델만 용도별로 나뉜다.

| 방향 | 인터페이스 | 읽기 모델 | 위치 |
| --- | --- | --- | --- |
| 읽기 | `SalesQueryPort` | `SaleData` / `CancelData` (정산 계산)<br>`SaleRecord` (판매 목록) | `application/port/out` |
| 쓰기 | `SaleRepository` | `Sale` 애그리게이트 | **`domain/sales`** |

포트를 정산용과 판매용으로 나누지 않는다. 나누면 `findSales(from, to, creatorId)`와 `findSalesForListing(from, to, creatorId)`가 **인자가 같고 반환 모델만 다른 채로** 포트 둘, 어댑터 둘에 흩어진다. 같은 SQL을 감싸는 껍데기가 둘이 된다. 중복의 실체는 포트와 어댑터이지 모델이 아니다.

읽기 모델이 둘인 것은 필요가 실제로 다르기 때문이다. 정산 계산기는 `courseId`를 안 쓰고 판매 목록 응답은 그게 필요하다. 하나로 합치면 계산기 입력이 안 쓰는 필드를 들고 다닌다.

읽기가 애그리게이트를 쓰지 않는 이유는 N+1이다. 판매 목록 응답을 만들려고 애그리게이트를 N개 로딩하면 각각이 자기 취소를 딸고 온다. 목록은 판매 1회 + 취소 1회, 두 쿼리로 끝나야 한다. **두 방향의 필요가 달라 모델이 갈리는 것이지 유행이 아니다.**

## Lombok 경계

Task 2가 영속성 계층 5개 파일에서 Lombok을 쓴다 — `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@RequiredArgsConstructor`. Task 4는 **계층마다 다르게 간다.**

| 계층 | Lombok | 이유 |
| --- | --- | --- |
| `domain/sales` | **쓰지 않는다** | 애그리게이트는 무엇을 노출할지가 곧 설계다. `@Getter`는 전부 연다 |
| `adapter/out/persistence` | **쓴다** | Task 2와 같은 패키지다. 규약이 갈리면 읽는 사람이 멈춘다 |

도메인에서 `@Getter`를 쓰면 안 되는 구체적 이유가 있다. `cancels` 필드가 가변 `List`인데 `@Getter`는 그걸 **그대로** 내준다. 그러면 호출자가 리스트에 직접 `add`해서 불변식을 우회한다. 아래 `cancels()`는 불변 뷰를 돌려주도록 손으로 쓴다. 테스트 케이스 5가 정확히 그걸 잠근다.

접근자 4개(`id`·`courseId`·`amount`·`paidAt`)는 불변 값이라 `@Getter`로도 안전하지만, 한 클래스에서 일부만 애노테이션으로 만들면 규칙이 더 헷갈린다. 넷 다 손으로 쓴다.

## 애그리게이트

```java
package com.liveclass.settlement.domain.sales;

/** 판매 한 건과 그에 딸린 취소들. 누적 환불 ≤ 원결제를 스스로 지킨다. */
public class Sale {

    private final String id;
    private final String courseId;
    private final long amount;
    private final Instant paidAt;
    private final List<Cancel> cancels;      // 방어 복사

    /** 신규 등록. */
    public static Sale register(String id, String courseId, long amount, Instant paidAt);

    /** 저장소에서 복원. */
    public static Sale restore(String id, String courseId, long amount, Instant paidAt,
                               List<Cancel> cancels);

    /**
     * 취소를 추가한다. 누적 합계가 원결제 금액을 넘으면 거부한다.
     *
     * @throws RefundAmountExceededException 누적 합계 + amount > this.amount
     */
    public Cancel cancel(String cancelId, long amount, Instant cancelledAt);

    public long cancelledTotal();
    public RefundStatus refundStatus();      // Task 3의 enum을 그대로 쓴다
    public List<Cancel> cancels();           // 불변 뷰

    // 접근자. 유스케이스가 id()를, 어댑터가 나머지 셋을 쓴다
    public String  id();
    public String  courseId();
    public long    amount();
    public Instant paidAt();
}

public record Cancel(String id, long amount, Instant cancelledAt) { }
```

`cancel(...)` 본문:

```java
long already = cancelledTotal();
if (amount > this.amount - already) {          // 덧셈이 아니라 뺄셈이다
    throw new RefundAmountExceededException(id, this.amount, already, amount);
}
Cancel c = new Cancel(cancelId, amount, cancelledAt);
cancels.add(c);
return c;
```

**`already + amount`로 쓰면 안 된다.** `@Positive`는 `Long.MAX_VALUE`를 허용한다. 취소가 하나라도 있는 판매에 `MAX_VALUE`를 넣으면 합이 오버플로해 음수가 되고, `음수 > this.amount`가 거짓이라 **상한 검사를 그냥 통과한다.** 요청 두 번이면 닿는다.

`this.amount - already`는 두 항 모두 음수가 아니므로 넘치지 않는다. 애그리게이트를 둔 이유가 이 불변식을 지키기 위해서인데 산술로 우회되면 의미가 없다.

**`>`이지 `>=`가 아니다.** 합계가 원결제액과 정확히 같은 것은 전액 환불이며 허용해야 한다. `cancel-1`이 그 경우다 — `sale-3`의 80,000원 전액. `>=`로 쓰면 시드가 들어가지 않는다.

**단건 비교가 아니라 누적 비교다.** `amount > this.amount`만 보면 80,000원 판매에 30,000원과 60,000원을 차례로 넣을 때 둘 다 통과해 90,000원이 환불된다.

**ID를 애그리게이트가 만들지 않는다.** `UUID.randomUUID()`를 도메인에 넣으면 테스트가 결과를 단언할 수 없다. Task 3이 "현재 시각을 읽지 않는다"를 규칙으로 잡은 것과 같은 이유로 무작위도 밖에서 주입한다. 유스케이스가 생성해 넘긴다.

**`RefundStatus`는 Task 3 것을 그대로 쓴다.** `domain.settlement.RefundStatus`이며 `of(long saleAmount, long cancelledTotal)` 2인자 오버로드를 부른다. 같은 개념의 enum을 두 벌 만들지 않는다.

`RefundAmountExceededException`는 이 애그리게이트가 던지므로 `domain/sales`에 둔다. 4.1의 파일 목록에서 `domain/settlement`가 아니라 여기로 옮긴다.

## 리포지토리

```java
package com.liveclass.settlement.domain.sales;

/** 도메인 인터페이스다. 애그리게이트를 통째로 주고받는다. */
public interface SaleRepository {

    /** 취소까지 함께 적재한다. 없으면 빈 Optional. null을 반환하지 않는다. */
    Optional<Sale> findById(String saleId);

    /** 신규 판매와 새로 추가된 취소를 반영한다. */
    void save(Sale sale);
}
```

**`domain`에 두는 이유는 애그리게이트를 다루기 때문이다.** 반환 타입이 도메인 타입이고 시그니처에 영속성 어휘가 없다. 구현은 `adapter/out/persistence`에 있고 도메인은 그 존재를 모른다.

**`findById`가 취소를 함께 읽는다.** 애그리게이트는 불변식을 지키는 단위이므로 부분만 적재하면 `cancelledTotal()`이 거짓말을 한다. Task 2의 엔티티에 `@OneToMany`가 없으므로 어댑터가 판매 1회 + 취소 1회로 조회해 조립한다. 취소 등록은 단건 경로라 N+1이 아니다.

## 조회 포트 확장

`SalesQueryPort`는 Task 3.5가 이미 선언했다. 4.2는 **메서드 둘과 읽기 모델 하나를 그 인터페이스에 더한다.**

```java
package com.liveclass.settlement.application.port.out;

public interface SalesQueryPort {

    // --- Task 3.5 선언, Task 2.5 구현 ---
    List<SaleData>   findSales(Instant fromInclusive, Instant toExclusive, String creatorId);
    List<CancelData> findCancels(Instant fromInclusive, Instant toExclusive, String creatorId);
    List<CancelData> findCancelsBySaleIds(Collection<String> saleIds);
    List<String>     findAllCreatorIds();

    // --- 4.2가 추가 ---
    /** 판매 목록. 결과 없으면 빈 리스트. paidAt 오름차순. */
    List<SaleRecord> findSalesForListing(Instant fromInclusive, Instant toExclusive, String creatorId);

    boolean courseExists(String courseId);
}

public record SaleRecord(String saleId, String courseId, long amount, Instant paidAt) { }
```

**메서드를 Task 3이 미리 선언하지 않은 이유는 포트의 모양이 유스케이스에서 나오기 때문이다.** Task 3 시점에는 판매 목록 API도 강의 존재 검사도 없었다. 없는 유스케이스의 시그니처를 추측해 선언하면 틀린 모양이 굳는다.

**Task 2의 어댑터를 4.2가 확장한다.** `SalesQueryJpaAdapter`는 Task 2.5가 네 메서드로 만들고, 4.2가 두 메서드를 더한다. 인터페이스가 자라면 구현체도 자라야 하므로 Task 2의 산출물을 Task 4가 편집하는 유일한 지점이다. Task 2 완료 시점에는 인터페이스에 네 개뿐이라 컴파일이 통과한다.

**`SaleData`를 못 쓰는 이유.** 4.4의 응답 `SaleItem`에 `courseId`가 들어가는데 Task 3의 `SaleData`에는 그 필드가 없다. Task 3은 계산에 안 쓰는 필드를 의도적으로 뺐고 그 결정은 옳다. 판매 목록은 계산이 아니라 조회이므로 자기 읽기 모델을 갖는다. Task 3의 고정된 포트를 건드리지 않는다.

**`paidAt` 오름차순을 계약에 넣는다.** 정렬을 안 정하면 SQL이 돌려주는 순서에 응답이 좌우되어 같은 요청이 다른 순서로 나갈 수 있다.

**`courseExists`가 여기 있는 것은 타협이다.** 강의는 판매 애그리게이트 밖이지만 이 하나 때문에 포트를 더 만들지 않는다. 3시간 예산의 판단이며 README에 남긴다.

## 어댑터

### 이름 세 개를 구분한다

`Sale`이 붙은 리포지토리 계열이 셋이라 한 화면에 나오면 헷갈린다.

| 이름 | 무엇 | 소유 | 계층 |
| --- | --- | --- | --- |
| `SaleRepository` | 애그리게이트를 주고받는 **도메인 인터페이스** | Task 4 | `domain/sales` |
| `SaleRepositoryJpaAdapter` | 그 구현 | Task 4 | `adapter/out/persistence` |
| `SaleJpaRepository` | Spring Data 인터페이스 | Task 2 | `adapter/out/persistence` |

`SaleRepositoryJpaAdapter`가 `SaleJpaRepository`를 **쓴다.** 앞의 둘은 도메인 계약, 뒤의 하나는 JPA 도구다.

```java
package com.liveclass.settlement.adapter.out.persistence;

@Component
@RequiredArgsConstructor                          // Task 2 규약
public class SaleRepositoryJpaAdapter implements SaleRepository {

    private final SaleJpaRepository sales;
    private final CancelJpaRepository cancels;

    // findById: sales.findById(1회) + cancels.findBySaleId(1회) -> Sale.restore
    // save: 엔티티로 변환해 저장. 기존 취소는 ID로 걸러 새것만 insert
}
```

### `SalesQueryJpaAdapter`는 새로 만들지 않고 확장한다

Task 2.5가 이미 만들었다. 실제 구현은 이렇다.

```java
@Component
@RequiredArgsConstructor
public class SalesQueryJpaAdapter implements SalesQueryPort {
    private final SaleJpaRepository sales;
    private final CancelJpaRepository cancels;
    private final CreatorJpaRepository creators;   // 셋뿐
```

4.2는 **필드 하나와 메서드 둘을 더한다.**

| 무엇 | 왜 |
| --- | --- |
| `private final CourseJpaRepository courses;` | `courseExists`가 쓴다. **이걸 빼먹으면 빈 주입 실패로 컨텍스트가 안 뜬다** |
| `findSalesForListing` | 2.4의 `findByCreatorAndPeriod`를 `SaleRecord`로 매핑. `findSales`와 같은 쿼리, 매핑만 다르다 |
| `courseExists` | `courses.existsById` |

`@RequiredArgsConstructor`라 필드를 선언하면 생성자가 따라온다. 손으로 생성자를 고칠 필요는 없지만 **필드 선언을 빼면 조용히 안 된다.**

`findByCreatorAndPeriod`의 실제 JPQL에 `order by s.paidAt, s.id`가 이미 있다. `findSalesForListing`은 그 쿼리를 재사용하므로 정렬 계약을 따로 구현하지 않아도 된다.

Task 2.4가 만든 Spring Data 리포지토리 4종을 그대로 감싼다. 새 리포지토리를 만들지 않는다.

**ID는 서버가 UUID로 만든다.** 클라이언트가 정하면 `sale-1`을 보내 시드를 덮어쓸 수 있고, `sale-{n}` 시퀀스는 동시 요청에서 경합한다. Task 2의 컬럼 길이 64자가 UUID 36자를 담는다.

## 유스케이스

```java
package com.liveclass.settlement.application.sales;

@Service
public class RegisterSaleUseCase {

    @Transactional
    public String register(ActorContext actor, String courseId, long amount, Instant paidAt) {
        accessPolicy.requireAdmin(actor);
        if (!saleQueryPort.courseExists(courseId)) throw new CourseNotFoundException(courseId);

        Sale sale = Sale.register(UUID.randomUUID().toString(), courseId, amount, paidAt);
        saleRepository.save(sale);
        log.info(...);                                   // 4.7
        return sale.id();
    }
}
```

**트랜잭션 경계는 유스케이스에 둔다.** 등록·취소 유스케이스에 `@Transactional`, 조회 전용 유스케이스에 `@Transactional(readOnly = true)`를 붙인다.

애그리게이트 한 번의 변경이 한 트랜잭션이라는 것이 애그리게이트 경계의 정의이고, 그 경계를 아는 곳은 유스케이스다. 어댑터의 `save`에만 걸면 `findById`와 `save`가 다른 트랜잭션이 되어, 그 틈에 다른 요청이 취소를 넣으면 누적 검사가 낡은 데이터로 돌아간다. 어댑터가 판매·취소 두 리포지토리를 쓰므로 부분 저장도 가능하다.

**`courseExists` 검사가 필수다.** Task 2가 FK 제약을 걸지 않았으므로 없는 `courseId`로도 행이 그냥 들어간다. 그러면 그 판매는 어떤 크리에이터에도 속하지 않아 정산 조회에서 영원히 안 보이는 유령 데이터가 된다. FK를 걸었다면 `DataIntegrityViolationException`이 500으로 샜을 것이다. 어느 쪽이든 여기서 막아야 한다.

**등록을 ADMIN으로 제한하는 것은 판단이다.** 원본 과제에 명시가 없다. 크리에이터가 자기 강의의 판매를 임의로 등록할 수 있으면 정산을 스스로 부풀릴 수 있다. 등록은 결제 시스템이 하는 일이라고 보고 운영자로 좁힌다. README에 가정으로 남긴다.

**금액 부호는 여기서 보지 않는다.** 4.5의 Bean Validation이 `@Positive`로 막는다. 요청 형식의 문제이지 도메인 규칙이 아니다.

## 테스트 — `SaleTest` 6건

애그리게이트는 Spring 없이 단위 테스트로 잠근다. 불변식이 도메인에 있으므로 HTTP까지 안 가도 검증된다.

| # | 케이스 | 기대 |
| --- | --- | --- |
| 1 | 취소 없는 판매에 30,000 취소 | 통과. `cancelledTotal()` 30,000, 상태 `PARTIAL` |
| 2 | 80,000 판매에 30,000 후 60,000 | **`RefundAmountExceededException`.** 누적 판정이 없으면 통과해 버린다 |
| 3 | 80,000 판매에 80,000 전액 | 통과. 상태 `FULL`. `>=`로 잘못 쓰면 여기서 걸린다 |
| 4 | 80,000 판매에 30,000 + 50,000 | 통과. 합계가 정확히 원결제액이다 |
| 5 | `cancels()` 반환값 수정 시도 | `UnsupportedOperationException`. 외부에서 불변식을 우회할 수 없다 |
| 6 | 30,000 취소가 있는 80,000 판매에 `Long.MAX_VALUE` | **`RefundAmountExceededException`.** 덧셈으로 쓰면 오버플로로 통과한다 |

## 파일

`domain/sales/Sale.java`, `Cancel.java`, `SaleRepository.java`, `RefundAmountExceededException.java`
  (`RefundAmountExceededException`는 **여기에만** 만든다. 4.1은 만들지 않는다)
`application/port/out/SalesQueryPort.java`, `SaleRecord.java`
`adapter/out/persistence/SaleRepositoryJpaAdapter.java`, `SalesQueryJpaAdapter.java`
`application/sales/RegisterSaleUseCase.java`
`src/test/java/.../domain/sales/SaleTest.java`

## 완료 기준

1. 컴파일되고 빈이 등록된다. 조회 포트가 하나다.
2. `SaleTest` 6건이 Spring 컨텍스트 없이 통과한다.
2-b. 상한 검사가 뺄셈이다. 덧셈이면 케이스 6이 실패한다.
2-c. `id()`·`courseId()`·`amount()`·`paidAt()` 접근자가 있어 유스케이스와 어댑터가 컴파일된다.
3. `domain/sales`에 Spring 애노테이션, JPA 애노테이션, Lombok이 없다.
3-b. `adapter/out/persistence`의 새 클래스가 Task 2와 같이 `@RequiredArgsConstructor`를 쓴다.
3-c. `SalesQueryJpaAdapter`에 `CourseJpaRepository` 필드가 추가됐다.
4. 없는 `courseId`가 `CourseNotFoundException`를 던진다.
5. CREATOR가 호출하면 `ActorAccessDeniedException`가 난다.
6. 반환된 ID가 UUID 형식이고 시드 ID와 충돌하지 않는다.
7. `findById`가 취소까지 적재한다. 부분 적재하지 않는다.
7-b. 등록·취소 유스케이스에 `@Transactional`이 있다.
8. `SalesQueryPort.findSalesForListing`가 `courseId`를 담아 `paidAt` 오름차순으로 돌려준다.
