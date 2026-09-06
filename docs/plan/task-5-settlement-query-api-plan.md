# Task 5 — 정산 조회 API: 검토 계획

## 목표

크리에이터 월별 정산과 운영자 기간 집계를 HTTP로 연다. 접근 경계 정책도 여기서 소유한다.

## 선행 조건

`tasks.json`의 의존성을 `[2,3]`에서 `[2,3,4]`로 고쳤다. Task 5가 던지는 `InvalidSettlementPeriodException`를 변환할 전역 예외 처리기가 Task 4에 있기 때문이다. Task 4 없이 Task 5를 먼저 끝내면 `2025-13` 요청이 500 스택트레이스로 나간다.

## 포함 범위

- 역할 매트릭스 — 어느 엔드포인트에 어떤 규칙이 붙는지
- 크리에이터 월별 정산 유스케이스
- 운영자 기간 집계 유스케이스
- `SettlementController`, 응답 DTO
- 접근 경계 테스트와 opt-in 가드 테스트

## 제외 범위

- 계산 규칙 — Task 3
- 예외 정의, 전역 처리기, `ActorAccessPolicy` 구현, 도메인 빈 등록 — Task 4
- 계산기 단언 — Task 3

## 확정된 전제

1. **운영자 기간 집계는 기간 전체를 단일 구간으로 계산한다.** 월별로 계산해 합산하지 않는다. Task 3 전제 8.

   creator-2의 2025-01-01~03-31이 이 판단의 시금석이다.

   | 방식 | 결과 |
   |---|---|
   | 단일 구간 (채택) | 48,000 |
   | 월별 합산 | 48,000 + (-60,000) + 48,000 = 36,000 |

   12,000원 차이는 월별 합산일 때 2월 음수 순 판매액에 수수료 0원 제한이 걸려 1월에 이미 뗀 수수료가 상쇄되지 않기 때문이다. **평가자가 두 API를 돌리면 이 불일치를 반드시 본다. 문서가 없으면 버그로 읽힌다.** Task 7 README 최우선 항목이다.

2. **운영자 집계는 `findAllCreatorIds()`로 목록을 받아 크리에이터마다 계산기를 한 번씩 돌린다.** 판매·취소 자료만으로는 실적 0인 creator-3의 존재를 알 수 없다. 크리에이터가 3명이라 N+1이 문제되지 않는다.

3. **빈 월은 전 항목 0원 정상 응답이다.** 404가 아니다. creator-3의 2025-03이 이 경로다.

4. **역할 매트릭스.**

   | 엔드포인트 | ADMIN | CREATOR |
   |---|---|---|
   | `GET /api/creators/{creatorId}/settlements/{yearMonth}` | 전부 허용 | `X-Actor-Id == creatorId`일 때만 |
   | `GET /api/admin/settlements?from=&to=` | 허용 | 거부 |
   | `GET /api/creators/{creatorId}/sales` (Task 4) | 전부 허용 | `X-Actor-Id == creatorId`일 때만 |

   위반은 `ActorAccessDeniedException` → 403.

   **이 표(어느 엔드포인트에 어떤 규칙이 붙는가)는 Task 5가 소유한다. 판정 코드 `ActorAccessPolicy`는 Task 4가 소유한다.** Task 5는 `requireSelfOrAdmin` / `requireAdmin`을 호출만 한다. 정책 구현을 Task 5에 두면 Task 4가 Task 5를 호출하는데 Task 5는 Task 4의 전역 처리기를 기다리는 순환이 된다.

5. **연월과 일자를 `String`으로 수신한다.** `@PathVariable YearMonth`로 바인딩하면 Spring이 `MethodArgumentTypeMismatchException`을 먼저 던져 `InvalidSettlementPeriodException`가 걸리지 않는다. `2025-13`의 거부는 도메인 규칙이므로 도메인이 판정하게 둔다.

6. **도메인 빈은 Task 4가 이미 등록해 두었다.** `FeePolicy`, `FixedRateFeePolicy(2000bp)`, `SettlementCalculator`를 Task 5에서 다시 등록하지 않는다. 등록은 `config`에서 하되 그 시점이 Task 4다. Task 4가 Spring 배선이 생기는 첫 Task이기 때문이다.

7. **응답에 판매 건수와 취소 건수를 모두 담는다.** `tasks.json`의 "건수"를 "판매 건수와 취소 건수"로 고쳤다. 원본 과제가 둘을 요구한다.

8. **접근 판정 호출은 유스케이스에서 한다.** 컨트롤러는 `ActorContext`를 넘기기만 한다.

## 응답 필드

```
총 판매액 grossAmount        판매 건수 saleCount
환불액   refundAmount        취소 건수 cancelCount
순 판매액 netAmount
수수료   feeAmount
정산 예정액 payoutAmount      (음수 가능 — 환불 부담액)
```

운영자 응답은 위 요약을 크리에이터별 목록으로 담고 전체 정산 예정액 합계를 더한다.

## 서브태스크

| # | 제목 | 핵심 |
|---|---|---|
| 5.1 | 역할 매트릭스 | 엔드포인트별 규칙 확정. 판정 코드는 Task 4의 `ActorAccessPolicy`를 호출 |
| 5.2 | 크리에이터 월별 정산 유스케이스 | 포트 조회 → 계산기 위임. 빈 월은 0원 정상 응답 |
| 5.3 | 운영자 기간 집계 유스케이스 | 단일 구간. `findAllCreatorIds()`로 실적 0 포함, 전체 합계 |
| 5.4 | `SettlementController` | 연월·일자를 `String`으로 수신 |
| 5.5 | 응답 DTO | 7개 필드 명시 |
| 5.6 | 접근 경계 테스트 + opt-in 가드 | 아래 |

## 테스트

| 케이스 | 기대 |
|---|---|
| CREATOR가 타인 정산 조회 | 403 |
| CREATOR가 운영자 API 호출 | 403 |
| CREATOR가 본인 정산 조회 | 200 |
| ADMIN이 타인 정산 조회 | 200 |
| 빈 월 (creator-3, 2025-03) | 200, 전 항목 0 |
| 운영자 2025-03 | 목록 3건, 전체 합계 168,000 |
| 운영자 2025-01~03 | 목록 3건, 전체 합계 264,000 (36,000 아님) |
| opt-in 가드 | 모든 컨트롤러 메서드가 `ActorContext` 파라미터를 선언한다 |

**opt-in 가드는 리플렉션 테스트다.** `@RestController` 빈을 전부 모아 `@RequestMapping` 계열 메서드의 파라미터에 `ActorContext`가 있는지 검사한다. 해석기가 필터가 아니라서, 파라미터 선언을 깜빡한 엔드포인트는 헤더 검사 없이 조용히 열린다. 컴파일러가 잡아주지 않는 유일한 구조적 위험이라 테스트로 막는다. Task 4의 컨트롤러도 이 가드가 함께 검사한다.

목록 조립과 전체 합계(168,000 / 264,000)는 Task 5가 단언한다. 크리에이터별 개별 값은 Task 3이 이미 단언했다.

## 완료 기준

- 두 엔드포인트가 동작하고 응답에 7개 필드가 있다.
- 운영자 1~3월 집계가 264,000이다. 월별 합산 구현이면 252,000이 나와 실패한다.
- 접근 경계 4종이 테스트로 고정된다.
- opt-in 가드가 Task 4·5의 모든 컨트롤러 메서드를 통과시킨다.
- `./gradlew test` 통과.

## 위험

- **월별 합산으로 잘못 구현해도 3월 조회는 정답이 나온다.** 3월에는 음수 월이 없기 때문이다. 1~3월 케이스가 없으면 이 버그가 안 잡힌다.
- **접근 경계가 코드와 문서로 갈라질 수 있다.** 판정 코드는 Task 4의 `ActorAccessPolicy` 하나, 배치 규칙은 이 문서의 역할 매트릭스 하나다. 둘 다 단일 소유자를 갖는다.

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
| 1 | CEO §1 | **순환 의존** — 정책 소유권이 Task 4와 충돌 | 판정 코드는 Task 4, 배치 규칙(역할 매트릭스)은 Task 5. 소유권을 쪼갬 |
| 2 | CEO §1 | 5.6 도메인 빈 등록이 Task 4보다 늦음 | 5.6 삭제. Task 4가 등록 |
| 3 | CEO §6 | 운영자 전체 합계를 Task 5·6이 둘 다 소유 | Task 5가 단언. 뿌리는 Task 3 계획서의 자기모순이었고 완료 기준 쪽을 택함 |
| 4 | OV | 역할 매트릭스에 판매 목록이 있으나 테스트는 정산만 | 엔드포인트를 소유한 Task 4가 단언하도록 이관 |

서브태스크가 7개에서 6개로 줄었다. 잃은 건 없고 Task 4로 옮겨졌다.

**VERDICT:** CEO + OUTSIDE VOICE CLEARED — 구현 착수 가능. eng review는 미실행.

NO UNRESOLVED DECISIONS
