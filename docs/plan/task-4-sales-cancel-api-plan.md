# Task 4 — 판매·취소 API: 검토 계획

## 목표

판매 등록, 취소 등록, 크리에이터별 기간 판매 목록 조회를 HTTP로 연다. 전역 예외 처리기도 여기서 완성한다.

## 선행 조건

`tasks.json`의 의존성을 `[2]`에서 `[2,3]`으로 고쳤다. 4.1이 Task 3의 `InvalidSettlementPeriod`를 참조하기 때문이다.

- Task 2: 엔티티, 리포지토리, 포트 어댑터
- Task 3: `InvalidSettlementPeriod`, `RefundStatus`, `findCancelsBySaleIds`
- Task 1: `ActorContext`와 헤더 해석기

## 포함 범위

- 도메인 예외 5종과 전역 예외 처리기
- 접근 경계 정책 `ActorAccessPolicy`와 도메인 빈 등록
- 판매 등록, 취소 등록, 크리에이터별 판매 목록 유스케이스
- 커맨드 포트 `SaleCommandPort`와 그 JPA 어댑터
- 요청·응답 DTO, Bean Validation
- `SaleController`
- application 계층 로깅
- API 테스트

## 제외 범위

- 정산 조회 — Task 5
- 엔티티, Spring Data 리포지토리, 조회 포트 어댑터 — Task 2
- 계산 규칙 — Task 3
- 역할 매트릭스 문서화 — Task 5. Task 4는 정책의 **구현**을 소유하고 Task 5는 어느 엔드포인트에 어떤 규칙이 붙는지를 소유한다

## 확정된 전제

1. **4.1을 가장 먼저 한다.** 전역 예외 처리기가 없으면 나머지 서브태스크의 실패 경로가 전부 500 스택트레이스로 나간다. Task 5도 이 처리기에 의존한다.

2. **예외 5종과 상태 코드.**

   | 예외 | 상태 | 소유 |
   |---|---|---|
   | `SaleNotFound` | 404 | Task 4 |
   | `CourseNotFound` | 404 | Task 4 |
   | `RefundAmountExceeded` | 409 | Task 4 |
   | `InvalidSettlementPeriod` | 400 | 정의는 Task 3, 변환은 Task 4 |
   | `ActorAccessDenied` | 403 | 정의·변환은 Task 4, 사용은 Task 5 |

   `CourseNotFound`가 없으면 `POST /api/sales`에 없는 `courseId`를 주었을 때 FK 위반이 `DataIntegrityViolationException`으로 새어 500이 나간다. 채점자가 curl 한 번으로 밟는 경로다.

   **Bean Validation 실패도 전역 처리기가 잡는다.** `MethodArgumentNotValidException`을 잡지 않으면 Spring 기본 포맷으로 나가 아래 3번의 포맷과 모양이 달라진다. 오류 응답이 두 가지 모양이 되면 README에 둘 다 적어야 한다.

   `RefundAmountExceeded`를 400이 아니라 **409로 둔다.** 요청 형식은 올바르고 현재 자원 상태와 충돌하는 것이므로 409가 맞다. 400으로 두면 "금액 필드가 잘못됐다"는 Bean Validation 실패와 구분되지 않는다.

3. **오류 응답 포맷을 여기서 확정한다.** `{ "code": "...", "message": "...", "status": 409 }`. Task 5가 같은 포맷을 쓴다.

4. **전역 처리기가 `ResponseStatusException`도 잡는다.** Task 1의 `ActorContextArgumentResolver`가 헤더 오류에 이 예외를 던지는데, 그대로 두면 Spring 기본 본문으로 나가 아래 3번의 포맷과 모양이 달라진다. Task 1 코드는 고치지 않고 처리기에서 흡수한다.

5. **접근 경계 정책 `ActorAccessPolicy`를 Task 4가 소유한다.** 두 연산만 둔다.

   ```java
   void requireSelfOrAdmin(ActorContext actor, String creatorId);  // 위반 시 ActorAccessDenied
   void requireAdmin(ActorContext actor);
   ```

   Task 4가 `ActorAccessDenied`의 정의와 403 변환을 이미 가지고 있으므로, 그 예외를 던지는 쪽도 같은 Task에 둔다. 정책이 Task 5에 있으면 Task 4가 Task 5를 호출하는데 Task 5는 Task 4의 전역 처리기를 기다리는 순환이 된다. **어느 엔드포인트에 어떤 규칙이 붙는지(역할 매트릭스)는 Task 5가 소유한다.** 구현과 배치를 나눈다.

   같은 이유로 **도메인 빈 등록도 Task 4가 한다.** `FeePolicy`, `FixedRateFeePolicy(2000bp)`, `SettlementCalculator`를 `config`에서 `@Bean`으로 둔다. Task 4는 Spring 배선이 생기는 첫 Task다. 등록을 Task 5로 미루면 Task 4가 도메인 빈을 하나라도 쓰는 순간 깨진다.

6. **새 판매의 ID는 서버가 UUID로 생성한다.** 응답 본문과 `Location` 헤더에 담는다. 시드의 `sale-1` 형태는 사람이 읽기 좋은 고정값일 뿐 형식 제약이 아니다. 클라이언트가 ID를 정하면 `sale-1`을 보내 시드를 덮어쓸 수 있고, `sale-{n}` 시퀀스는 동시 요청에서 경합한다.

6-b. **헤더 형식 오류(400)와 인가 실패(403)는 다른 경로다.** 400은 `ActorContextArgumentResolver`가 `ResponseStatusException`으로 이미 던진다. 403은 유스케이스가 `ActorAccessDenied`를 던지고 전역 처리기가 변환한다. 두 경로가 섞이지 않는지 4.8이 단언한다.

7. **커맨드 포트 `SaleCommandPort`와 그 어댑터를 Task 4가 소유한다.**

   ```java
   String save(NewSale sale);              // 서버 생성 UUID를 돌려준다
   Optional<SaleRecord> findById(String saleId);
   // 누적 초과 환불 판정은 Sale 애그리게이트가 소유한다 (명세 4.2에서 개편)
   void saveCancel(NewCancel cancel);
   boolean courseExists(String courseId);  // CourseNotFound 판정용
   ```

   Task 2의 `SalesQueryPort`는 조회 메서드 4개뿐이라 등록 경로를 덮지 못한다. 포트의 모양은 유스케이스가 무엇을 필요로 하는지에서 나오므로, 그것을 아는 Task 4가 선언과 구현을 함께 갖는다. 어댑터는 Task 2가 만든 Spring Data 리포지토리를 감싼다.

   **Task 2의 리포지토리는 4종이어야 한다.** 판매·취소·크리에이터에 **강의**를 더한다. `courseExists`가 없으면 `CourseNotFound`를 판정할 수 없다.

8. **초과 환불 판정은 누적이다.** `기존 취소 합계 + 신규 환불액 > 원결제액`이면 거부한다. 단건 비교가 아니다. 30,000 + 60,000 > 80,000 케이스가 이것만 잡는다.

9. **동시성은 보장하지 않는다.** 같은 판매에 취소 두 건이 동시에 들어오면 합계 검사를 둘 다 통과할 수 있다. 3시간 예산에서 비관적 락이나 유니크 제약을 넣지 않는다. **README에 가정으로 명시한다.** 실무라면 `SELECT ... FOR UPDATE`나 판매 행 버전 컬럼으로 막을 지점이라는 설명을 붙인다.

10. **판매 목록의 환불 상태에는 기간 필터를 적용하지 않는다.** Task 3 기준표에 따라 `findCancelsBySaleIds`로 그 판매의 모든 취소를 본다. `sale-5`를 1월 조회로 꺼내도 환불 상태는 `FULL`이다. `findCancels(from, to, ...)`로 산출하면 `NONE`이 나온다.

11. **모든 컨트롤러 메서드가 `ActorContext`를 선언한다.** 해석기는 필터가 아니라 opt-in이다. 선언을 빠뜨린 메서드는 헤더 검사를 통째로 건너뛴다. 리플렉션 가드 테스트는 Task 5.7이 소유하고 Task 4 컨트롤러까지 함께 검사한다.

12. **로깅은 application 계층에만 둔다.** PRD가 "핵심 명령·조회 성공과 도메인 실패"를 요구하므로 판매 등록 성공, 취소 등록 성공, **판매 목록 조회 성공**, 도메인 예외 5종을 남긴다. 초판은 조회를 뺐으나 PRD와 어긋나 되돌렸다. 도메인에 SLF4J를 넣지 않는다. Task 3 전제 11과 같다.

13. **날짜는 `String`으로 받는다.** `@RequestParam LocalDate`로 바인딩하면 Spring이 `MethodArgumentTypeMismatchException`을 먼저 던져 `InvalidSettlementPeriod`가 안 걸린다.

## API

| 메서드 | 경로 | 액터 |
|---|---|---|
| POST | `/api/sales` | ADMIN |
| POST | `/api/sales/{saleId}/cancellations` | ADMIN |
| GET | `/api/creators/{creatorId}/sales?from=&to=` | CREATOR 본인 또는 ADMIN |

### 요청·응답 계약

시각은 전부 **오프셋을 포함한 ISO-8601 문자열**로 주고받는다. `2025-03-05T10:00:00+09:00`처럼 쓴다. 오프셋 없는 값은 거부한다. 오프셋이 없으면 서버가 무슨 시간대로 읽었는지 요청만 보고 알 수 없고, `sale-5`가 1월인지 2월인지 갈리는 바로 그 함정이다.

```
POST /api/sales
{ "courseId": "course-1", "amount": 50000, "paidAt": "2025-03-05T10:00:00+09:00" }
→ 201  { "saleId": "<UUID>", ... }   Location: /api/sales/<UUID>

POST /api/sales/{saleId}/cancellations
{ "amount": 30000, "cancelledAt": "2025-03-26T10:00:00+09:00" }
→ 201  { "cancelId": "<UUID>", ... }

오류(전부 같은 모양)
→ { "code": "REFUND_AMOUNT_EXCEEDED", "message": "...", "status": 409 }
```

Task 6 통합 테스트와 Task 7 README curl 예시가 이 계약을 그대로 쓴다.

판매·취소 등록을 ADMIN으로 제한하는 것은 판단이다. 원본 과제에 명시가 없다. 크리에이터가 자기 강의의 판매를 임의로 등록할 수 있으면 정산을 스스로 부풀릴 수 있다. 등록은 결제 시스템이 하는 일이라고 보고 운영자로 좁힌다. README에 가정으로 남긴다.

`GET /api/creators/{creatorId}/sales`는 `ActorAccessPolicy#requireSelfOrAdmin`을 호출한다. **정책 구현은 Task 4가, 어느 엔드포인트에 무엇이 붙는지는 Task 5의 역할 매트릭스가 소유한다.** 규칙을 두 곳에 복제하지 않는다.

## 서브태스크

| # | 제목 | 핵심 |
|---|---|---|
| 4.1 | 도메인 예외 + 전역 예외 처리기 + 접근 정책 + 도메인 빈 등록 | 예외 5종, Bean Validation 포함, 응답 포맷 확정, `ActorAccessPolicy`, `@Bean` 등록. **가장 먼저** |
| 4.2 | 판매 등록 유스케이스 + `SaleCommandPort` + JPA 어댑터 | 서버가 UUID 생성, 없는 강의는 `CourseNotFound` |
| 4.3 | 취소 등록 유스케이스 | 누적 초과 환불 거부. 동시성 미보장은 README |
| 4.4 | 크리에이터별 기간 판매 목록 | 환불 상태는 기간 필터 미적용 |
| 4.5 | DTO와 Bean Validation | 금액 양수, 필수 필드, 날짜는 `String` |
| 4.6 | `SaleController` | 3개 엔드포인트. 모든 메서드가 `ActorContext` 선언 |
| 4.7 | 로깅 | application 계층에서만. 등록 성공 2종 + 도메인 예외 5종 |
| 4.8 | API 테스트 | 아래 |

## 테스트

| 케이스 | 기대 |
|---|---|
| 누적 초과 환불 (30,000 + 60,000 > 80,000) | 409 `RefundAmountExceeded` |
| 없는 판매에 취소 | 404 `SaleNotFound` |
| 없는 강의로 판매 등록 | 404 `CourseNotFound` (500 아님) |
| Bean Validation 실패 | 400, `{code, message, status}` 포맷 |
| 오프셋 없는 `paidAt` | 400 |
| CREATOR가 타인 판매 목록 조회 | 403 (엔드포인트를 소유한 Task 4가 단언) |
| CREATOR가 본인 판매 목록 조회 | 200 |
| `ActorAccessDenied` 변환 | 403 |
| 헤더 누락 | 400 (403이 아님) |
| 잘못된 역할값 | 400 |
| `sale-5`를 1월 조회 | 환불 상태 `FULL` |
| 금액 0 또는 음수로 판매 등록 | 400 |

Task 3이 소유한 계산 단언은 여기서 반복하지 않는다.

## 완료 기준

- 3개 엔드포인트가 동작하고 오류 5종이 정해진 상태 코드로 나온다.
- 모든 오류 응답이 `{code, message, status}` 한 가지 모양이다. Bean Validation 실패와 액터 헤더 오류도 포함한다.
- `SaleCommandPort`와 어댑터가 있어 등록 경로가 영속화된다.
- `ActorAccessPolicy`와 도메인 빈이 등록되어 Task 5가 바로 쓸 수 있다.
- 400과 403이 서로 다른 경로에서 나오는 것이 테스트로 고정된다.
- 누적 초과 환불이 거부된다.
- `./gradlew test` 통과.

## 위험

- **4.1을 나중에 하면 나머지 전부를 다시 손대야 한다.** 순서가 곧 비용이다.
- **`RefundAmountExceeded`를 409로 둔 것은 원본 과제에 명시가 없는 판단이다.** 평가자가 400을 기대할 수 있다. README에 근거를 남긴다.

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
| 1 | CEO §1 | **순환 의존** — Task 4가 Task 5의 정책을 호출하는데 Task 5는 Task 4를 의존 | `ActorAccessPolicy` 구현을 Task 4로. 역할 매트릭스(배치 규칙)만 Task 5 |
| 2 | CEO §1 | 도메인 빈 등록이 Task 5인데 Task 4가 먼저 옴 | 빈 등록을 Task 4.1로 |
| 3 | CEO §2 | 없는 `courseId` → FK 위반이 500으로 샘 | `CourseNotFound` 404 추가 |
| 4 | CEO §2 | Bean Validation 실패가 다른 포맷으로 나감 | 전역 처리기가 `MethodArgumentNotValidException`도 흡수 |
| 5 | CEO §6 | 새 판매 ID 생성 규칙 부재 | 서버 UUID, 응답 본문과 `Location`에 담음 |
| 6 | OV | **쓰기 어댑터 부재** — 등록 경로를 영속화할 수단이 없음 | `SaleCommandPort` + JPA 어댑터를 Task 4가 소유 |
| 7 | OV | 액터 헤더의 `ResponseStatusException`이 포맷을 안 따름 | 전역 처리기가 흡수. Task 1 코드는 안 고침 |
| 8 | OV | PRD는 조회 성공 로그를 요구하는데 계획은 제외 | 판매 목록 조회 성공도 로깅 |
| 9 | OV | 판매 목록 인가 테스트가 어디에도 없음 | 엔드포인트를 소유한 Task 4가 단언 |
| 10 | OV | 요청 페이로드와 시각 포맷 미정 | ISO-8601 + 오프셋 필수. 예시 명시 |

예외가 4종에서 5종으로, 서브태스크 4.1의 범위가 넷으로 늘었다. 4.1을 가장 먼저 하는 이유가 더 강해졌다.

**VERDICT:** CEO + OUTSIDE VOICE CLEARED — 구현 착수 가능. eng review는 미실행.

NO UNRESOLVED DECISIONS
