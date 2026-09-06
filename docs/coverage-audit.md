# 커버리지 감사

테스트가 여섯 Task에 흩어져 있다. "규칙을 소유한 Task가 그 규칙의 테스트도 소유한다"는 원칙에는 약점이 하나 있다 — **한 Task가 약속한 테스트를 안 써도 `./gradlew test`는 초록색이다.** 이 표가 그 틈을 맡는다.

자동화하지 않았다. 리플렉션이나 정적 분석으로 "이 시나리오가 검증됐는가"를 판정하는 도구를 만들 수 있지만 3시간 예산에서 과하고, 그 도구 자체가 검증되지 않는다. 표를 보며 손으로 대조하고 결과를 남긴다.

측정 시점: **108건 통과 / 실패 0**.

## 시나리오 대조

| # | 시나리오 | 소유 | 테스트 파일 | 메서드 |
| --- | --- | --- | --- | --- |
| 1 | creator-1 2025-03 → 120,000 | Task 3 | `SettlementCalculatorTest` | `creator1March` |
| 2 | 부분 환불 (sale-4, 30,000) | Task 3 | `SettlementCalculatorTest` | `creator1March`, `multiplePartialCancels` |
| 3 | 월 경계 취소 (creator-2 2025-02, 음수·수수료 0) | Task 3 | `SettlementCalculatorTest` | `creator2February` |
| 4 | 빈 월 (creator-3 2025-03) | Task 3 | `SettlementCalculatorTest` | `creator3MarchIsEmpty` |
| 5 | 환불 상태 4행 | Task 3 | `RefundStatusTest` | `none`, `partial`, `full`, `overRefundClosesToFull` |
| 6 | 반열린 구간 경계 3방향 | Task 3 | `SettlementPeriodTest` | `startIncluded`, `endOfLastDayIncluded`, `nextMonthStartExcluded` |
| 7 | 잘못된 연월 (`2025-13`, `2025/03`, 빈 값, 역전) | Task 3 | `SettlementPeriodTest` | `malformed`, `blank`, `reversedRange`, `monthOutOfRange` |
| 8 | 수수료 버림 (33,333 → 6,666) | Task 3 | `FixedRateFeePolicyTest` | `truncates` |
| 9 | 누적 초과 환불 (30,000 + 60,000 > 80,000) | Task 4 | `SaleTest`, `SaleControllerTest` | `rejectsAccumulatedExcess`, `accumulatedRefundExceeded` |
| 10 | 없는 판매 404 / 없는 강의 404 | Task 4 | `SaleControllerTest` | `saleNotFound`, `courseNotFound` |
| 11 | 헤더 오류 400 vs 인가 실패 403 | Task 4 | `SaleControllerTest` | `missingActorHeader`, `creatorCannotRegister` |
| 12 | 오프셋 없는 시각 400 | Task 4 | `SaleControllerTest` | `missingOffset` |
| 12-b | 우리 처리기의 오류가 RFC 9457 6필드 | Task 4 | `SaleControllerTest` | `rfc9457Shape` |
| 12-c | 프레임워크 실패(405)도 problem+json | Task 4 | `SaleControllerTest` | `frameworkFailureIsProblemJson` |
| 12-d | 오버플로 취소 거부 (`Long.MAX_VALUE`) | Task 4 | `SaleTest` | `rejectsOverflowingAmount` |
| 12-e | 응답 시각이 `+09:00` 문자열 | Task 4 | `SaleControllerTest` | `serializesDateAsIsoString` |
| 12-f | 결제 이전 취소 409 `CANCEL_BEFORE_PAYMENT` | Task 4 | `SaleControllerTest`, `SaleTest` | `cancelBeforePaymentRejected` |
| 12-g | 금액 상한 초과 400 (수수료·합계 오버플로 차단) | Task 4 | `SaleControllerTest` | `amountAboveCapRejected` |
| 12-h | 지원 범위 밖 종료일 400 (500 아님) | Task 4 | `SaleControllerTest` | `endDateOutOfRangeIsBadRequest` |
| 12-i | 취소도 ADMIN 전용 403 | Task 4 | `SaleControllerTest` | `creatorCannotCancel` |
| 12-j | 인가 판정 네 갈래 (허용 2 + 거부 2) | Task 4 | `ActorAccessPolicyTest` | 4건 전부 |
| 13 | `sale-5` 1월 조회 → `FULL` | Task 4 | `SaleControllerTest` | `refundStatusIgnoresPeriod` |
| 13-b | ADMIN이 타인 판매 목록 조회 200 | Task 4 | `SaleControllerTest` | `adminListsOtherCreator` |
| 14 | 운영자 2025-03 → 168,000 | Task 5 | `SettlementControllerTest` | `march` |
| 15 | 운영자 2025-01~03 → **264,000** | Task 5 | `SettlementControllerTest` | `quarterIsNotSumOfMonths` |
| 16 | 실적 0 크리에이터 목록 포함 | Task 5 | `SettlementControllerTest` | `zeroCreatorIncluded` |
| 17 | 타인 정산 403 / 운영자 API 403 | Task 5 | `SettlementControllerTest` | `otherCreatorSettlement`, `creatorCannotUseAdminApi` |
| 18 | opt-in 가드 | Task 5 | `ControllerActorGuardTest` | `everyHandlerDeclaresActorContext` |
| 18-b | `to`가 종료일을 포함한다 (하루 구간) | Task 5 | `SettlementControllerTest` | `endDateIsInclusive` |
| 18-c | 잘못된 연월 400 `INVALID_SETTLEMENT_PERIOD` | Task 5 | `SettlementControllerTest` | `invalidYearMonth` |
| 19 | 시드 17행 재현, `sale-5` KST 1월 귀속 | Task 2 | `SeedDataTest` | `rowCounts`, `saleFiveBelongsToJanuaryInKst` |
| 20 | 빈 `IN` 절 방어 | Task 2 | `SalesQueryJpaAdapterTest` | `findCancelsBySaleIdsEmpty` |
| 21 | HTTP 통합 흐름 | Task 6 | `SettlementE2ETest` | `registerCancelThenSettle` |
| 22 | `now()` 미사용 | Task 6 | `NoCurrentTimeUsageTest` | `noCurrentTimeCalls` |
| 23 | 날짜 상한 오버플로가 400 (QA-001) | Task 3 | `SettlementPeriodTest` | `yearMonthUpperBoundDoesNotOverflow`, `endDateUpperBoundDoesNotOverflow` |
| 24 | 소수 금액이 조용히 잘리지 않음 (QA-002) | Task 4 | `SaleControllerTest` | `fractionalAmountRejected` |

**빈 행 없음.** 34행 전부 실제 테스트가 대응된다.

**15번이 이 표에서 가장 중요하다.** 월별 합산으로 잘못 구현해도 14번(2025-03)은 정답이 나온다 — 3월에는 음수 월이 없기 때문이다. 15번이 없으면 그 버그가 통과한다.

## 두 가드는 자기가 공허하지 않은지 스스로 검사한다

18번과 22번은 "대상이 0개여도 통과"할 수 있는 종류다. opt-in 가드는 패키지 필터가 어긋나면, `now()` 스캐너는 작업 디렉터리가 어긋나면 아무것도 검사하지 않고 초록불이 된다.

각각 짝 테스트를 뒀다. `guardIsNotVacuous`가 핸들러 5개 이상을, `scannerReadsSources`가 소스 파일 20개 이상을 실제로 보고 있는지 확인한다.

## 테스트 수 대조

| Task | 파일 | 건수 |
| --- | --- | ---: |
| Task 1 | `SettlementApplicationContextTest`, `ActorContextArgumentResolverTest` | 4 |
| Task 2 | `SalesQueryJpaAdapterTest`, `SeedDataTest` | 11 |
| Task 3 | `SettlementCalculatorTest`, `SettlementPeriodTest`, `RefundStatusTest`, `FixedRateFeePolicyTest`, `SettlementSummaryTest` | 44 |
| Task 4 | `SaleControllerTest`, `SaleTest`, `ActorAccessPolicyTest` | 34 |
| Task 5 | `SettlementControllerTest`, `ControllerActorGuardTest` | 12 |
| Task 6 | `SettlementE2ETest`, `NoCurrentTimeUsageTest` | 3 |
| **합계** | | **108** |

Task 3이 42건으로 가장 크다. 계산 규칙 전부를 Spring도 H2도 없이 잠근다 — 그래서 나머지 Task가 같은 숫자를 다시 단언하지 않아도 된다.

## 시드-픽스처 대응

`sale-5`의 값이 두 곳에 있다. 형식이 달라 한쪽에서 다른 쪽을 생성할 수 없다.

| 어디 | 값 |
| --- | --- |
| `src/main/resources/data.sql` | `('sale-5', 'course-3', 60000, '2025-01-31T14:30:00Z')` |
| `SettlementFixtures.java` | `sale("sale-5", CREATOR_2, 60_000, "2025-01-31T23:30:00+09:00")` |

`2025-01-31T14:30:00Z` = `2025-01-31T23:30:00+09:00`. **같은 순간이다.** 금액도 60,000으로 같다. `cancel-3`(60,000, 2025-02-03 10:00 KST)도 양쪽이 일치한다.

**시드를 바꾸면 두 곳을 다 고쳐야 한다.** SQL 리터럴은 UTC, Java 상수는 KST 표기다.

## 세 상수 집합이 겹치지 않는다

| 무엇 | 어디 | 쓰는 달 |
| --- | --- | --- |
| 시드 17행 | `data.sql` | 2025-01 ~ 03 |
| 계산기 픽스처 | `SettlementFixtures.java` (package-private) | 2025-01 ~ 03 + 시드에 없는 추가 케이스 |
| E2E·API 입력 | `SettlementE2ETest`, `SaleControllerTest` 안 | **2025-06** |

Task 3의 추가 케이스(수수료 버림 33,333원, 동일 판매 다수 부분 취소)는 픽스처에만 있다. `data.sql`에 넣으면 creator-1의 2025-03 기대값 120,000원이 깨지는데, 그 값은 원본 과제가 명시한 숫자라 바꿀 수 없다. 실제로 `data.sql`에 33,333이 없음을 확인했다.

`SettlementFixtures`는 package-private이라 Task 6이 import할 수 없다. **필요도 없다** — E2E는 자기 숫자(100,000 / 40,000)를 2025-06에 넣는다.

## 결정성 점검 결과

| 검사 | 방법 | 결과 |
| --- | --- | --- |
| `now()` 호출 | `NoCurrentTimeUsageTest` (자동) | 0건 |
| 두 번 연속 실행 | `./gradlew cleanTest test` × 2 | 108건 / 108건, 동일 |
| 실행 순서 의존 | `src/test`의 `@TestMethodOrder`·`@Order` | 0건 |
| 외부 서비스 | HTTP 클라이언트·브로커·외부 API | 없음. H2 인메모리뿐 |

`src/main`의 `GlobalExceptionHandler`에 `@Order`가 있지만 그건 어드바이스 우선순위이지 테스트 순서가 아니다. 트리 전체를 grep하면 오탐이 난다.

## 이 표가 검사하지 않는 것

테스트가 **존재하는지**만 본다. 그 테스트가 **옳은지**는 못 본다. 잘못된 기대값을 박아둔 테스트도 이 표에서는 초록색이다.

그 층은 실행 검증이 맡았다 — Task 5에서 애플리케이션을 띄우고 curl로 두 엔드포인트, 오류 4종, 그리고 월별 3회 합(36,000)과 기간 집계(48,000)의 불일치까지 재현했다.

---

# 제출 전 클린 점검 (Task 7.8)

로컬에서 되는 것과 평가자 환경에서 되는 것은 다르다. 아래는 **실제로 돌린 결과**다.

| # | 항목 | 결과 |
| ---: | --- | --- |
| 1 | 클린 클론에서 `./gradlew clean test` | **108건 / 실패 0** |
| 2 | `gradlew` 100755, wrapper jar 추적 | `100755 gradlew`, `100644 gradle-wrapper.jar` |
| 3 | `bootRun` 기동, 예외 없음, 시드 적재 | `Started SettlementApplication in 2.43 seconds`. ERROR·Exception 0건 |
| 4 | README curl 전부 실행, 응답 일치 | 9개 예시 전부 일치. 실패 0 |
| 5 | `git status` 비어 있음, 산출물 미추적 | `build`·`.gradle`·`.claude` 추적 0건 |
| 6 | README 수치 = 테스트 기대값 | 아래 대조표 |
| 7 | 오류 포맷 | 우리 예외는 6필드 + `code`, 프레임워크 실패는 `code` 없이 problem+json |
| 8 | 커버리지 감사 빈 행 없음 | 34행 전부 채움 |

## 1. 클린 클론

작업 디렉터리가 아니라 새로 클론한 곳에서 돌렸다. 커밋 안 된 파일에 의존하면 여기서 드러난다.

```
git clone <repo> && ./gradlew clean test
→ BUILD SUCCESSFUL, 108건 / 실패 0
```

그 클론에서 `bootRun`도 띄워 README의 curl 아홉 개를 다시 대조했다. 전부 일치.

## 4. curl 대조 결과

| 예시 | 결과 |
| --- | --- |
| 월별 정산 200 (7필드) | OK |
| 운영자 집계 총액 168,000 + creator-3 0원 | OK |
| 판매 목록 `sale-5` → `FULL` | OK |
| 판매 등록 201 | OK (`saleId`는 매 실행 다른 UUID) |
| 타인 정산 403 | OK (6필드 전부) |
| 405 프레임워크 실패 | OK (`code`·`type` **없음**이 정상) |
| 초과 환불 409 + `detail` 형식 | OK |

## 6. 수치 대조

| 값 | 나오는 곳 | 일치 |
| --- | --- | --- |
| creator-1 2025-03 → 120,000 | README 데이터 모델 표, curl 응답, `creator1March` | OK |
| 운영자 2025-03 → 168,000 | README curl 응답, `march` | OK |
| 운영자 2025-01~03 → **264,000** | README 가정 1, `quarterIsNotSumOfMonths` | OK |
| 월별 합산 → 252,000 | README 가정 1 (대조군) | OK |
| creator-2 2025-02 → −60,000 | README 기대 정산표, `creator2February` | OK |
| 시드 17행 | README 초기 데이터 표, `data.sql`, `rowCounts` | OK |
| 테스트 108건 | README 실행 절, 감사표 합계 | OK |

## 병합 후 재검증

`/review`에서 나온 결함 셋을 고친 뒤 위 점검을 다시 돌렸다. 오류 계약이 바뀌었으므로
숫자만 고치지 않고 실제로 실행했다.

| 항목 | 결과 |
| --- | --- |
| 클린 클론 `./gradlew clean test` | 108건 / 실패 0 |
| `bootRun` 기동 | `Started SettlementApplication in 2.364 seconds`, ERROR·Exception 0건 |
| 월별 정산 creator-1 2025-03 | `payout` 120,000 |
| 운영자 2025-03 / 2025-01~03 | `totalPayout` 168,000 / **264,000** |
| 판매 목록 `sale-5` 1월 | `refundStatus` `FULL` |
| 타인 정산 403 | 6필드 + `code` |
| 405 프레임워크 실패 | `code`·`type` 없이 problem+json |
| 금액 상한 초과 | 400 `VALIDATION_FAILED` |
| 결제 이전 취소 | 409 `CANCEL_BEFORE_PAYMENT` |
| 지원 범위 밖 종료일 | 400 `INVALID_SETTLEMENT_PERIOD` (500 아님) |

포트 8080이 점유돼 있어 `SERVER_PORT=18080`으로 띄웠다. 나머지는 동일하다.

## 결함이 나오면

**여기서 코드를 고치지 않는다.** 해당 Task로 되돌린다. 제출 직전에 급하게 고친 코드가 테스트를 안 거치고 들어가는 것이 가장 위험하다.

이번 점검에서는 여덟 항목 전부 통과해 되돌린 것이 없다.
