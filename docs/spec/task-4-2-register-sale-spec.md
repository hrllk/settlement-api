# Task 4.2 — 판매 등록 + `SalePort` 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 의존 4.1 · 15분

## 커맨드 포트

```java
package com.liveclass.settlement.application.port.out;

public interface SalePort {

    /** 저장하고 서버가 생성한 판매 ID를 돌려준다. */
    String saveSale(String courseId, long amount, Instant paidAt);

    /** 저장하고 서버가 생성한 취소 ID를 돌려준다. */
    String saveCancel(String saleId, long amount, Instant cancelledAt);

    /** 없으면 빈 Optional. null을 반환하지 않는다. */
    Optional<SaleRecord> findSaleById(String saleId);

    /** 판매 목록 조회용. 결과 없으면 빈 리스트. paidAt 오름차순. */
    List<SaleRecord> findSalesByCreator(Instant fromInclusive, Instant toExclusive, String creatorId);

    /** 그 판매에 연결된 모든 취소의 합계. 없으면 0. */
    long sumCancelledAmount(String saleId);

    boolean courseExists(String courseId);
}
```

**Task 2의 `SettlementQueryPort`는 조회 메서드 4개뿐이라 등록 경로를 덮지 못한다.** 커맨드 포트를 Task 4가 소유하는 이유는 포트의 모양이 유스케이스에서 나오기 때문이다. Task 2 시점에는 등록 유스케이스가 없어 시그니처를 추측해야 한다.

**커맨드 포트가 아니라 `SalePort`다.** 등록과 판매 목록 조회를 함께 갖는다. Task 2의 `SettlementQueryPort`는 정산 계산의 입력을 주는 포트이고, 이쪽은 판매 API의 읽기·쓰기 모델이다. 같은 테이블을 보지만 목적이 다르다.

**`saveCancel`이 취소 ID를 돌려준다.** 4.5의 `CancelResponse`가 `cancelId`를 담고 4.7이 로그에 남긴다. `void`로 두면 둘 다 채울 수 없다.

**`findSaleById`는 `SaleData`가 아니라 전용 타입을 돌려준다.**

```java
package com.liveclass.settlement.application.port.out;

public record SaleRecord(String saleId, String courseId, long amount, Instant paidAt) { }
```

Task 3의 `SaleData`는 `creatorId`를 필수 필드로 갖고 compact 생성자가 `requireNonNull`을 건다. 그런데 `findSaleById(saleId)`에는 크리에이터를 알 방법이 없다. 채우려면 `sales → courses` 조인을 해야 하는데, **정작 4.3이 쓰는 값은 `amount` 하나다.** 아무도 안 쓰는 필드를 채우려고 조인하는 것은 본말이 전도된다.

`SaleRecord`는 `sales` 테이블 한 번 조회로 채워진다. 애플리케이션 계층이 JPA 엔티티를 보지 않는 것은 동일하다.

**`findSalesByCreator`도 `SaleRecord`를 돌려주는 이유가 여기 있다.** 4.4의 응답 `SaleItem`에는 `courseId`가 들어가는데 Task 3의 `SaleData`에는 그 필드가 없다. Task 3은 계산에 안 쓰는 필드를 의도적으로 뺐고 그 결정은 옳다. 판매 목록은 계산이 아니라 조회이므로 자기 읽기 모델을 갖는다. Task 3의 고정된 포트를 건드리지 않는다.

**`paidAt` 오름차순을 계약에 넣는다.** 정렬을 안 정하면 SQL이 돌려주는 순서에 응답이 좌우되어 같은 요청이 다른 순서로 나갈 수 있다.

`sumCancelledAmount`와 `courseExists`는 4.3과 4.2의 판정에 각각 쓰인다. 유스케이스가 판정에 필요한 값만 받고 조회 방법은 모른다.

## 어댑터

```java
package com.liveclass.settlement.adapter.out.persistence;

@Component
public class SaleJpaAdapter implements SalePort {
    // SaleRepository, CancelRepository, CourseRepository (Task 2.4)를 감싼다
    // saveSale / saveCancel: UUID.randomUUID().toString()으로 ID 생성 후 반환
    // findSalesByCreator: Task 2.4의 findByCreatorAndPeriod를 SaleRecord로 매핑
}
```

**ID는 서버가 UUID로 만든다.** 클라이언트가 정하면 `sale-1`을 보내 시드를 덮어쓸 수 있고, `sale-{n}` 시퀀스는 동시 요청에서 경합한다. Task 2의 컬럼 길이 64자가 UUID 36자를 담는다.

## 유스케이스

```java
package com.liveclass.settlement.application.sale;

@Service
public class RegisterSaleUseCase {

    public String register(ActorContext actor, String courseId, long amount, Instant paidAt) {
        accessPolicy.requireAdmin(actor);
        if (!salePort.courseExists(courseId)) throw new CourseNotFound(courseId);
        String saleId = salePort.saveSale(courseId, amount, paidAt);
        log.info(...);                                   // 4.7
        return saleId;
    }
}
```

**`courseExists` 검사가 필수다.** Task 2가 FK 제약을 걸지 않았으므로 없는 `courseId`로도 행이 그냥 들어간다. 그러면 그 판매는 어떤 크리에이터에도 속하지 않아 정산 조회에서 영원히 안 보이는 유령 데이터가 된다. FK를 걸었다면 `DataIntegrityViolationException`이 500으로 샜을 것이다. 어느 쪽이든 여기서 막아야 한다.

**등록을 ADMIN으로 제한하는 것은 판단이다.** 원본 과제에 명시가 없다. 크리에이터가 자기 강의의 판매를 임의로 등록할 수 있으면 정산을 스스로 부풀릴 수 있다. 등록은 결제 시스템이 하는 일이라고 보고 운영자로 좁힌다. README에 가정으로 남긴다.

**금액 부호는 여기서 보지 않는다.** 4.5의 Bean Validation이 `@Positive`로 막는다. 요청 형식의 문제이지 도메인 규칙이 아니다.

## 파일

`application/port/out/SalePort.java`, `SaleRecord.java`
`adapter/out/persistence/SaleJpaAdapter.java`
`application/sale/RegisterSaleUseCase.java`

테스트는 없다. 4.8이 HTTP 레벨로 검증한다.

## 완료 기준

1. 컴파일되고 빈이 등록된다.
2. 없는 `courseId`가 `CourseNotFound`를 던진다.
3. CREATOR가 호출하면 `ActorAccessDenied`가 난다.
4. 반환된 ID가 UUID 형식이고 시드 ID와 충돌하지 않는다. `saveCancel`도 ID를 돌려준다.
4-b. `findSalesByCreator`가 `courseId`를 담은 `SaleRecord`를 `paidAt` 오름차순으로 돌려준다.
5. `findSaleById`가 `null`이 아니라 `Optional`을 돌려준다.
