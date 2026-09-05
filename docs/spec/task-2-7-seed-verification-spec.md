# Task 2.7 — 시드 재현성 테스트 명세

부모: [`task-2-sales-seed-data-spec.md`](./task-2-sales-seed-data-spec.md) · 의존 2.5, 2.6 · 10분 · 테스트 4

## 무엇을 검증하나

시드가 **의도한 값 그대로** 들어갔는지만 본다. 정산 계산 결과는 검증하지 않는다 — 그건 Task 3이 계산기 단위 테스트로 소유한다. 이 테스트는 "Task 3이 맞다고 가정할 때, 그 계산기에 들어갈 입력이 맞는가"를 잡는다.

## 테스트

`SeedDataTest` — `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import(SalesQueryJpaAdapter.class)`.

**`@Import`를 2.5와 똑같이 붙인다. 쓰지 않아도 붙인다.** 이유는 컨텍스트 캐시다.

`replace = NONE`이 없으면 `@DataJpaTest`가 내장 DB를 새로 띄워 `application.yml`의 H2와 `data.sql`을 무시한다. 시드 없는 빈 DB에서 돌아 네 건 전부 실패한다.

## 컨텍스트를 2.5와 공유해야 한다

`@Import`가 붙고 안 붙고는 **Spring의 컨텍스트 캐시 키를 가른다.** 설정이 다르면 컨텍스트가 둘 생기는데, 둘 다 `application.yml`의 `jdbc:h2:mem:creator-settlement;DB_CLOSE_DELAY=-1`을 본다. `DB_CLOSE_DELAY=-1`이라 JVM이 사는 동안 DB가 안 닫히므로 **서로 같은 인스턴스를 밟는다.**

`ddl-auto=create-drop`이라 두 번째 컨텍스트가 뜨는 순간 첫 번째가 쓰던 테이블을 드롭하고 다시 만든다. 실행 순서에 따라 통과할 수도 있지만 그때부터 **테스트가 순서에 의존한다.** Task 3이 "실행 순서에 의존하지 않는다"를 규칙으로 잡았는데 통합 레벨에서 그게 깨진다.

설정을 같게 두면 컨텍스트가 하나로 합쳐져 문제가 사라진다. Task 1의 `@SpringBootTest`는 어차피 별개 컨텍스트지만 엔티티도 시드도 안 보므로 영향이 없다. Task 4·6이 컨텍스트를 더 만들 때도 같은 규칙을 따른다.

| # | 케이스 | 단언 |
| --- | --- | --- |
| 1 | 행 수 | creators 3, courses 4, sales 7, cancels 3 |
| 2 | `sale-5`의 저장값 | `paidAt`이 정확히 `Instant.parse("2025-01-31T14:30:00Z")` |
| 3 | `sale-5`의 KST 귀속월 | KST 1월 구간 `[2024-12-31T15:00Z, 2025-01-31T15:00Z)`에 들어가고, KST 2월 구간에는 안 들어간다 |
| 4 | 취소↔판매 연결 | `cancel-1`→`sale-3`, `cancel-2`→`sale-4`, `cancel-3`→`sale-5`. 금액 80,000 / 30,000 / 60,000 |

## 2번과 3번이 따로 있는 이유

**2번은 매핑 타입을 잡는다.** 엔티티를 `LocalDateTime`으로 잘못 매핑하면 저장값이 `2025-01-31T23:30`이 되고 `Instant`와 비교하는 순간 컴파일이 안 되거나 값이 어긋난다. 타입 실수를 즉시 드러낸다.

**3번은 해석을 잡는다.** 타입이 맞아도 시드에 UTC 대신 KST 벽시계 값(`2025-01-31T23:30:00Z`)을 잘못 적을 수 있다. 그러면 2번은 통과하지 못하지만, 값을 실수로 `2025-02-01T...`처럼 적으면 2번만으로는 "그래서 몇 월이냐"를 못 잡는다. 3번이 구간 포함 여부를 직접 단언한다.

`sale-5`가 이 프로젝트에서 유일하게 KST와 UTC의 날짜가 갈리는 레코드다. 여기가 틀리면 creator-2의 1월과 2월 정산이 통째로 뒤집힌다.

## 하지 않는 것

- 정산 금액 단언 — Task 3
- 운영자 집계 — Task 5
- 초과 환불 거부 — Task 4
- 포트 메서드 동작 — 2.5

시드가 바뀌면 이 파일과 Task 3의 기준표 상수를 **둘 다** 고쳐야 한다. 같은 숫자가 두 곳에 있는 것은 불가피하다. 하나는 SQL, 하나는 Java 상수다. Task 6.4 커버리지 감사가 이 대응을 점검한다.

## 파일

`src/test/java/.../adapter/out/persistence/SeedDataTest.java`.

## 완료 기준

1. 테스트 4건이 통과한다.
2. `@AutoConfigureTestDatabase(replace = NONE)`과 `@Import(SalesQueryJpaAdapter.class)`가 2.5와 동일하다.
3. `sale-5`의 KST 귀속월이 명시적으로 단언된다.
4. 정산 금액을 단언하지 않는다.
