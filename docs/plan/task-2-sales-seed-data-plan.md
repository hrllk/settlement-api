# Task 2 — 판매 데이터와 시나리오 초기 데이터: 검토 계획

## 목표

`SalesQueryPort`의 JPA 구현체와, Task 3 기준표를 그대로 재현하는 초기 데이터를 만든다. 계산 규칙과 API는 범위 밖이다.

## 선행 조건

Task 1과 Task 3 **양쪽**에 의존한다. `tasks.json`의 의존성을 `[1]`에서 `[1,3]`으로 고쳤다.

- Task 1: 빌드 스크립트, `application.yml`, 루트 패키지
- Task 3: `SalesQueryPort` 인터페이스, `SaleData` / `CancelData` 값 타입

**2.5(포트 어댑터)는 Task 3의 포트 시그니처가 컴파일되기 전에는 착수할 수 없다.** 2.1~2.4, 2.6은 먼저 할 수 있다.

## 포함 범위

- `CreatorEntity`, `CourseEntity`, `SaleEntity`, `CancelEntity`
- Spring Data 리포지토리 4종과 `SalesQueryPort` JPA 어댑터 (조회 전용)
- `data.sql` 초기 데이터
- 시드 재현성 테스트

## 제외 범위

- 정산 계산 규칙 — Task 3
- 컨트롤러, DTO, 전역 예외 처리기 — Task 4, Task 5
- 초과 환불 거부 — 등록 시점 규칙이므로 Task 4
- 커맨드 포트 `SaleCommandPort`와 그 어댑터 — Task 4. 포트의 모양이 유스케이스에서 나오므로 사용처가 선언과 구현을 함께 갖는다. Task 2는 그 어댑터가 쓸 **리포지토리**까지만 준비한다
- 정산 스냅샷 엔티티, 수수료율 이력

## 확정된 전제

1. **식별자는 `String`이다.** Task 3의 값 타입이 `String`이므로 변환 계층을 두지 않는다. 시드의 `sale-1` 형태는 사람이 읽기 좋은 고정값일 뿐 **형식 제약이 아니다.** Task 4의 `POST /api/sales`가 만드는 판매는 서버가 생성한 UUID를 쓴다.
2. **금액은 원 단위 `long`, `NOT NULL`.**
3. **시간 컬럼은 `Instant`로 매핑한다.** Task 3 전제 2에 맞춘다. `LocalDateTime`을 쓰지 않는다.
4. **판매는 크리에이터를 직접 갖지 않고 강의를 통해 안다.** `SaleEntity.courseId` → `CourseEntity.creatorId`. 크리에이터로 좁히는 조회는 조인이다. `creator_id`를 판매에 비정규화하면 조인이 사라지지만, 강의 소유자가 바뀌면 두 곳을 고쳐야 한다. 7건 규모에서 조인 비용이 0이므로 정규화를 택한다.
5. **인덱스는 현행 정의를 유지한다.** 판매 `(course_id, paid_at)`, 취소 `(sale_id, cancelled_at)`.
6. **엔티티는 도메인으로 새지 않는다.** 어댑터가 엔티티를 `SaleData` / `CancelData`로 변환해서 넘긴다.
7. **포트 구현체는 어떤 경우에도 `null`을 반환하지 않는다.** 결과가 없으면 빈 리스트다.

   **입력이 빈 컬렉션일 때도 마찬가지다.** `findCancelsBySaleIds(emptyList())`는 쿼리를 던지지 않고 즉시 빈 리스트를 돌려준다. 판매 0건인 기간을 조회하면 이 경로를 밟는데, 그대로 JPQL로 내리면 `IN ()`이 되어 Hibernate 버전과 dialect에 따라 동작이 갈린다. 조용히 도는 버전도 있고 문법 오류가 나는 버전도 있다. 어댑터에서 명시적으로 막는다.
8. **연관은 `@ManyToOne`이 아니라 식별자 컬럼으로 둔다.** 지연 로딩 프록시와 N+1이 끼어들 여지를 없애고, 어댑터가 명시적으로 조회한다.

### 인덱스 순서에 대한 판단

`(paid_at, course_id)`, `(cancelled_at, sale_id)`로 뒤집자는 제안이 있었으나 채택하지 않는다. **두 조회 경로가 상반된 선행 컬럼을 원하기 때문이다.**

| 조회 | 원하는 선행 컬럼 |
|---|---|
| `findSales(from, to, creatorId)` | `paid_at` |
| 강의별 판매 조회 | `course_id` |
| `findCancels(from, to, creatorId)` | `cancelled_at` |
| `findCancelsBySaleIds(saleIds)` | `sale_id` |

특히 `findCancelsBySaleIds`는 **시간 조건이 아예 없다.** `(cancelled_at, sale_id)`로 두면 선행 컬럼을 쓸 수 없어 풀스캔이 된다. 이 메서드는 Task 3이 환불 상태 산출을 위해 명시적으로 추가한 경로다.

데이터가 7건이라 어느 순서든 실측 차이가 0이다. 인덱스를 늘리지 않고 현행을 유지하며, **두 경로가 다른 순서를 원한다는 관찰을 README에 남긴다.** 실제 트래픽에서는 인덱스를 하나 더 두거나 커버링 인덱스를 검토할 지점이라는 설명을 붙인다.

## 엔티티 설계

```text
creators   id(PK,String)  name
courses    id(PK,String)  creator_id(FK)  title
sales      id(PK,String)  course_id(FK)  amount(long)  paid_at(Instant)
           INDEX (course_id, paid_at)
cancels    id(PK,String)  sale_id(FK)  amount(long)  cancelled_at(Instant)
           INDEX (sale_id, cancelled_at)
```

`sales`에 환불 상태 컬럼을 두지 않는다. Task 3 전제 10에 따라 취소 합계에서 매번 계산한다.

## 초기 데이터

Task 3 기준표를 그대로 옮긴다. `application.yml`의 `defer-datasource-initialization=true` 덕분에 `data.sql`은 Hibernate DDL 이후에 실행된다.

**행 수: 크리에이터 3 + 강의 4 + 판매 7 + 취소 3 = 17행.** "정확히 10건"은 판매와 취소만 센 것이다.

| 판매 | 강의 | 크리에이터 | 금액 | 결제 (KST) | 저장값 (UTC) |
|---|---|---|---|---|---|
| sale-1 | course-1 | creator-1 | 50,000 | 2025-03-05 10:00 | `2025-03-05T01:00:00Z` |
| sale-2 | course-1 | creator-1 | 50,000 | 2025-03-15 14:30 | `2025-03-15T05:30:00Z` |
| sale-3 | course-2 | creator-1 | 80,000 | 2025-03-20 09:00 | `2025-03-20T00:00:00Z` |
| sale-4 | course-2 | creator-1 | 80,000 | 2025-03-22 11:00 | `2025-03-22T02:00:00Z` |
| sale-5 | course-3 | creator-2 | 60,000 | 2025-01-31 23:30 | `2025-01-31T14:30:00Z` |
| sale-6 | course-3 | creator-2 | 60,000 | 2025-03-10 16:00 | `2025-03-10T07:00:00Z` |
| sale-7 | course-4 | creator-3 | 120,000 | 2025-02-14 10:00 | `2025-02-14T01:00:00Z` |

| 취소 | 원본 | 금액 | 취소 (KST) | 저장값 (UTC) |
|---|---|---|---|---|
| cancel-1 | sale-3 | 80,000 | 2025-03-25 10:00 | `2025-03-25T01:00:00Z` |
| cancel-2 | sale-4 | 30,000 | 2025-03-26 10:00 | `2025-03-26T01:00:00Z` |
| cancel-3 | sale-5 | 60,000 | 2025-02-03 10:00 | `2025-02-03T01:00:00Z` |

**취소 시각은 Task 2가 정한다.** Task 3 기준표는 날짜만 확정했다. 세 건 모두 10:00 KST로 고정한다. 경계에서 멀어 어떤 구간 구현에서도 귀속 월이 흔들리지 않는다. 이 선택을 README에 남긴다.

`data.sql`에는 KST 표기를 주석으로 병기한다. `sale-5`는 KST 1월 31일 23:30이지만 저장값이 `2025-01-31T14:30:00Z`라, 주석이 없으면 UTC 값만 보고 1월이 맞는지 판단할 수 없다.

**이 17행이 시드의 전부다.** Task 3의 추가 테스트 케이스(수수료 버림 33,333원 등)를 시드에 넣으면 creator-1의 3월 기대값 120,000원이 깨진다. 추가 케이스는 테스트 픽스처로만 만든다.

### 검산 결과

이 시드로 Task 3 기준표의 월별 6행과 운영자 기간 2행을 계산한 결과가 전부 일치한다. creator-2의 1~3월 기간 집계가 월별 합(36,000)이 아니라 48,000으로 나오는 것도 확인했다.

## 서브태스크

| # | 제목 | 핵심 |
|---|---|---|
| 2.1 | 판매·취소 엔티티 | `SaleEntity`, `CancelEntity`. `long` 금액, `Instant` 시간, `String` ID |
| 2.2 | 크리에이터·강의 엔티티 | `CreatorEntity`, `CourseEntity`. creator-3을 운영자 목록에 0원으로 넣으려면 존재 자체가 조회돼야 한다 |
| 2.3 | 인덱스 정의 | `(course_id, paid_at)`, `(sale_id, cancelled_at)` — 현행 유지 |
| 2.4 | Spring Data 리포지토리 | 판매·취소·크리에이터·**강의** 4종. 반열린 구간 쿼리 메서드. 강의 리포지토리는 Task 4의 `CourseNotFound` 판정에 필요하다 |
| 2.5 | `SalesQueryPort` JPA 어댑터 | 4개 메서드 구현. 엔티티 → 값 타입 변환. `null` 반환 금지, 빈 컬렉션 입력 방어. **Task 3 포트 확정 후 착수** |
| 2.6 | `data.sql` 17행 | KST 주석 병기 |
| 2.7 | 시드 재현성 테스트 | 건수, 취소↔판매 연결, `sale-5` 귀속월 단언 |

## 완료 기준

- 기동 후 크리에이터 3, 강의 4, 판매 7, 취소 3이 조회된다.
- `sale-5`의 `paidAt`이 `2025-01-31T14:30:00Z`이고 KST 1월에 귀속된다.
- `findCancelsBySaleIds(["sale-5"])`가 기간 조건 없이 `cancel-3`을 돌려준다.
- 실적이 없는 크리에이터도 `findAllCreatorIds()`에 포함된다.
- 포트 메서드 4개가 결과 없을 때 빈 리스트를 돌려준다.
- `findCancelsBySaleIds(emptyList())`가 쿼리 없이 빈 리스트를 돌려준다.
- 리포지토리 4종이 있고, 강의 리포지토리로 존재 여부를 조회할 수 있다.
- `./gradlew test` 통과.

## 위험

- **`data.sql`이 DDL보다 먼저 실행되면 전부 실패한다.** Task 1이 `defer-datasource-initialization=true`를 넣어 막았다. 이 설정이 지워지면 Task 2가 통째로 깨진다.
- **`Instant` 매핑을 놓치고 `LocalDateTime`을 쓰면** `sale-5`가 조용히 2월로 귀속된다. 2.7이 이것만 잡는 단언을 갖는다.

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|-----:|--------|----------|
| CEO Review | `/plan-ceo-review` | 범위와 전략 | 1 | RESOLVED | mode: HOLD_SCOPE. 5개 계획서 한 세트로 검수 |
| Outside Voice | Codex (독립) | 교차 검증 | 1 | RESOLVED | 10건 적발, 10건 전부 반영 |
| Eng Review | `/plan-eng-review` | 아키텍처와 테스트 | 0 | — | 미실행 |
| Design Review | `/plan-design-review` | UI/UX | 0 | SKIPPED | 백엔드 전용 |
| DX Review | `/plan-devex-review` | 로컬 실행 경험 | 0 | — | 미실행 |

**세트 전체 적발:** 15건 (CEO 11-섹션) + 10건 (Outside Voice) = 25건. 사용자 판단 5건, 나머지는 유일한 정답이라 직접 반영.

**CROSS-MODEL:** Codex가 시드 데이터의 정산 재현성을 독립적으로 재검산해 일치를 확인했다("no mismatch", 168,000 / 264,000). 반박한 지적은 없다. 가장 값어치 있는 발견은 **쓰기 어댑터 부재** — Task 2가 만드는 포트는 조회 전용 4개뿐인데 Task 4는 저장·조회가 필요하다. CEO 검수 11개 섹션이 놓친 구현 블로커였다.

### 이 문서에 반영된 것

| # | 출처 | 모순·구멍 | 조치 |
|---|---|---|---|
| 1 | 사전 | 2.3이 인덱스를 뒤집으라고 하나 Task 3이 철회한 처방 | 현행 `(course_id, paid_at)`, `(sale_id, cancelled_at)` 유지. `findCancelsBySaleIds`는 시간 조건이 없어 `sale_id` 선행이 필요 |
| 2 | CEO §2 | `findCancelsBySaleIds(emptyList())` → `IN ()` | 어댑터가 쿼리 없이 빈 리스트 반환 |
| 3 | CEO §6 | 시드 `sale-1` 형태가 형식 제약으로 읽힘 | 고정값일 뿐이며 신규 판매는 서버 생성 UUID임을 명시 |
| 4 | OV | 리포지토리 3종으로는 `CourseNotFound` 판정 불가 | 강의 리포지토리 추가, 4종 |
| 5 | OV | 쓰기 어댑터 소유자 부재 | 커맨드 포트와 어댑터는 Task 4. Task 2는 리포지토리까지 |

시드 17행이 Task 3 기준표를 재현하는지 계산으로 검산했고 Codex가 독립 재검산했다. 월별 6행, 운영자 2행 전부 일치.

**VERDICT:** CEO + OUTSIDE VOICE CLEARED — 구현 착수 가능. eng review는 미실행.

NO UNRESOLVED DECISIONS
