# Task 4.2 — `Sale` 애그리게이트와 판매 등록 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 의존 4.1 · 25분

## 왜 애그리게이트인가

이 프로젝트에서 애그리게이트가 필요한 지점은 정확히 하나다. **누적 취소 금액이 원결제 금액을 넘을 수 없다**는 불변식이다. 이건 판매 한 건과 그에 딸린 취소들이 **함께** 지켜야 하는 규칙이라 교과서적인 애그리게이트 경계다.

이 규칙을 application 서비스에 두면 "조회하고, 더하고, 비교하고, 저장한다"가 유스케이스마다 반복되고 누가 빠뜨려도 컴파일이 통과한다. 애그리게이트 안에 두면 `sale.cancel(...)`을 부르는 경로가 규칙을 우회할 방법이 없다.

정산 계산 쪽(Task 3)에는 애그리게이트를 두지 않았다. 거기는 불변식이 없고 전부 값에 대한 순수 함수다. **애그리게이트는 지킬 불변식이 있을 때만 값어치가 있다.**

## 세 갈래 모델

| 방향 | 타입 | 위치 | 소유 |
| --- | --- | --- | --- |
| 정산 계산 입력 | `SettlementQueryPort` → `SaleData` / `CancelData` | `application/port/out` | Task 3 선언, Task 2 구현 |
| 판매 목록 조회 | `SaleQueryPort` → `SaleRecord` | `application/port/out` | **4.2** |
| 판매 등록·취소 | `SaleRepository` → `Sale` | **`domain/sales`** | **4.2** |

읽기가 애그리게이트를 쓰지 않는 이유는 N+1이다. 판매 목록 응답을 만들려고 애그리게이트를 N개 로딩하면 각각이 자기 취소를 딸고 온다. 목록은 판매 1회 + 취소 1회, 두 쿼리로 끝나야 한다. **두 방향의 필요가 달라 모델이 갈리는 것이지 유행이 아니다.**

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
     * @throws RefundAmountExceeded 누적 합계 + amount > this.amount
     */
    public Cancel cancel(String cancelId, long amount, Instant cancelledAt);

    public long cancelledTotal();
    public RefundStatus refundStatus();      // Task 3의 enum을 그대로 쓴다
    public List<Cancel> cancels();           // 불변 뷰
}

public record Cancel(String id, String amount, Instant cancelledAt) { }   // amount는 long
```

`cancel(...)` 본문:

```java
long already = cancelledTotal();
if (already + amount > this.amount) {
    throw new RefundAmountExceeded(id, this.amount, already, amount);
}
Cancel c = new Cancel(cancelId, amount, cancelledAt);
cancels.add(c);
return c;
```

**`>`이지 `>=`가 아니다.** 합계가 원결제액과 정확히 같은 것은 전액 환불이며 허용해야 한다. `cancel-1`이 그 경우다 — `sale-3`의 80,000원 전액. `>=`로 쓰면 시드가 들어가지 않는다.

**단건 비교가 아니라 누적 비교다.** `amount > this.amount`만 보면 80,000원 판매에 30,000원과 60,000원을 차례로 넣을 때 둘 다 통과해 90,000원이 환불된다.

**ID를 애그리게이트가 만들지 않는다.** `UUID.randomUUID()`를 도메인에 넣으면 테스트가 결과를 단언할 수 없다. Task 3이 "현재 시각을 읽지 않는다"를 규칙으로 잡은 것과 같은 이유로 무작위도 밖에서 주입한다. 유스케이스가 생성해 넘긴다.

**`RefundStatus`는 Task 3 것을 그대로 쓴다.** `domain.settlement.RefundStatus`이며 `of(long saleAmount, long cancelledTotal)` 2인자 오버로드를 부른다. 같은 개념의 enum을 두 벌 만들지 않는다.

`RefundAmountExceeded`는 이 애그리게이트가 던지므로 `domain/sales`에 둔다. 4.1의 파일 목록에서 `domain/settlement`가 아니라 여기로 옮긴다.

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

## 조회 포트

```java
package com.liveclass.settlement.application.port.out;

public interface SaleQueryPort {

    /** 판매 목록. 결과 없으면 빈 리스트. paidAt 오름차순. */
    List<SaleRecord> findSalesByCreator(Instant fromInclusive, Instant toExclusive, String creatorId);

    boolean courseExists(String courseId);
}

public record SaleRecord(String saleId, String courseId, long amount, Instant paidAt) { }
```

**`SaleData`를 못 쓰는 이유.** 4.4의 응답 `SaleItem`에 `courseId`가 들어가는데 Task 3의 `SaleData`에는 그 필드가 없다. Task 3은 계산에 안 쓰는 필드를 의도적으로 뺐고 그 결정은 옳다. 판매 목록은 계산이 아니라 조회이므로 자기 읽기 모델을 갖는다. Task 3의 고정된 포트를 건드리지 않는다.

**`paidAt` 오름차순을 계약에 넣는다.** 정렬을 안 정하면 SQL이 돌려주는 순서에 응답이 좌우되어 같은 요청이 다른 순서로 나갈 수 있다.

**`courseExists`가 여기 있는 것은 타협이다.** 강의는 판매 애그리게이트 밖이지만 이 하나 때문에 포트를 더 만들지 않는다. 3시간 예산의 판단이며 README에 남긴다.

## 어댑터

```java
package com.liveclass.settlement.adapter.out.persistence;

@Component
public class SaleRepositoryJpaAdapter implements SaleRepository {
    // findById: SaleRepository(2.4) 1회 + CancelRepository.findBySaleId(2.4) 1회 -> Sale.restore
    // save: 엔티티로 변환해 저장. 기존 취소는 ID로 걸러 새것만 insert
}

@Component
public class SaleQueryJpaAdapter implements SaleQueryPort {
    // findSalesByCreator: 2.4의 findByCreatorAndPeriod를 SaleRecord로 매핑
    // courseExists: CourseRepository.existsById
}
```

Task 2.4가 만든 Spring Data 리포지토리 4종을 그대로 감싼다. 새 리포지토리를 만들지 않는다.

**ID는 서버가 UUID로 만든다.** 클라이언트가 정하면 `sale-1`을 보내 시드를 덮어쓸 수 있고, `sale-{n}` 시퀀스는 동시 요청에서 경합한다. Task 2의 컬럼 길이 64자가 UUID 36자를 담는다.

## 유스케이스

```java
package com.liveclass.settlement.application.sale;

@Service
public class RegisterSaleUseCase {

    public String register(ActorContext actor, String courseId, long amount, Instant paidAt) {
        accessPolicy.requireAdmin(actor);
        if (!saleQueryPort.courseExists(courseId)) throw new CourseNotFound(courseId);

        Sale sale = Sale.register(UUID.randomUUID().toString(), courseId, amount, paidAt);
        saleRepository.save(sale);
        log.info(...);                                   // 4.7
        return sale.id();
    }
}
```

**`courseExists` 검사가 필수다.** Task 2가 FK 제약을 걸지 않았으므로 없는 `courseId`로도 행이 그냥 들어간다. 그러면 그 판매는 어떤 크리에이터에도 속하지 않아 정산 조회에서 영원히 안 보이는 유령 데이터가 된다. FK를 걸었다면 `DataIntegrityViolationException`이 500으로 샜을 것이다. 어느 쪽이든 여기서 막아야 한다.

**등록을 ADMIN으로 제한하는 것은 판단이다.** 원본 과제에 명시가 없다. 크리에이터가 자기 강의의 판매를 임의로 등록할 수 있으면 정산을 스스로 부풀릴 수 있다. 등록은 결제 시스템이 하는 일이라고 보고 운영자로 좁힌다. README에 가정으로 남긴다.

**금액 부호는 여기서 보지 않는다.** 4.5의 Bean Validation이 `@Positive`로 막는다. 요청 형식의 문제이지 도메인 규칙이 아니다.

## 테스트 — `SaleTest` 5건

애그리게이트는 Spring 없이 단위 테스트로 잠근다. 불변식이 도메인에 있으므로 HTTP까지 안 가도 검증된다.

| # | 케이스 | 기대 |
| --- | --- | --- |
| 1 | 취소 없는 판매에 30,000 취소 | 통과. `cancelledTotal()` 30,000, 상태 `PARTIAL` |
| 2 | 80,000 판매에 30,000 후 60,000 | **`RefundAmountExceeded`.** 누적 판정이 없으면 통과해 버린다 |
| 3 | 80,000 판매에 80,000 전액 | 통과. 상태 `FULL`. `>=`로 잘못 쓰면 여기서 걸린다 |
| 4 | 80,000 판매에 30,000 + 50,000 | 통과. 합계가 정확히 원결제액이다 |
| 5 | `cancels()` 반환값 수정 시도 | `UnsupportedOperationException`. 외부에서 불변식을 우회할 수 없다 |

## 파일

`domain/sales/Sale.java`, `Cancel.java`, `SaleRepository.java`, `RefundAmountExceeded.java`
`application/port/out/SaleQueryPort.java`, `SaleRecord.java`
`adapter/out/persistence/SaleRepositoryJpaAdapter.java`, `SaleQueryJpaAdapter.java`
`application/sale/RegisterSaleUseCase.java`
`src/test/java/.../domain/sales/SaleTest.java`

## 완료 기준

1. 컴파일되고 빈이 등록된다.
2. `SaleTest` 5건이 Spring 컨텍스트 없이 통과한다.
3. `domain/sales`에 Spring 애노테이션과 JPA 애노테이션이 없다.
4. 없는 `courseId`가 `CourseNotFound`를 던진다.
5. CREATOR가 호출하면 `ActorAccessDenied`가 난다.
6. 반환된 ID가 UUID 형식이고 시드 ID와 충돌하지 않는다.
7. `findById`가 취소까지 적재한다. 부분 적재하지 않는다.
8. `SaleQueryPort.findSalesByCreator`가 `courseId`를 담아 `paidAt` 오름차순으로 돌려준다.
