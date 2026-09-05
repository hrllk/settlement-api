# Task 4.8 — API 테스트 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 의존 4.6, 4.7 · 25분 · 테스트 13

## 환경

`SaleControllerTest` — `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`.

`@WebMvcTest`를 쓰지 않는다. 유스케이스와 포트를 전부 목으로 만들어야 하는데, 그러면 검증하는 것이 배선이 아니라 목 설정이 된다. 시드가 이미 있으므로 실제 스택으로 돌린다.

`@Transactional`로 롤백한다. 등록 테스트가 시드에 데이터를 더하므로, 롤백이 없으면 Task 2·3의 시드 기반 단언이 실행 순서에 따라 깨진다.

**컨텍스트가 하나 더 생긴다는 점을 알고 있어야 한다.** `@SpringBootTest + @AutoConfigureMockMvc + @Transactional`은 Task 1의 `@SpringBootTest(classes = ...)`와 캐시 키가 다르다. Task 2의 두 `@DataJpaTest`까지 합치면 컨텍스트가 셋이고, 셋 다 `application.yml`의 `jdbc:h2:mem:creator-settlement;DB_CLOSE_DELAY=-1`을 본다. `ddl-auto=create-drop`이라 나중에 뜬 컨텍스트가 앞 컨텍스트의 테이블을 드롭하고 다시 만든다.

`data.sql`이 컨텍스트마다 재실행되므로 드롭 직후 재삽입되어 데이터가 영구히 사라지지는 않는다. `@DataJpaTest` 슬라이스가 `data.sql`을 실행한다는 것은 이 프로젝트에서 실측으로 확인했다 — `DataSourceInitializationAutoConfiguration`이 슬라이스 컨텍스트에 들어온다.

그래도 컨텍스트를 늘리지 않는 것이 낫다. **Task 5·6이 API 테스트를 추가할 때 이 파일과 같은 애노테이션 세 줄을 쓴다.** 다르게 쓰면 컨텍스트가 또 하나 늘어난다.

**등록 테스트는 2025-06을 쓴다.** 시드는 2025년 1~3월만 쓴다. 롤백이 한 번이라도 새면 creator-1의 3월 기대값 120,000원이 조용히 틀어지고, 깨지는 것은 이 파일이 아니라 다른 파일이다. 월을 분리하면 롤백이 실패해도 아무것도 안 깨진다. Task 6.2가 같은 규칙을 쓴다.

## 테스트

| # | 케이스 | 요청 | 기대 |
| --- | --- | --- | --- |
| 1 | 판매 등록 성공 | ADMIN, course-1, 2025-06-10 KST | 201, `Location` 헤더, 응답의 `saleId`가 UUID |
| 2 | 없는 강의 | ADMIN, `course-999` | **404** `COURSE_NOT_FOUND`. 500이 아니다 |
| 3 | 금액 0 | ADMIN, amount 0 | 400 `VALIDATION_FAILED` |
| 4 | 오프셋 없는 `paidAt` | `"2025-06-10T10:00:00"` | 400 `MALFORMED_REQUEST` |
| 5 | 없는 판매에 취소 | ADMIN, `sale-999` | 404 `SALE_NOT_FOUND` |
| 6 | 누적 초과 환불 | 2025-06 판매 80,000에 30,000 → 60,000 | 첫 건 201, **둘째 409** `REFUND_AMOUNT_EXCEEDED` |
| 7 | 헤더 누락 | `X-Actor-Id` 없이 등록 | **400** `INVALID_ACTOR_HEADER`. 403이 아니다 |
| 8 | CREATOR가 타인 판매 목록 | creator-2가 creator-1 조회 | **403** `ACTOR_ACCESS_DENIED` |
| 9 | `sale-5`를 1월로 조회 | creator-2, 2025-01-01~01-31 | 200, `sale-5`의 `refundStatus`가 **`FULL`** |
| 10 | 전액 환불 (합계 == 원결제액) | 2025-06 판매 80,000에 80,000 한 건 | **201.** 거부되면 안 된다 |
| 11 | CREATOR가 판매 등록 시도 | creator-1 / CREATOR → `POST /api/sales` | **403** `ACTOR_ACCESS_DENIED` |
| 12 | 잘못된 연월 | `from=2025-13-01` | 400 `INVALID_SETTLEMENT_PERIOD` |
| 13 | `to` 파라미터 누락 | `from`만 보냄 | 400 `MISSING_PARAMETER` |

## 각 케이스가 잡는 것

**2번** — Task 2가 FK 제약을 걸지 않았다. `courseExists` 검사가 없으면 유령 판매가 그냥 등록되거나, FK가 있었다면 `DataIntegrityViolationException`이 500으로 샌다. 채점자가 curl 한 번으로 밟는 경로다.

**4번** — Jackson이 `Instant`에 오프셋 없는 값을 UTC로 조용히 파싱하는 함정. DTO를 `OffsetDateTime`으로 둔 이유가 이것이다. 이 테스트가 없으면 타입을 `Instant`로 되돌려도 아무도 모른다.

**6번** — 단건 비교 구현을 잡는다. `amount > sale.amount()`만 보면 30,000과 60,000이 각각 통과해 총 90,000원이 환불된다. 누적 판정만이 이걸 막는다.

**7번** — 400과 403이 다른 경로임을 고정한다. 헤더 누락은 신원을 **모르는** 것이고, 인가 실패는 신원을 **알고 거부**하는 것이다. 둘을 같은 코드로 내면 클라이언트가 재시도해야 할지 포기해야 할지 판단할 수 없다. 응답 포맷 통일도 여기서 검증된다 — Task 1의 `ResponseStatusException`이 4.1 처리기에 흡수됐는지 본다.

**10번** — 부등호 하나짜리 실수를 잡는다. 4.3의 판정이 `>` 대신 `>=`이면 전액 환불이 거부된다. 시드의 `cancel-1`이 정확히 이 경우(`sale-3` 80,000원 전액)인데, **시드는 `data.sql`로 들어가므로 API를 안 거친다.** 이 테스트가 없으면 `>=` 실수를 잡는 것이 아무것도 없다.

**11번** — `requireAdmin` 실패 경로를 덮는다. 8번은 `requireSelfOrAdmin`만 검증한다. 두 메서드는 다른 분기이고, 등록 엔드포인트가 무방비면 크리에이터가 자기 정산을 부풀릴 수 있다.

**12번** — 4.4가 "`2025-13`의 거부는 도메인 규칙이므로 도메인이 판정하게 둔다"고 정하고 검증을 여기 위임했다. 이 케이스가 없으면 컨트롤러가 `@RequestParam LocalDate`로 바인딩해도 아무도 못 잡는다. 그러면 Spring의 `MethodArgumentTypeMismatchException`이 먼저 나 `InvalidSettlementPeriod`가 영영 안 걸리고, 오류 코드가 조용히 달라진다.

**13번** — 4.1이 "안 잡으면 '모든 실패가 한 가지 모양'이라는 주장이 거짓이 된다"고 강조한 항목이다. 강조해 놓고 테스트가 없으면 핸들러를 지워도 아무도 모른다.

**9번** — 이 태스크에서 가장 틀리기 쉬운 규칙. `findCancels(1/1, 2/1, creator-2)`로 환불 상태를 만들면 `cancel-3`(2월 3일)이 창에 안 잡혀 `NONE`이 나온다. `findCancelsBySaleIds`를 써야 `FULL`이 된다. 금액 집계는 기간으로 나뉘고 환불 상태는 안 나뉜다는 규칙 전체가 이 한 줄에 달려 있다.

## 하지 않는 것

| 검증 | 소유 |
| --- | --- |
| 정산 금액 계산, 월별 기대값, 경계 3방향 | Task 3 |
| 운영자 집계, 정산 조회 접근 경계 | Task 5 |
| opt-in 가드 (모든 메서드가 `ActorContext` 선언) | Task 5.6 |
| HTTP 통합 흐름 1건 | Task 6.2 |

**Task 4는 자기가 만든 규칙만 단언한다.** 계산 결과를 여기서 다시 단언하면 시드를 바꿀 때 고칠 곳이 하나 더 늘어난다.

## 파일

`src/test/java/.../adapter/in/web/SaleControllerTest.java`.

## 완료 기준

1. 13건이 통과한다.
2. 2번이 404다. 500이 아니다.
3. 7번이 400이고 8번이 403이다.
4. 9번의 `refundStatus`가 `FULL`이다.
5. 10번이 201이다. 전액 환불이 거부되지 않는다.
6. 11번이 403이다.
7. 응답 본문이 전부 `{code, message, status}` 모양이다.
7-b. 4.1 오류 코드 표의 9종 중 8종이 이 파일에서 단언된다. 나머지 하나(`REFUND_AMOUNT_EXCEEDED`)도 6번이 덮으므로 9종 전부 커버된다.
8. 이 파일을 돌린 뒤 Task 2·3의 시드 기반 단언이 계속 통과한다.
