# Task 5.4 — `SettlementController` 명세

부모: [`task-5-settlement-query-api-spec.md`](./task-5-settlement-query-api-spec.md) · 의존 5.2, 5.3, 5.5 · 15분

## 타입

```java
package com.liveclass.settlement.adapter.in.web;

@RestController
@RequestMapping("/api")
public class SettlementController {

    @GetMapping("/creators/{creatorId}/settlements/{yearMonth}")
    MonthlySettlementResponse monthly(@PathVariable String creatorId,
                                      @PathVariable String yearMonth,
                                      ActorContext actor) { ... }

    @GetMapping("/admin/settlements")
    AdminSettlementResponse admin(@RequestParam String from,
                                  @RequestParam String to,
                                  ActorContext actor) { ... }
}
```

## 연월과 일자를 `String`으로 받는다

**`@PathVariable YearMonth`를 쓰면 안 된다.** Spring이 바인딩 단계에서 `MethodArgumentTypeMismatchException`을 먼저 던져 `InvalidSettlementPeriodException`가 영영 안 걸린다.

`2025-13`의 거부는 도메인 규칙이다. Task 3의 `SettlementPeriod`가 판정을 소유하고, 그래야 오류 응답이 다른 도메인 실패와 같은 모양으로 나간다. 프레임워크가 먼저 거부하면 `{code: "INVALID_SETTLEMENT_PERIOD"}` 대신 Spring 기본 본문이 나가거나, 전역 처리기에 예외 하나를 더 등록해야 한다.

`@RequestParam String from/to`도 같은 이유다.

컨트롤러는 문자열을 그대로 유스케이스에 넘긴다.

## 두 메서드 모두 `ActorContext`를 선언한다

Task 1의 해석기는 opt-in이다. 선언을 빠뜨리면 헤더 검사도 인가 판정도 없이 열린다. 특히 `/api/admin/settlements`는 **전체 매출이 나가는 엔드포인트**라 여기서 빠지면 누구나 볼 수 있다.

5.6의 리플렉션 가드가 이 두 메서드를 검사한다.

## 컨트롤러가 하는 일과 안 하는 일

| 한다 | 안 한다 |
| --- | --- |
| 경로·쿼리 파라미터를 유스케이스에 전달 | 접근 판정 (유스케이스가 호출) |
| 도메인 결과 → 응답 DTO 변환 | 날짜 파싱 |
| — | 계산, 집계, 합산 |
| — | 로깅 (유스케이스가 남긴다) |

## 경로를 나눈 이유

크리에이터 정산은 `/api/creators/{id}/settlements/{ym}`, 운영자는 `/api/admin/settlements`다.

운영자 엔드포인트에 `creatorId`가 없어 경로만 봐도 대상 범위가 드러난다. 하나의 엔드포인트에 `creatorId` 옵션 파라미터를 두는 방식도 가능하지만, 그러면 "파라미터를 빼면 전체가 나온다"는 동작이 되어 인가 실수의 여지가 커진다.

## 파일

`adapter/in/web/SettlementController.java`.

테스트는 없다. 5.2·5.3·5.6이 검증한다.

## 완료 기준

1. 두 메서드 전부 `ActorContext` 파라미터를 선언한다.
2. 연월과 일자가 `String`이다. `YearMonth` / `LocalDate` 바인딩이 없다.
3. 컨트롤러에 접근 판정 코드가 없다.
4. 컨트롤러에 산술이 없다.
