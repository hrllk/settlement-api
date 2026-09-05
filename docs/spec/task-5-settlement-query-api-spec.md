# Task 5 — 정산 조회 API 명세

## 배경

크리에이터 월별 정산과 운영자 기간 집계를 HTTP로 연다. 계산은 Task 3의 계산기가 하고, 여기서는 자료를 모아 넘기고 결과를 조립한다.

**이 태스크에 이 프로젝트에서 가장 설명하기 어려운 동작이 있다.** 운영자 기간 집계가 월별 정산의 합이 아니다. 문서가 없으면 버그로 읽힌다.

## 현재 상태

- 계획: `docs/plan/task-5-settlement-query-api-plan.md`
- Task 2: `SalesQueryJpaAdapter` (조회 포트 구현)
- Task 3: `SettlementPeriod`, `SettlementCalculator`, `SettlementSummary`, `FeePolicy`
- Task 4: 전역 예외 처리기, 오류 포맷, `ActorAccessPolicy`, `FeePolicy` 빈

`tasks.json`의 의존성을 `[2,3]`에서 `[2,3,4]`로 고쳤다. Task 5가 던지는 `InvalidSettlementPeriod`를 변환할 처리기가 Task 4에 있기 때문이다. Task 4 없이 먼저 끝내면 `2025-13` 요청이 500 스택트레이스로 나간다.

## 서브태스크

| ID | 명세 | 의존 | 예상 | 테스트 |
| --- | --- | --- | ---: | ---: |
| 5.1 | [역할 매트릭스](./task-5-1-role-matrix-spec.md) | — | 5분 | 0 |
| 5.2 | [크리에이터 월별 정산](./task-5-2-monthly-settlement-spec.md) | 5.1, Task 2·3·4 | 15분 | 2 |
| 5.3 | [운영자 기간 집계](./task-5-3-admin-aggregate-spec.md) | 5.1, 5.2 | 20분 | 3 |
| 5.4 | [`SettlementController`](./task-5-4-controller-spec.md) | 5.2, 5.3, 5.5 | 15분 | 0 |
| 5.5 | [응답 DTO](./task-5-5-response-dto-spec.md) | — | 10분 | 0 |
| 5.6 | [접근 경계 테스트 + opt-in 가드](./task-5-6-access-test-spec.md) | 5.4 | 20분 | 5 |

약 85분, 새 테스트 10건.

**표의 의존은 부모 Task 의존(`[2,3,4]`)에 더해지는 것이다.** 5.2는 Task 2의 조회 어댑터, Task 3의 계산기, Task 4의 `ActorAccessPolicy`와 도메인 빈을 전부 쓴다. 5.3은 5.2가 만든 `SettlementQuery`를 그대로 쓰므로 5.2 뒤에 온다.

### Spring Boot 4 임포트 경로

Boot 4가 테스트 자동설정을 모듈별로 쪼갰다. **Boot 3 임포트를 쓰면 컴파일이 안 된다.**

| 애노테이션 | 패키지 |
| --- | --- |
| `@SpringBootTest` | `org.springframework.boot.test.context` (그대로) |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.jdbc.test.autoconfigure` |
| `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure` |

`spring-boot-test-autoconfigure` jar에는 이 셋이 없다. Task 1이 넣은 `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`가 각각 가져온다.

## 핵심: 운영자 집계는 월별 합이 아니다

기간 전체를 **단일 정산 구간**으로 계산한다. 월별로 계산해 더하지 않는다.

creator-2의 2025-01-01~03-31이 시금석이다.

| 방식 | 계산 | 결과 |
| --- | --- | --- |
| **단일 구간 (채택)** | 총 120,000 − 환불 60,000 = 순 60,000, 수수료 12,000 | **48,000** |
| 월별 합산 | 48,000 + (−60,000) + 48,000 | 36,000 |

12,000원 차이는 월별 합산일 때 2월의 음수 순 판매액에 수수료 0원 제한이 걸려 1월에 이미 뗀 수수료가 상쇄되지 않기 때문이다. 전체 합계로 보면 단일 구간 264,000, 월별 합산 252,000이다.

**평가자가 두 API를 돌리면 이 불일치를 반드시 본다.** creator-2를 월별로 세 번 조회한 합과 운영자 조회 값이 다르다. Task 7 README 최우선 항목이다.

월별 합산 방식을 안 쓰는 이유는 크리에이터에게 불리하고 설명하기 어렵기 때문이다. 음수 월마다 수수료 0원 제한이 반복 적용된다. 단일 구간은 계산기를 기간만 바꿔 그대로 재사용할 수 있어 "순수 계산기" 설계와도 맞는다.

## API

| 메서드 | 경로 | 액터 |
| --- | --- | --- |
| GET | `/api/creators/{creatorId}/settlements/{yearMonth}` | 본인 CREATOR 또는 ADMIN |
| GET | `/api/admin/settlements?from=&to=` | ADMIN 전용 |

```
GET /api/creators/creator-1/settlements/2025-03
→ 200 { "creatorId": "creator-1", "yearMonth": "2025-03",
        "grossSales": 260000, "saleCount": 4,
        "refunds": 110000, "cancelCount": 2,
        "netSales": 150000, "fee": 30000, "payout": 120000 }

GET /api/admin/settlements?from=2025-03-01&to=2025-03-31
→ 200 { "from": "2025-03-01", "to": "2025-03-31",
        "creators": [ { "creatorId": "creator-1", ..., "payout": 120000 }, ... ],
        "totalPayout": 168000 }
```

## 책임 분담

| 책임 | 소유 |
| --- | --- |
| 어느 엔드포인트에 어떤 규칙이 붙는지 | **5.1** |
| 판정 코드 `ActorAccessPolicy` | Task 4.1 |
| 기간 구간에서 크리에이터 1명의 요약 산출 | Task 3.4 |
| 크리에이터 목록 조회, 반복 호출, 목록 조립, 전체 합계 | **5.3** |
| 예외 정의, 전역 처리기, 오류 포맷, `FeePolicy` 빈 | Task 4.1 |
| `SettlementCalculator` 빈 등록 | **Task 5** |

## 제외 범위

- 계산 규칙, 값 타입 — Task 3
- 예외 정의, 전역 처리기, `ActorAccessPolicy` 구현, `FeePolicy` 빈 — Task 4

**`SettlementCalculator` 빈은 Task 5가 등록한다.** Task 4.1의 `DomainConfig`에 메서드를 하나 더한다.

```java
@Bean
SettlementCalculator settlementCalculator(FeePolicy feePolicy) {
    return new SettlementCalculator(feePolicy);
}
```

Task 4는 정산 계산을 하지 않으므로 그 빈이 필요 없다. 쓰는 태스크가 등록한다. 도메인에 `@Component`를 붙이지 않는 이유는 그러면 도메인이 Spring에 묶여 계산기 단위 테스트 42건이 컨텍스트 없이 못 돌기 때문이다.
- 판매 목록 조회와 그 인가 테스트 — Task 4 (엔드포인트를 소유한 곳이 단언한다)
- 누적 초과 환불 — Task 4
- HTTP 통합 흐름 1건 — Task 6

## 파일

| 경로 | 서브태스크 |
| --- | --- |
| `adapter/in/web/SettlementController.java` | 5.4 |
| `adapter/in/web/dto/MonthlySettlementResponse.java`, `AdminSettlementResponse.java`, `CreatorPayoutItem.java` | 5.5 |
| `application/settlement/SettlementQuery.java` | 5.2 |
| `application/settlement/MonthlySettlementUseCase.java` | 5.2 |
| `application/settlement/AdminSettlementUseCase.java` | 5.3 |

테스트는 `src/test/java/.../adapter/in/web/SettlementControllerTest.java`(5.2·5.3·5.6)와 `.../ControllerActorGuardTest.java`(5.6).

## 완료 기준

1. `./gradlew test`가 통과한다.
2. 두 엔드포인트가 동작하고 응답에 7개 금액·건수 필드가 있다.
3. 운영자 2025-03 전체 합계가 168,000이다.
4. 운영자 2025-01~03 전체 합계가 **264,000**이다. 월별 합산 구현이면 252,000이 나와 실패한다.
5. 실적 없는 creator-3이 운영자 목록에 0원으로 포함된다.
6. 빈 월이 404가 아니라 200 + 전 항목 0이다.
7. 접근 경계 4종이 고정된다.
8. opt-in 가드가 Task 4·5의 모든 컨트롤러 메서드를 통과시킨다.
9. Task 1~4의 기존 테스트가 계속 통과한다.

## 롤백 · 소요

신규 파일만 추가한다. 커밋을 되돌리면 된다. 약 85분.

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
2. 판매 단건 조회가 `SaleData`를 돌려주면 `creatorId`를 채울 방법이 없어 NPE. 단건 경로는 `Sale` 애그리게이트로, 목록 경로는 전용 `SaleRecord`로 갈랐다.
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
