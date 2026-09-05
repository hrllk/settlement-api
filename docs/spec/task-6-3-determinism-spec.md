# Task 6.3 — 결정성 점검 명세

부모: [`task-6-core-scenario-tests-spec.md`](./task-6-core-scenario-tests-spec.md) · 의존 6.2 · 10분 · 테스트 1

## 왜 필요한가

이 프로젝트의 기대값은 전부 2025년 데이터에 고정돼 있다. 어떤 코드가 현재 시각을 읽으면 실행 시점에 따라 결과가 달라진다. 과제 제출 후 평가자가 돌릴 때 처음 깨지는 종류의 문제다.

## 검사 1 — `now()` 호출 금지 (자동)

`NoCurrentTimeUsageTest` — 소스 트리를 훑어 금지 패턴을 찾는다.

```
Instant.now(       LocalDate.now(      LocalDateTime.now(
ZonedDateTime.now( OffsetDateTime.now( new Date(
System.currentTimeMillis(
```

`src/main/java`와 `src/test/java` 전부가 대상이다. 하나라도 있으면 파일과 줄 번호를 모아 실패시킨다.

**자기 자신을 건너뛰어야 한다.** 이 테스트의 소스에 금지 문자열이 리터럴로 들어 있으므로, 그대로 두면 자기를 잡아 **항상 실패한다.** 두 가지 중 하나를 쓴다.

| 방법 | 내용 |
| --- | --- |
| **자기 파일 제외 (채택)** | 훑는 대상에서 `NoCurrentTimeUsageTest.java`를 뺀다. 한 줄이고 의도가 명확하다 |
| 문자열 연결 | `"Instant" + ".now("`처럼 쪼개 리터럴이 안 생기게 한다. 영리하지만 다음 사람이 왜 이렇게 썼는지 모른다 |

제외한 이유를 코드 주석에 남긴다. 안 남기면 누군가 "왜 자기를 빼지?" 하며 되돌린다.

**프로덕션 코드도 검사하는 이유.** 정산 조회에 "이번 달" 기본값 같은 편의 기능을 넣고 싶은 유혹이 있다. 넣는 순간 응답이 실행 시점에 따라 달라진다. 이 과제에서 시각은 전부 요청이 준다.

문자열 검사라 주석 속의 `Instant.now(`도 걸린다. 그건 받아들인다 — 주석에 쓸 이유가 없다.

## 검사 2 — 두 번 연속 실행 (수동)

```bash
./gradlew test && ./gradlew test
```

두 번째 실행이 첫 번째와 같은 결과여야 한다. Gradle이 `UP-TO-DATE`로 건너뛰면 `./gradlew cleanTest test`로 강제한다.

**이것이 잡는 것은 테스트 간 오염이다.** 6.2 통합 테스트가 등록한 2025-06 데이터가 롤백되지 않으면 두 번째 실행에서 데이터가 두 배가 된다. 월을 분리해 뒀으므로 다른 파일은 안 깨지지만, 6.2 자신이 `saleCount 1`을 기대하는데 2가 나와 실패한다.

**자기 자신이 오염을 감지하는 구조다.** 별도 감시 테스트가 필요 없다.

## 검사 3 — 실행 순서 무관 (수동)

JUnit 5는 기본적으로 메서드 순서를 보장하지 않는다. 클래스 순서도 마찬가지다. 이미 순서 비의존이 기본값이므로 별도 설정을 넣지 않는다.

`src/test`에서 `@TestMethodOrder`나 `@Order`를 쓴 곳이 있는지 확인한다. 있으면 그 테스트는 순서에 기대고 있다는 뜻이라 없앤다.

**`src/main`은 대상이 아니다.** `GlobalExceptionHandler`에 `@Order(Ordered.HIGHEST_PRECEDENCE)`가 붙어 있는데 그건 어드바이스 우선순위이지 테스트 순서가 아니다. 트리 전체를 grep하면 오탐이 난다.

## 외부 서비스 비의존

이 프로젝트는 외부 호출이 없다. HTTP 클라이언트도, 메시지 브로커도, 외부 API도 쓰지 않는다. 확인만 하고 넘어간다.

H2는 인메모리라 외부 의존이 아니다. 네트워크가 끊긴 상태에서도 `./gradlew test`가 돌아야 한다 — 단 Gradle 의존성이 이미 캐시된 상태여야 한다. Task 7.8의 클린 클론 점검이 이 조건을 다룬다.

## 파일

`src/test/java/.../NoCurrentTimeUsageTest.java`.

검사 2·3은 테스트가 아니라 절차다. 결과를 6.4 커버리지 감사 문서에 기록한다.

## 완료 기준

1. `NoCurrentTimeUsageTest`가 통과한다. **자기 파일을 대상에서 제외해 자기 매칭으로 실패하지 않는다.**
2. `./gradlew cleanTest test`를 두 번 연속 돌려 같은 결과가 나온다.
3. `src/test`에 `@TestMethodOrder`나 `@Order`를 쓴 테스트가 없다.
4. 프로덕션 코드에 `now()` 호출이 없다.
