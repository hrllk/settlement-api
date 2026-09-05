# Task 2.3 — 인덱스 정의 명세

부모: [`task-2-sales-seed-data-spec.md`](./task-2-sales-seed-data-spec.md) · 의존 2.1, 2.2 · 5분

별도 파일이 없다. 2.1과 2.2가 만든 엔티티의 `@Table(indexes = ...)`에 넣는다.

## 정의

```java
@Table(name = "sales", indexes = {
    @Index(name = "idx_sales_course_paid", columnList = "course_id, paid_at")
})

@Table(name = "cancels", indexes = {
    @Index(name = "idx_cancels_sale_cancelled", columnList = "sale_id, cancelled_at")
})

@Table(name = "courses", indexes = {
    @Index(name = "idx_courses_creator", columnList = "creator_id")
})
```

`creators`에는 인덱스를 두지 않는다. PK 조회와 전체 스캔뿐이다.

## 컬럼 순서를 뒤집지 않는 이유

`(paid_at, course_id)`, `(cancelled_at, sale_id)`로 뒤집자는 제안이 검토 과정에서 나왔으나 철회했다. **두 조회 경로가 상반된 선행 컬럼을 원하기 때문이다.**

| 조회 | 필터 | 원하는 선행 컬럼 |
| --- | --- | --- |
| `findSales(from, to, creatorId)` | 기간 + 크리에이터 조인 | `paid_at` |
| 강의별 판매 | `course_id` | `course_id` |
| `findCancels(from, to, creatorId)` | 기간 + 조인 | `cancelled_at` |
| `findCancelsBySaleIds(saleIds)` | **`sale_id`만** | `sale_id` |

마지막 줄이 결정적이다. `findCancelsBySaleIds`에는 **시간 조건이 아예 없다.** `(cancelled_at, sale_id)`로 두면 선행 컬럼을 쓸 수 없어 풀스캔이 된다. 이 메서드는 Task 3이 환불 상태 산출을 위해 명시적으로 추가한 경로다 — `sale-5`는 1월 판매인데 취소가 2월이라 시간 창 조회로는 잡히지 않는다.

데이터가 7건이라 어느 순서든 실측 차이가 0이다. 인덱스를 늘리지 않고 현행을 유지하며, **두 경로가 다른 순서를 원한다는 관찰을 README에 남긴다.** 실제 트래픽에서는 인덱스를 하나 더 두거나 커버링 인덱스를 검토할 지점이라는 설명을 붙인다. 그 설명 자체가 설계 이해의 신호가 된다.

## 완료 기준

1. 세 인덱스가 엔티티 애노테이션에 있다.
2. 기동 로그의 DDL에 인덱스 생성문이 보인다.
3. 인덱스를 추가로 만들지 않았다.
