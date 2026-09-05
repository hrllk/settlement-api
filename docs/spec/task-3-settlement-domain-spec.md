# Task 3 — 정산 도메인 계산 구현 명세

## 배경

정산 계산 규칙을 Spring과 DB 없이 도는 순수 도메인 코드로 구현한다. 판매·취소 원본 자료에서 정산 요약과 판매별 환불 상태를 산출하는 것까지가 범위다. API와 영속성은 포함하지 않는다.

이 태스크에 설계 판단이 몰려 있다. 이중 집계 기준, KST 경계, 음수 순 판매액 정책이 틀리면 Task 5와 6이 다시 짜인다.

## 현재 상태

- 계획: `docs/plan/task-3-settlement-domain-plan.md`
- Task 1 완료: Java 21, Spring Boot 4.1.1, Gradle Wrapper 9.7.1, 루트 패키지 `com.liveclass.settlement`, 헥사고날 5계층
- 구현 완료. `domain/settlement` 9개 타입과 `application/port/out` 포트 1개
- `ActorRole`(`ADMIN` / `CREATOR`)은 Task 1, `ActorAccessDenied`(403)는 Task 4에서 정의된다

## 서브태스크

| ID | 명세 | 의존 | 예상 | 테스트 |
| --- | --- | --- | ---: | ---: |
| 3.1 | [`SettlementPeriod`](./task-3-1-settlement-period-spec.md) | — | 20분 | 15 |
| 3.2 | [값 타입과 환불 상태](./task-3-2-value-types-spec.md) | 3.3 | 20분 | 9 |
| 3.3 | [`FeePolicy`](./task-3-3-fee-policy-spec.md) | — | 10분 | 6 |
| 3.4 | [`SettlementCalculator`](./task-3-4-settlement-calculator-spec.md) | 3.1, 3.2, 3.3 | 15분 | 12 |
| 3.5 | [`SettlementDataPort`](./task-3-5-settlement-data-port-spec.md) | 3.2 | 5분 | 0 |

3.1과 3.3은 서로 독립이다. `SettlementSummary.of`가 `FeePolicy`를 인자로 받으므로 3.2는 3.3 뒤에 온다. 3.4가 셋을 모은다. 각 문서는 단독으로 읽고 구현할 수 있다. 이 문서는 여러 서브태스크에 걸치는 것만 담는다.

## 테스트 환경

JUnit 5와 AssertJ를 쓴다. Task 1의 `spring-boot-starter-*-test`가 이미 제공하므로 의존성을 추가하지 않는다. 42건 전부 단위 테스트이며 `@SpringBootTest`를 쓰지 않는다. `Instant`는 `OffsetDateTime.parse("...+09:00").toInstant()`로 만들고 UTC로 손 변환하지 않는다.

## 예외 타입 구분

| 위반 | 예외 | 정의 | HTTP |
| --- | --- | --- | --- |
| `SettlementPeriod` 불변식·파싱 | `InvalidSettlementPeriod` | **3.1** | 400 |
| `SettlementSummary` 파생값 불일치 | `IllegalArgumentException` | 3.2 | 500 |
| `SaleData` / `CancelData` null 필드 | `NullPointerException` | 3.2 | 500 |
| `FixedRateFeePolicy` bp 범위 | `IllegalArgumentException` | 3.3 | 500 |
| 액터 헤더 누락·형식 오류 | `ResponseStatusException` | Task 1 | 400 |
| 인가 실패 | `ActorAccessDenied` | Task 4 | 403 |

**Task 4의 전역 예외 처리기는 `IllegalArgumentException`과 `NullPointerException`을 400으로 매핑하면 안 된다.** 사용자가 유발할 수 있는 것은 400·403 세 개뿐이다. 나머지를 400으로 싸잡으면 프로그래밍 버그가 사용자 오류로 위장돼 로그에서 사라진다.

## 책임 분담

| 책임 | 소유 |
| --- | --- |
| 기간 구간에서 크리에이터 1명의 정산 요약 산출 | **3.4** |
| 크리에이터 목록 조회, 반복 호출, 목록 조립, 전체 합계 | **Task 5** |
| 판매별 환불 상태 산출 | **3.2** |
| 판매 목록 응답에 환불 상태 결합 | **Task 4** |
| 접근 경계 (CREATOR 본인 확인, ADMIN 전용) | **Task 5** (예외 정의는 Task 4) |

## 제외 범위

- JPA 엔티티, 리포지토리, 포트 구현체 — Task 2
- 컨트롤러, DTO, 전역 예외 처리기 — Task 4·5
- 초과 환불 거부의 누적 판정, 금액 부호 검증 — Task 4
- Spring 빈 등록과 수수료율 설정 바인딩 — Task 4 (Spring 배선이 생기는 첫 Task)
- 크리에이터별 그룹핑과 전체 합계, 접근 경계 배치, 로깅 — Task 5
- 수수료율 이력과 시점별 적용, 정산 상태 전이, CSV — 과제 명시적 제외
- 수수료율 설정 바인딩 — Task 4. 값은 `application.yml`에 두고 도메인은 주입만 받는다

## 파일

`src/main/java/com/liveclass/settlement/` 아래.

| 경로 | 서브태스크 |
| --- | --- |
| `domain/settlement/SettlementPeriod.java`, `InvalidSettlementPeriod.java` | 3.1 |
| `domain/settlement/SaleData.java`, `CancelData.java`, `SettlementSummary.java`, `RefundStatus.java` | 3.2 |
| `domain/settlement/FeePolicy.java`, `FixedRateFeePolicy.java` | 3.3 |
| `domain/settlement/SettlementCalculator.java` | 3.4 |
| `application/port/out/SettlementDataPort.java` | 3.5 |

테스트는 `src/test/java/.../domain/settlement/`에 `SettlementPeriodTest`(3.1), `SettlementSummaryTest`·`RefundStatusTest`(3.2), `FixedRateFeePolicyTest`(3.3), `SettlementCalculatorTest`(3.4).

`domain/.gitkeep`은 3.1과 3.2 중 먼저 끝나는 쪽이, `application/.gitkeep`은 3.5가 지운다.

## 완료 기준

1. `./gradlew test`가 통과한다.
2. 새 테스트 42건이 Spring 컨텍스트와 H2 없이 돈다. `./gradlew test` 전체는 Task 1의 4건을 더해 46건이다.
3. 월별 6행, 크리에이터별 기간 6값, 환불 상태 4행을 명시적으로 단언한다.
4. 반열린 구간 경계 3방향이 있다 (3.1).
5. `SettlementPeriod`와 `SettlementSummary`의 compact 생성자 불변식이 표준 생성자 직접 호출로 검증된다.
6. 환불 상태의 기간 무관 단언이 있다 (3.2).
7. `domain`과 `application.port.out`에 Spring 애노테이션과 로깅이 없다.
8. Task 1의 기존 테스트 2종이 계속 통과한다.

## 롤백 · 소요

신규 파일만 추가하므로 커밋을 되돌리면 된다. 약 70분.

## 엔지니어링 검토 결과

### 고친 것

**1회차 — 설계 결함 4건.** record 표준 생성자가 public이라 팩토리 검증이 통째로 우회된다는 것을 놓쳤다. `SettlementPeriod`·`SettlementSummary`에 compact 생성자를 넣었다. `RefundStatus.of`가 미리 더한 합계만 받으면 "그 판매의 모든 취소를 본다"는 규칙이 도메인 밖으로 밀려나 테스트로 잠글 수 없다. `SaleData` + 취소 목록 오버로드를 추가했고, 그 결과 "기간 밖 취소 → `FULL`" 테스트가 비로소 작성 가능해졌다.

**2회차 — 1회차가 만든 문제 3건.** compact 생성자가 역전을 잡으면서 메시지가 `Instant`로 바뀌어 원본 입력이 안 보이게 됐다(팩토리 선검증으로 해결). 예외 타입이 갈렸는데 근거가 없어 Task 4가 둘 다 400으로 매핑할 위험이 있었다(표로 구분). 값 타입 불변식이 비일관했다.

**3회차 — 사소한 것 3건.** `requireNonNull` 필드명, 테스트 케이스 인자 명시, `amount` 부호 검증 소관.

**4회차 — 분할 후 재검토 6건.** `YearMonth.parse(null)`이 `DateTimeParseException`이 아니라 NPE를 던져 "null → `InvalidSettlementPeriod`"가 구현 불가였다(`requireText` 가드 추가). 분할하면서 3.4에서 입력 픽스처가 빠지고, `Instant` 변환을 손으로 시키고, JUnit·AssertJ 명시가 사라지고, `.gitkeep` 삭제 소유자가 없어졌다. 전부 채웠다.

**rebase 반영.** main이 `ActorAccessDenied`(403)를 Task 4에, 접근 경계를 Task 5에 넣었다. 계획 단계에서 후속 항목으로 남긴 역할 매트릭스가 상류에서 해결됐다. "사용자가 만들 수 있는 오류는 하나뿐"이라던 예외 표를 다시 썼다.

### 결론

- 아키텍처: `domain.settlement` 9개 타입, 포트 1개. Spring 의존이 없어 Task 1·2와 병렬 가능하다.
- 코드 품질: 값 타입 4개 전부 불변식을 갖는다. 파생값은 팩토리로만 계산한다.
- 테스트: 42건 단위 테스트. 경계 3방향, 생성자 우회, 환불 상태 기간 무관성, 요율 교체 가능성이 각각 잠긴다.
- 성능: 메모리 내 7건. 운영자 집계가 크리에이터 수만큼 포트를 부르지만 3명이라 무의미하다.
- 복잡도 임계(8파일/2클래스)를 넘지만 CEO 검토에서 HOLD SCOPE로 확정됐다. 외부 검토의 축소안은 그에 따라 반려됐다.

## 후속 태스크

- **Task 2**: 시간 컬럼을 `Instant`로 매핑. 인덱스는 현행 유지. 포트 구현 요구는 3.5 명세 참조.
- **Task 4**: 예외 표 매핑을 지킨다.
- **Task 4**: `settlement.fee.basis-points`를 `@ConfigurationProperties`로 바인딩해 `new FixedRateFeePolicy(bp)`를 조립하고 `SettlementCalculator`를 빈으로 등록한다. 도메인은 요율 값을 갖지 않는다. `SettlementDataPort`는 조회 전용이므로 저장과 ID 조회용 `SaleCommandPort`와 그 어댑터는 Task 4가 따로 만든다.
- **Task 7 README**: 운영자 기간 집계가 월별 합이 아니라는 점을 최우선으로 적는다. 두 API를 돌리면 creator-2가 48,000원과 36,000원으로 갈리는데 문서가 없으면 버그로 읽힌다. 그 외 반열린 구간 근거, 초과 환불 거부 추가, 취소 데이터 정의 근거, 수수료·정산액 비대칭, 순수 계산기의 메모리 한계, 인덱스 순서 관찰, 식별자 `String` 선택, 기간 상한 없음, 액터 헤더 한계, 추가 테스트 케이스와 이유, 정산 상태 전이 확장 경로.

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
| --- | --- | --- | ---: | --- | --- |
| CEO Review | `/plan-ceo-review` | 범위와 전략 | 1 | CLEAR | HOLD_SCOPE, 결함 10건 반영 |
| Outside Voice | 독립 에이전트 | 독립 2차 의견 | 1 | CLEAR | 확인 10 / 반박 3 / 자기수정 1 |
| Eng Review | `/plan-eng-review` | 아키텍처와 테스트 | 4 | CLEAR | 16건 전부 반영 (분할 후 6건 포함) |
| Design Review | 해당 없음 | UI/UX | 0 | SKIPPED | 백엔드 전용 |
| DX Review | 해당 없음 | 로컬 실행 | 0 | SKIPPED | Task 1에서 완료 |

**VERDICT:** CEO + ENG CLEARED — Task 3 구현 준비 완료.

NO UNRESOLVED DECISIONS
