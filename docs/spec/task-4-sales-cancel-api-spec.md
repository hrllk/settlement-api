# Task 4 — 판매·취소 API 명세

## 배경

판매 등록, 취소 등록, 크리에이터별 기간 판매 목록 조회를 HTTP로 연다. **전역 예외 처리기와 접근 경계 정책, 도메인 빈 등록도 여기서 완성한다.** Task 5가 그 셋에 의존한다.

## 현재 상태

- 계획: `docs/plan/task-4-sales-cancel-api-plan.md`
- Task 1: `ActorContext`, `ActorRole`, `ActorContextArgumentResolver`, `WebMvcConfig`
- Task 2: 엔티티 4종(Lombok `@Getter`), Spring Data 리포지토리 4종(`SaleJpaRepository`, `CancelJpaRepository`, `CourseJpaRepository`, `CreatorJpaRepository`), `SalesQueryJpaAdapter`(`@RequiredArgsConstructor`, 리포지토리 3개 주입), `data.sql` 17행
- Task 3: `SettlementPeriod`, `RefundStatus`, `InvalidSettlementPeriod`, `FeePolicy`, `SettlementCalculator`, `SaleData` / `CancelData`

## 서브태스크

| ID | 명세 | 의존 | 예상 | 테스트 |
| --- | --- | --- | ---: | ---: |
| 4.1 | [예외·전역 처리기·접근 정책·정책 빈 등록](./task-4-1-error-policy-wiring-spec.md) | Task 1·3 | 25분 | 0 |
| 4.2 | [`Sale` 애그리게이트와 판매 등록](./task-4-2-register-sale-spec.md) | 4.1, Task 2·3 | 25분 | 6 |
| 4.3 | [취소 등록 + 누적 초과 환불 거부](./task-4-3-register-cancel-spec.md) | 4.2 | 10분 | 0 |
| 4.4 | [크리에이터별 기간 판매 목록](./task-4-4-list-sales-spec.md) | 4.1, 4.2, Task 2·3 | 15분 | 0 |
| 4.5 | [DTO와 Bean Validation](./task-4-5-dto-validation-spec.md) | — | 10분 | 0 |
| 4.6 | [`SaleController`](./task-4-6-controller-spec.md) | 4.2~4.5 | 15분 | 0 |
| 4.7 | [로깅](./task-4-7-logging-spec.md) | 4.2~4.4 | 5분 | 0 |
| 4.8 | [API 테스트](./task-4-8-api-test-spec.md) | 4.6, 4.7 | 25분 | 15 |

약 130분, 새 테스트 21건 (4.2의 6건 + 4.8의 15건).

**표의 의존은 부모 Task 의존(`[2,3]`)에 더해지는 것이다.** 4.1은 Task 1의 액터 타입을 옮기고 Task 3의 `FeePolicy`·`SettlementCalculator`·`InvalidSettlementPeriod`를 참조하므로 둘 다 있어야 한다. 4.4는 Task 2의 리포지토리와 Task 3의 `SettlementPeriod`·`RefundStatus`·`findCancelsBySaleIds`를 쓴다.

**4.1을 반드시 먼저 한다.** 전역 처리기가 없으면 나머지 서브태스크의 실패 경로가 전부 500 스택트레이스로 나가고, 나중에 넣으면 이미 쓴 코드를 다시 손대야 한다.

### Spring Boot 4 임포트 경로

Boot 4가 테스트 자동설정을 모듈별로 쪼갰다. **Boot 3 임포트를 쓰면 컴파일이 안 된다.**

| 애노테이션 | 패키지 |
| --- | --- |
| `@SpringBootTest` | `org.springframework.boot.test.context` (그대로) |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.jdbc.test.autoconfigure` |
| `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure` |

`spring-boot-test-autoconfigure` jar에는 이 셋이 없다. Task 1이 넣은 `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`가 각각 가져온다.

## 예외 매핑 (Task 3 명세와 함께 읽는다)

| 예외 | 정의 | HTTP | 사용자가 유발 가능 |
| --- | --- | --- | --- |
| `SaleNotFound` | 4.1 | 404 | O |
| `CourseNotFound` | 4.1 | 404 | O |
| `RefundAmountExceeded` | 4.1 | 409 | O |
| `ActorAccessDenied` | 4.1 | 403 | O |
| `InvalidSettlementPeriod` | Task 3.1 | 400 | O |
| `MethodArgumentNotValidException` | Spring | 400 | O |
| `ResponseStatusException` (액터 헤더) | Task 1 | 400 | O |
| `IllegalArgumentException` | Task 3.2, 3.3 | **500** | X |
| `NullPointerException` | Task 3.2 | **500** | X |

**마지막 두 줄이 Task 3 명세가 명시적으로 요구한 제약이다.** `IllegalArgumentException`과 `NullPointerException`을 400으로 싸잡으면 프로그래밍 버그가 사용자 오류로 위장돼 로그에서 사라진다. 값 타입 불변식 위반은 우리 코드의 결함이지 요청의 결함이 아니다.

## 오류 응답 포맷

**RFC 9457 Problem Details를 쓴다.** Spring 내장 `ProblemDetail`이라 우리가 record를 만들지 않는다.

```json
{ "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "...", "code": "REFUND_AMOUNT_EXCEEDED" }
```

`Content-Type: application/problem+json`. `code`는 RFC 9457 확장 멤버로 남겨 기계가 읽을 판별자를 유지한다 — `type`이 `about:blank`라 그 역할이 비기 때문이다.

**사용자가 유발할 수 있는 모든 실패가 이 한 가지 모양이다.** Bean Validation 실패와 액터 헤더 오류도 포함한다. 위 표에서 500으로 남긴 `IllegalArgumentException`·`NullPointerException`은 예외다 — 우리 코드의 버그이므로 Spring 기본 500 본문으로 나가 스택트레이스가 로그에 남아야 한다. 둘을 안 잡으면 Spring 기본 본문으로 나가 포맷이 세 가지가 되고, README에 오류 예시를 세 번 적어야 한다. Task 5가 같은 처리기를 그대로 쓴다.

## 요청·응답 계약

시각은 **오프셋을 포함한 ISO-8601 문자열**로 주고받는다. 오프셋 없는 값은 거부한다. 오프셋이 없으면 서버가 무슨 시간대로 읽었는지 요청만 보고 알 수 없고, `sale-5`가 1월인지 2월인지 갈리는 바로 그 함정이다.

```
POST /api/sales                      ADMIN
{ "courseId": "course-1", "amount": 50000, "paidAt": "2025-03-05T10:00:00+09:00" }
→ 201  { "saleId": "<UUID>", "courseId": "...", "amount": 50000, "paidAt": "..." }
       Location: /api/sales/<UUID>

POST /api/sales/{saleId}/cancellations    ADMIN
{ "amount": 30000, "cancelledAt": "2025-03-26T10:00:00+09:00" }
→ 201  { "cancelId": "<UUID>", "saleId": "...", "amount": 30000, "cancelledAt": "..." }

GET /api/creators/{creatorId}/sales?from=2025-03-01&to=2025-03-31    본인 CREATOR 또는 ADMIN
→ 200  { "creatorId": "...", "sales": [ { "saleId", "courseId", "amount", "paidAt",
                                          "refundStatus": "NONE|PARTIAL|FULL" } ] }
```

Task 6 통합 테스트와 Task 7 README curl 예시가 이 계약을 그대로 쓴다.

## 책임 분담

| 책임 | 소유 |
| --- | --- |
| 예외 정의·전역 변환, 오류 포맷 | **4.1** |
| `ActorAccessPolicy` 판정 코드 | **4.1** |
| 도메인 빈 등록 (`FeePolicy`, `SettlementCalculator`) | **4.1** |
| 어느 엔드포인트에 어떤 규칙이 붙는지 (역할 매트릭스) | Task 5 |
| 판매 포트와 그 어댑터 (등록 + 목록 읽기 모델) | **4.2** |
| 조회 포트와 그 어댑터 | Task 2 |
| 정산 계산, 환불 상태 산출 | Task 3 |

## 제외 범위

- 정산 조회 API, 운영자 집계 — Task 5
- 계산 규칙 — Task 3
- 엔티티, 리포지토리, 시드 — Task 2
- opt-in 가드 테스트 — Task 5.6이 Task 4 컨트롤러까지 함께 검사한다
- 동시성 제어 — 아래 참조

## 동시성을 보장하지 않는다

같은 판매에 취소 두 건이 동시에 들어오면 누적 합계 검사를 둘 다 통과해 원결제액을 넘길 수 있다. 3시간 예산에서 비관적 락이나 버전 컬럼을 넣지 않는다. **README에 가정으로 명시한다.** 실무라면 판매 행에 `@Version`을 두거나 `SELECT ... FOR UPDATE`로 막을 지점이라는 설명을 붙인다.

## 파일

| 경로 | 서브태스크 |
| --- | --- |
| `adapter/in/web/GlobalExceptionHandler.java` | 4.1 |
| `domain/settlement/SaleNotFound.java`, `CourseNotFound.java` | 4.1 |
| `config/DomainConfig.java` | 4.1 |
| `application/sale/RegisterSaleUseCase.java`, `RegisterCancelUseCase.java`, `ListCreatorSalesUseCase.java`, `SaleWithRefundStatus.java` | 4.2~4.4 |
| `domain/sales/Sale.java`, `Cancel.java`, `SaleRepository.java`, `RefundAmountExceeded.java` | 4.2 |
| `application/port/out/SalesQueryPort.java`, `SaleRecord.java` | 4.2 |
| `application/actor/ActorContext.java`, `ActorRole.java` (Task 1에서 이동) | 4.1 |
| `application/actor/ActorAccessDenied.java`, `ActorAccessPolicy.java` (신규) | 4.1 |
| `adapter/in/actor/ActorContextArgumentResolver.java`, `config/WebMvcConfig.java` (import만) | 4.1 |
| `adapter/out/persistence/SaleRepositoryJpaAdapter.java`, `SalesQueryJpaAdapter.java` | 4.2 |
| `adapter/in/web/dto/*.java` | 4.5 |
| `adapter/in/web/SaleController.java` | 4.6 |

테스트는 `src/test/java/.../adapter/in/web/SaleControllerTest.java` (4.8).

## 완료 기준

1. `./gradlew test`가 통과한다.
2. 3개 엔드포인트가 동작한다.
3. 사용자 유발 오류 8종이 정해진 상태 코드로 나오고, 응답이 전부 RFC 9457 `application/problem+json`이며 `code` 확장 멤버를 갖는다.
3-b. `RefundAmountExceeded`가 저장소 전체에 하나만 존재한다 (`domain/sales`).
4. `IllegalArgumentException` / `NullPointerException`이 400으로 매핑되지 않는다.
5. 누적 초과 환불이 409로 거부된다.
6. 없는 강의로 판매 등록이 404로 거부된다. 500이 아니다.
7. `sale-5`를 1월로 조회해도 환불 상태가 `FULL`이다.
8. `ActorAccessPolicy`와 도메인 빈이 등록되어 Task 5가 바로 쓸 수 있다.
9. Task 1·2·3의 기존 테스트가 계속 통과한다.

## 롤백 · 소요

신규 파일만 추가한다. 다만 4.1이 Task 1의 액터 타입 2개를 옮기고 그 테스트의 import를 고치며, 4.2가 Task 2의 `SalesQueryJpaAdapter`에 메서드 둘을 더한다. 커밋을 되돌리면 된다. 약 130분.

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
| --- | --- | --- | ---: | --- | --- |
| CEO Review | `/plan-ceo-review` | 범위와 전략 | 1 | CLEAR | HOLD_SCOPE 확정 |
| Eng Review | `/plan-eng-review` | 아키텍처와 테스트 | 3 | CLEAR | 1회 4건 · 2회 12건 · **3회(Task 2 실물 대조) 3건** |
| Outside Voice | Codex (독립) | 교차 검증 | 2 | CLEAR | 1회 9건 · 2회 9건 중 8건 확인 1건 반박 |
| Design Review | 해당 없음 | UI/UX | 0 | SKIPPED | 백엔드 전용 |
| DX Review | 해당 없음 | 로컬 실행 | 0 | SKIPPED | Task 1에서 완료 |

### 3회차 — Task 2 구현 완료 후 실물 대조

Task 2가 2.1~2.7을 구현하고 빌드가 그린이 된 시점에 그 **실제 코드**를 계약으로 삼아 다시 봤다. 블로커는 없었다. 앞선 두 라운드가 걸러냈다.

**계약 확인 3건 — 전부 통과**

| 확인 | 결과 |
| --- | --- |
| `findByCreatorAndPeriod`의 정렬 | `order by s.paidAt, s.id` 실제 JPQL에 있음. `findSalesForListing`이 재사용 |
| `SaleEntity` 접근자 | Lombok `@Getter`로 존재. `Sale.restore(...)` 조립 가능 |
| 애그리게이트 적재 경로 | `CancelJpaRepository.findBySaleId` 존재 |

엔티티에 setter가 없는 것도 문제가 아니다. 판매는 불변이고 취소는 append-only라 새 `CancelEntity` 행을 만들면 된다.

**규약 불일치 3건 — 반영**

1. **`SalesQueryJpaAdapter` 생성자 확장이 명세에 없었다.** 실제 어댑터는 `@RequiredArgsConstructor`에 리포지토리 셋을 주입한다. `courseExists`를 추가하려면 `CourseJpaRepository`가 네 번째 `final` 필드로 들어가야 하는데, 명세는 "메서드 둘을 더한다"고만 했다. **필드 선언을 빼면 빈 주입 실패로 컨텍스트가 안 뜬다.** 필드·메서드 표로 명시했다.
2. **Lombok 규약이 한 줄도 없었다.** 계층별로 갈랐다 — `domain/sales`는 손으로, `adapter/out/persistence`는 Task 2와 같이 Lombok. 도메인에서 `@Getter`를 쓰면 안 되는 구체적 이유가 있다. `cancels`가 가변 `List`인데 `@Getter`는 그걸 그대로 내주어 호출자가 직접 `add`로 불변식을 우회한다. 테스트 케이스 5가 그걸 잠근다.
3. **`Sale` 계열 이름이 셋이었다.** `SaleRepository`(도메인 인터페이스), `SaleRepositoryJpaAdapter`(그 구현), `SaleJpaRepository`(Spring Data). 논리는 맞지만 한 화면에 나오면 멈춘다. 역할 표를 넣었다.

**명세 갈라짐은 오해였다.** `task2` 브랜치가 Task 4 명세를 고친 커밋은 없다. main의 2회차 수정을 아직 안 받았을 뿐이라 머지하면 깨끗이 붙는다.

**테스트 17건** — 4.2 `SaleTest` 6, 4.8 `SaleControllerTest` 11.

**VERDICT:** CEO + ENG(3회) + OUTSIDE VOICE(2회) CLEARED — 구현 착수 가능.

NO UNRESOLVED DECISIONS
