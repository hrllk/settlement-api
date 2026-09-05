# Task 3.1 — `SettlementPeriod` 명세

부모: [`task-3-settlement-domain-spec.md`](./task-3-settlement-domain-spec.md) · 의존 없음 (3.2, 3.3과 병렬) · 20분

## 타입

```java
package com.liveclass.settlement.domain.settlement;

public record SettlementPeriod(Instant fromInclusive, Instant toExclusive) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public SettlementPeriod {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new InvalidSettlementPeriod("invalid period: [" + fromInclusive + ", " + toExclusive + ")");
        }
    }

    public static SettlementPeriod ofYearMonth(String yearMonth);
    public static SettlementPeriod ofDateRange(String startDate, String endDate);

    public boolean contains(Instant at) {
        return !at.isBefore(fromInclusive) && at.isBefore(toExclusive);
    }
}

public class InvalidSettlementPeriod extends RuntimeException { }
```

## 동작

**입력 가드가 파싱보다 먼저다.** `YearMonth.parse(null)`은 `DateTimeParseException`이 아니라 진입부 `Objects.requireNonNull`의 **NPE**를 던진다. `DateTimeParseException`만 잡으면 NPE가 그대로 500이 된다.

```java
private static String requireText(String raw, String field) {
    if (raw == null || raw.isBlank()) throw new InvalidSettlementPeriod(field + " must not be blank: " + raw);
    return raw.strip();
}
```

`strip()` 후 파싱한다. Task 1의 액터 헤더 처리와 규칙을 맞춘다.

| 팩토리 | 파싱 | `fromInclusive` | `toExclusive` |
| --- | --- | --- | --- |
| `ofYearMonth("2025-03")` | `YearMonth.parse` | 해당 월 1일 `atStartOfDay(KST)` | **다음 달** 1일 `atStartOfDay(KST)` |
| `ofDateRange("2025-03-01", "2025-03-31")` | `LocalDate.parse` ×2 | 시작일 `atStartOfDay(KST)` | 종료일 **다음 날** `atStartOfDay(KST)` |

`ofDateRange`는 `LocalDate` 비교로 역전을 먼저 거부한다. compact 생성자도 같은 불변식을 걸지만 그때는 원본 문자열이 없어 메시지가 `Instant`로 나온다. 같은 날은 허용한다(하루짜리 구간).

`DateTimeParseException`은 잡아서 `InvalidSettlementPeriod`로 바꿔 던진다. 메시지에 거부된 입력값을 넣는다.

기간 길이에 상한을 두지 않는다. README에 가정으로 남긴다.

## 오류

| 입력 | 걸리는 지점 |
| --- | --- |
| `null`, `""`, `"   "` | `requireText` |
| `"2025-13"`, `"2025/03"`, `"202503"` | `DateTimeParseException` 변환 |
| `ofDateRange("2025-03-31", "2025-03-01")` | 팩토리 `LocalDate` 비교 |
| `new SettlementPeriod(늦은시각, 이른시각)` | compact 생성자 |

`InvalidSettlementPeriod`는 이 프로젝트에서 사용자가 만들 수 있는 유일한 도메인 예외이며 Task 4가 400으로 변환한다.

**웹 어댑터는 `String`으로 받아 팩토리에 그대로 넘긴다.** `@PathVariable YearMonth`로 바인딩하면 Spring이 `MethodArgumentTypeMismatchException`을 먼저 던져 이 예외가 걸리지 않는다.

## 테스트 — `SettlementPeriodTest` 15건

아래 11개 케이스 중 형식 불일치와 빈 값이 파라미터화되어 실행 시 15건이 된다.

| 케이스 | 입력 | 기대 |
| --- | --- | --- |
| 연월 정규화 | `"2025-03"` | `[03-01T00:00+09:00, 04-01T00:00+09:00)` |
| 일자 범위 | `"2025-03-01"`, `"2025-03-31"` | 위와 동일 |
| 하루짜리 | `"2025-03-05"` ×2 | `[03-05T00:00+09:00, 03-06T00:00+09:00)` |
| **시작 경계 포함** | `2025-03-01T00:00:00.000+09:00` | 3월 포함 |
| **종료 경계 포함** | `2025-01-31T23:59:59.999+09:00` | 1월 포함 |
| **종료 경계 배제** | `2025-02-01T00:00:00.000+09:00` | 1월 **불포함**, 2월 포함 |
| 기간 역전 | `"2025-03-31"`, `"2025-03-01"` | `InvalidSettlementPeriod` |
| 월 범위 초과 | `"2025-13"` | 〃 |
| 형식 불일치 | `"2025/03"` | 〃 |
| null·빈 값 | `null`, `""` | 〃 |
| 생성자 우회 | `new SettlementPeriod(늦은, 이른)` | 〃 |

경계 셋이 각각 잡는 것: 시작 포함은 `> from` 구현, 종료 포함(`.999`)은 원본 과제 문구를 그대로 옮긴 `<= 23:59:59` 구현, 종료 배제는 `<= to` 구현. 앞의 둘만으로는 상한을 나노초까지 닫은 구현이 통과한다.

`Instant`는 `OffsetDateTime.parse(...).toInstant()`로 만든다. UTC로 손 변환하지 않는다.

## 파일

`domain/settlement/`에 `SettlementPeriod.java`, `InvalidSettlementPeriod.java`. 테스트는 `SettlementPeriodTest.java`. **이 서브태스크가 먼저 끝나면 `domain/.gitkeep`을 지운다.**

## 완료 기준

1. 15건 통과, Spring 컨텍스트 없이 돈다.
2. 경계 3방향과 생성자 우회 케이스가 있다.
3. `DateTimeParseException`도 NPE도 밖으로 새지 않는다.
4. Spring 애노테이션과 로깅이 없다.
