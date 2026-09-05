# Task 2.1 — 판매·취소 엔티티 명세

부모: [`task-2-sales-seed-data-spec.md`](./task-2-sales-seed-data-spec.md) · 의존 없음 · 10분

## 타입

```java
package com.liveclass.settlement.adapter.out.persistence;

@Entity
@Table(name = "sales", indexes = { /* 2.3 */ })
public class SaleEntity {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(name = "course_id", length = 64, nullable = false)
    private String courseId;

    @Column(nullable = false)
    private long amount;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    protected SaleEntity() { }                      // JPA 전용
    public SaleEntity(String id, String courseId, long amount, Instant paidAt) { ... }
    // getter만. setter 없음
}
```

`CancelEntity`도 같은 모양이다. 필드는 `id`, `saleId`(`sale_id`), `amount`, `cancelledAt`(`cancelled_at`).

## 계약

**`long`을 쓴다. `Long`이 아니다.** 원시 타입이면 `null`이 애초에 불가능하다. 래퍼로 두면 `NOT NULL`을 DB만 알고 코드는 모른다.

**시간은 `Instant`다.** Hibernate 6은 `Instant`를 `TIMESTAMP(6) WITH TIME ZONE`으로 매핑한다. `LocalDateTime`을 쓰면 오프셋이 사라져 `sale-5`(`2025-01-31T23:30+09:00`)가 어느 달인지 값만 보고 알 수 없게 된다. 2.7이 이것만 잡는 단언을 갖는다.

**컬럼 길이는 64자다.** 시드는 `sale-1`처럼 짧지만 Task 4가 만드는 판매는 UUID 36자다. 둘 다 담긴다.

**`@ManyToOne`을 쓰지 않는다.** `courseId`, `saleId`를 평범한 문자열 컬럼으로 둔다. 연관 매핑을 걸면 지연 로딩 프록시와 N+1이 따라오고, 어댑터가 어떤 쿼리를 던지는지 코드만 봐서는 알 수 없게 된다. 필요한 조인은 2.5가 JPQL로 명시한다.

**엔티티는 영속성 모델이지 도메인 모델이 아니다.** Task 4가 `domain/sales`에 `Sale` 애그리게이트를 따로 둔다. 두 엔티티는 그 애그리게이트의 저장 표현일 뿐이며 규칙을 갖지 않는다. `Sale`을 조립하는 것은 Task 4의 `SaleRepository` 어댑터이고, 이 두 엔티티에는 그 코드가 없다.

**setter를 만들지 않는다.** 두 엔티티 모두 등록 후 변경되지 않는다. 판매 금액이나 결제 시각이 사후에 바뀌면 이미 계산된 정산이 조용히 달라진다. 환불 상태도 저장하지 않는다 — Task 3 전제 10에 따라 취소 합계에서 매번 계산한다.

**`protected` 기본 생성자가 필요하다.** Hibernate가 프록시를 만들 때 쓴다. `public`으로 두면 애플리케이션 코드가 빈 엔티티를 만들 수 있다.

**FK 제약을 걸지 않는다.** `ddl-auto=create-drop`이 만드는 스키마에 `@ManyToOne`이 없으므로 FK도 없다. 없는 `courseId`로 판매를 등록하는 것은 Task 4가 `courseExists`로 막는다. DB 제약에 맡기면 `DataIntegrityViolationException`이 500으로 새어 나간다.

## 파일

`adapter/out/persistence/SaleEntity.java`, `CancelEntity.java`. `adapter/out/.gitkeep`을 지운다.

테스트는 없다. 검증은 2.5 어댑터 테스트와 2.7 시드 테스트가 한다.

## 완료 기준

1. 컴파일된다.
2. `long amount`, `Instant paidAt` / `cancelledAt`이다.
3. setter가 없고 기본 생성자가 `protected`다.
4. `@ManyToOne`, `@OneToMany`가 없다.
5. 환불 상태 컬럼이 없다.
