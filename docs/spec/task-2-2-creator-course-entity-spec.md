# Task 2.2 — 크리에이터·강의 엔티티 명세

부모: [`task-2-sales-seed-data-spec.md`](./task-2-sales-seed-data-spec.md) · 의존 없음 · 5분

## 타입

```java
@Entity @Table(name = "creators")
public class CreatorEntity {
    @Id @Column(length = 64, nullable = false) private String id;
    @Column(nullable = false) private String name;
    protected CreatorEntity() { }
    public CreatorEntity(String id, String name) { ... }
}

@Entity @Table(name = "courses", indexes = { /* 2.3 */ })
public class CourseEntity {
    @Id @Column(length = 64, nullable = false) private String id;
    @Column(name = "creator_id", length = 64, nullable = false) private String creatorId;
    @Column(nullable = false) private String title;
    protected CourseEntity() { }
    public CourseEntity(String id, String creatorId, String title) { ... }
}
```

규칙은 2.1과 같다. setter 없음, `protected` 기본 생성자, `@ManyToOne` 없음. Lombok은 `@Getter`와 `@NoArgsConstructor(access = PROTECTED)`만 쓴다.

## 왜 이 두 엔티티가 필요한가

과제의 정산 계산만 보면 판매와 취소로 충분해 보인다. 두 가지 때문에 필요하다.

**`creators`는 creator-3 때문이다.** creator-3은 2025-03에 판매도 취소도 없다. 운영자 기간 집계는 실적 0인 크리에이터도 목록에 0원으로 넣어야 한다. 판매·취소 자료만으로는 그 존재를 알 방법이 없다. Task 3의 `findAllCreatorIds()`가 이 테이블을 읽는다.

**`courses`는 판매를 크리에이터에 연결하는 유일한 경로다.** 판매는 `course_id`만 갖는다. `findSales(from, to, creatorId)`는 `sales → courses`를 조인해 `creator_id`로 좁힌다. `findCancels`는 `cancels → sales → courses`로 한 단계 더 간다.

Task 4의 `CourseNotFound` 판정도 이 테이블을 읽는다. 없는 강의로 판매를 등록하는 요청을 404로 거부하려면 존재 여부를 조회할 수 있어야 한다.

## 판매에 `creator_id`를 비정규화하지 않는 이유

조인 두 단계가 사라지고 쿼리가 단순해진다. 대신 강의 소유자가 바뀌면 판매 행 전부를 같이 고쳐야 하고, 안 고치면 과거 정산이 조용히 틀어진다. 데이터가 7건이라 조인 비용이 0이므로 정규화를 택한다. 이 판단을 README에 한 줄 남긴다.

## 파일

`adapter/out/persistence/CreatorEntity.java`, `CourseEntity.java`.

테스트는 없다. 2.7이 검증한다.

## 완료 기준

1. 컴파일된다.
2. `CourseEntity.creatorId`가 있어 판매→크리에이터 경로가 성립한다.
3. setter가 없고 기본 생성자가 `protected`다.
