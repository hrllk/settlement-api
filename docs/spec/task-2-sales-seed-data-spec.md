# Task 2 — 판매 데이터와 시나리오 초기 데이터 명세

## 배경

정산 계산기가 읽을 자료를 영속화하고, Task 3 기준표를 그대로 재현하는 초기 데이터를 만든다. 계산 규칙과 API는 범위 밖이다.

## 현재 상태

- 계획: `docs/plan/task-2-sales-seed-data-plan.md`
- Task 1 완료: Java 21, Spring Boot 4.1.1, 메모리 H2, `ddl-auto=create-drop`, `defer-datasource-initialization=true`
- Task 3에서 오는 것: `SalesQueryPort` 인터페이스, `SaleData`, `CancelData` 값 타입
- **2.5는 Task 3의 포트가 컴파일된 뒤에만 착수할 수 있다.** 2.1~2.4와 2.6은 먼저 할 수 있다.

## 읽기와 쓰기의 경계

Task 2는 **읽기 쪽 전부와 두 방향이 공유하는 영속성 모델**을 만든다.

| | Task 2 | Task 4 |
| --- | --- | --- |
| 엔티티 | `SaleEntity`, `CancelEntity`, `CreatorEntity`, `CourseEntity` | — |
| Spring Data 리포지토리 | 4종 | 재사용 |
| 정산 조회 | `SalesQueryPort` 어댑터 | — |
| 판매 등록·취소 | — | `Sale` 애그리게이트 + 도메인 `SaleRepository` + 어댑터 |

초과 환불 불변식(누적 취소 ≤ 원결제)은 Task 4의 애그리게이트가 강제한다. Task 2는 그 규칙을 알지 않는다. 엔티티에 setter도 검증도 두지 않는 이유가 여기 있다.

## 서브태스크

| ID | 명세 | 의존 | 예상 | 테스트 |
| --- | --- | --- | ---: | ---: |
| 2.1 | [판매·취소 엔티티](./task-2-1-sale-cancel-entity-spec.md) | — | 10분 | 0 |
| 2.2 | [크리에이터·강의 엔티티](./task-2-2-creator-course-entity-spec.md) | — | 5분 | 0 |
| 2.3 | [인덱스 정의](./task-2-3-index-spec.md) | 2.1, 2.2 | 5분 | 0 |
| 2.4 | [Spring Data 리포지토리 4종](./task-2-4-repository-spec.md) | 2.1, 2.2 | 10분 | 0 |
| 2.5 | [`SalesQueryPort` JPA 어댑터](./task-2-5-query-port-adapter-spec.md) | 2.4, Task 3.5 | 15분 | 5 |
| 2.6 | [`data.sql` 초기 데이터 17행](./task-2-6-seed-data-spec.md) | 2.1, 2.2 | 10분 | 0 |
| 2.7 | [시드 재현성 테스트](./task-2-7-seed-verification-spec.md) | 2.5, 2.6 | 10분 | 4 |

약 55분, 새 테스트 9건.

## 테스트 환경

2.5와 2.7은 H2가 필요하므로 `@DataJpaTest`를 쓴다. `@SpringBootTest`보다 가볍고 트랜잭션 롤백이 기본이다. **단 `@DataJpaTest`는 기본으로 내장 DB를 새로 띄우므로 `@AutoConfigureTestDatabase(replace = NONE)`을 붙여 `application.yml`의 H2와 `data.sql`을 그대로 쓴다.** 이걸 빼면 시드가 없는 빈 DB에서 돌아 2.7이 통째로 실패한다.

### Spring Boot 4 임포트 경로

Boot 4가 테스트 자동설정을 모듈별로 쪼갰다. **Boot 3 임포트를 쓰면 컴파일이 안 된다.**

| 애노테이션 | 패키지 |
| --- | --- |
| `@SpringBootTest` | `org.springframework.boot.test.context` (그대로) |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.jdbc.test.autoconfigure` |
| `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure` |

`spring-boot-test-autoconfigure` jar에는 이 셋이 없다. Task 1이 넣은 `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`가 각각 가져온다.

## 엔티티 개요

```text
creators   id(PK)  name
   ▲
   │ creator_id
courses    id(PK)  creator_id  title
   ▲
   │ course_id
sales      id(PK)  course_id  amount(long)  paid_at(Instant)
   ▲
   │ sale_id
cancels    id(PK)  sale_id  amount(long)  cancelled_at(Instant)
```

판매는 크리에이터를 직접 갖지 않는다. 강의를 통해 안다. 크리에이터로 좁히는 조회는 전부 조인이다.

## 크로스 서브태스크 규칙

**시간은 전부 `Instant`다.** Task 3 전제 2에 맞춘다. `LocalDateTime`을 쓰면 값이 KST인지 UTC인지 코드에 안 남아 `sale-5`가 조용히 2월로 귀속된다. 2.7이 이것만 잡는 단언을 갖는다.

**금액은 원 단위 `long`, `NOT NULL`.** 부동소수점을 쓰지 않는다.

**연관은 `@ManyToOne`이 아니라 식별자 컬럼으로 둔다.** 지연 로딩 프록시와 N+1이 끼어들 여지를 없앤다. 어댑터가 필요한 조인을 JPQL로 명시한다.

**식별자는 `String`이다.** 시드의 `sale-1` 형태는 사람이 읽기 좋은 고정값일 뿐 형식 제약이 아니다. Task 4가 만드는 판매는 서버 생성 UUID를 쓴다. 컬럼 길이는 UUID 36자를 담을 수 있게 잡는다.

**엔티티는 도메인으로 새지 않는다.** 2.5 어댑터가 엔티티를 `SaleData` / `CancelData`로 변환한다. `domain` 패키지가 `adapter.out`을 참조하는 일은 없다.

## 책임 분담

| 책임 | 소유 |
| --- | --- |
| 엔티티, 리포지토리, 조회 포트 어댑터, 시드 | **Task 2** |
| `SalesQueryPort` 인터페이스 선언, 값 타입 | Task 3 |
| 판매 목록 조회 `SalesQueryPort`와 그 어댑터 | **Task 4** |
| `Sale` 애그리게이트와 도메인 `SaleRepository` | **Task 4** |
| 초과 환불 거부, 금액 부호 검증 | Task 4 |
| 정산 계산 | Task 3 |

커맨드 포트를 Task 4가 갖는 이유는 포트의 모양이 유스케이스에서 나오기 때문이다. Task 2 시점에는 등록 유스케이스가 없어 시그니처를 추측해야 한다. **Task 2는 그 어댑터가 쓸 리포지토리까지만 준비한다.**

## 제외 범위

- 정산 계산 규칙, 값 타입 정의 — Task 3
- 컨트롤러, DTO, 전역 예외 처리기, 커맨드 포트 — Task 4
- 정산 조회 유스케이스 — Task 5
- 정산 스냅샷 엔티티, 수수료율 이력, 상태 전이 — 과제 명시적 제외

## 파일

| 경로 | 서브태스크 |
| --- | --- |
| `adapter/out/persistence/SaleEntity.java`, `CancelEntity.java` | 2.1, 2.3 |
| `adapter/out/persistence/CreatorEntity.java`, `CourseEntity.java` | 2.2, 2.3 |
| `adapter/out/persistence/SaleRepository.java`, `CancelRepository.java`, `CreatorRepository.java`, `CourseRepository.java` | 2.4 |
| `adapter/out/persistence/SalesQueryJpaAdapter.java` | 2.5 |
| `src/main/resources/data.sql` | 2.6 |

`adapter/out/.gitkeep`은 2.1이 지운다.

테스트는 `src/test/java/.../adapter/out/persistence/`에 `SalesQueryJpaAdapterTest`(2.5), `SeedDataTest`(2.7).

## 완료 기준

1. `./gradlew test`가 통과한다.
2. 기동 후 크리에이터 3, 강의 4, 판매 7, 취소 3이 조회된다.
3. `sale-5`의 `paidAt`이 `2025-01-31T14:30:00Z`이고 KST 1월에 귀속된다.
4. `findCancelsBySaleIds(List.of("sale-5"))`가 기간 조건 없이 `cancel-3`을 돌려준다.
5. `findCancelsBySaleIds(List.of())`가 쿼리 없이 빈 리스트를 돌려준다.
6. `findAllCreatorIds()`가 실적 없는 크리에이터를 포함해 3건을 돌려준다.
7. 포트 메서드 4개가 결과 없을 때 빈 리스트를 돌려준다.
8. `domain` 패키지가 `adapter.out`을 참조하지 않는다.
9. Task 1·3의 기존 테스트가 계속 통과한다.

## 롤백 · 소요

신규 파일만 추가한다. 인메모리 DB라 데이터 마이그레이션이 없다. 커밋을 되돌리면 된다. 약 55분.

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
| --- | --- | --- | ---: | --- | --- |
| CEO Review | `/plan-ceo-review` | 범위와 전략 | 1 | CLEAR | 계획 단계에서 HOLD_SCOPE 확정, 25건 반영 |
| Eng Review | `/plan-eng-review` | 아키텍처와 테스트 | 1 | CLEAR | 4건 적발, 전부 반영 |
| Outside Voice | Codex (독립) | 교차 검증 | 1 | CLEAR | 블로커 6 + 중간 3, 전부 검증 후 반영 |
| Design Review | 해당 없음 | UI/UX | 0 | SKIPPED | 백엔드 전용 |
| DX Review | 해당 없음 | 로컬 실행 | 0 | SKIPPED | Task 1에서 완료 |

**세트 검수 결과.** 38건을 한 세트로 봤다. 따로 봤으면 안 잡혔을 것이 대부분이다.

**엔지니어링 검수 4건**
1. 헥사고날 방향 역전. 유스케이스가 `ActorContext`를 받으면서 `application → adapter.in` 의존이 생겼다. 액터 타입을 `application.actor`로 옮기고 해석기만 어댑터에 남겼다.
2. `findSaleById`가 `SaleData`를 돌려주는데 `creatorId`를 채울 방법이 없어 NPE. 전용 `SaleRecord`를 신설했다.
3. 5.2와 5.3이 조회·계산 세 줄을 복제. `SettlementQuery`로 뺐다.
4. 테스트 공백 2건(전액 환불 경계, CREATOR 등록 403)과 `NoCurrentTimeUsageTest` 자기 매칭 버그.

**Outside Voice 6 + 3 (전부 실물로 검증)**
1. `FixedRateFeePolicy.PLATFORM_DEFAULT_BP`가 없다. 검수 도중 Task 3 세션이 지웠다. 설정 프로퍼티 주입으로 바꿨다.
2. `saveCancel`이 `void`인데 `CancelResponse`가 `cancelId`를 요구.
3. Task 3의 `SaleData`에 `courseId`가 없는데 `SaleItem`이 요구. 판매 목록을 `SalesQueryPort`의 읽기 모델로 분리했다.
4. 우산 파일 목록과 4.1의 `ActorAccessPolicy` 경로 불일치.
5. Task 3의 `SettlementFixtures`가 package-private이라 Task 6이 import 불가. Task 6은 애초에 그게 필요 없어 분리 확인으로 바꿨다.
6. 서브태스크 의존성이 교차 Task를 안 적어 DAG가 거짓.
7. **Boot 4 테스트 애노테이션 패키지 이동.** `spring-boot-test-autoconfigure` jar에 `@DataJpaTest`·`@AutoConfigureTestDatabase`·`@AutoConfigureMockMvc`가 없다. jar를 열어 확인했다. Boot 3 임포트를 쓰면 컴파일이 안 된다.
8. 목록 쿼리와 크리에이터 목록에 정렬 계약이 없어 응답 순서가 비결정적.
9. `from`/`to` 누락 시 `MissingServletRequestParameterException`이 포맷 통일을 깬다.

**CROSS-MODEL:** Codex가 낸 9건 중 반박한 것은 없다. 전부 실제 코드와 jar를 열어 확인했다. 1번은 제가 처음 읽었을 때 존재했으나 검수 도중 다른 세션이 지운 것이라, 두 번째 확인이 없었으면 놓쳤을 지점이다. 7번은 Boot 4 모듈 분할을 몰랐으면 구현자가 컴파일 오류로 시간을 태웠을 항목이다.

**테스트 78건** — Task 1: 4, Task 2: 9, Task 3: 42, Task 4: 11, Task 5: 10, Task 6: 2.

**VERDICT:** CEO + ENG + OUTSIDE VOICE CLEARED — 구현 착수 가능.

NO UNRESOLVED DECISIONS
