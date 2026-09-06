# Task 4.6 — `SaleController` 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 의존 4.2~4.5 · 15분

## 타입

```java
package com.liveclass.settlement.adapter.in.web;

@RestController
@RequestMapping("/api")
public class SaleController {

    @PostMapping("/sales")
    ResponseEntity<SaleResponse> register(@Valid @RequestBody RegisterSaleRequest req,
                                          ActorContext actor) { ... }        // 201 + Location

    @PostMapping("/sales/{saleId}/cancellations")
    ResponseEntity<CancelResponse> cancel(@PathVariable String saleId,
                                          @Valid @RequestBody RegisterCancelRequest req,
                                          ActorContext actor) { ... }        // 201

    @GetMapping("/creators/{creatorId}/sales")
    CreatorSalesResponse list(@PathVariable String creatorId,
                              @RequestParam String from,
                              @RequestParam String to,
                              ActorContext actor) { ... }                    // 200
}
```

## 모든 메서드가 `ActorContext`를 선언한다

**Task 1의 해석기는 필터가 아니라 opt-in이다.** `ActorContextArgumentResolver.supportsParameter`가 파라미터 타입을 보고 동작하므로, 파라미터를 선언하지 않은 메서드는 헤더 검사를 통째로 건너뛴다. 그 엔드포인트는 인증 흔적도 없이 조용히 열린다.

컴파일러가 잡아주지 않는 유일한 구조적 위험이다. **Task 5.6의 리플렉션 가드 테스트가 이 컨트롤러까지 함께 검사한다.**

## 날짜를 `@RequestParam String`으로 받는다

`LocalDate`로 바인딩하면 Spring이 `MethodArgumentTypeMismatchException`을 먼저 던진다. 그러면 `2025-13`의 거부가 도메인이 아니라 프레임워크에서 일어나고, `InvalidSettlementPeriodException`가 영영 안 걸린다. 전역 처리기가 그 예외도 잡으면 되긴 하지만, 잘못된 연월 판정은 도메인 규칙이므로 도메인이 하게 둔다.

컨트롤러는 문자열을 그대로 유스케이스에 넘긴다. 파싱은 `SettlementPeriod.ofDateRange`가 한다.

## 컨트롤러가 하는 일과 안 하는 일

| 한다 | 안 한다 |
| --- | --- |
| DTO ↔ 유스케이스 인자 변환 | 접근 판정 (유스케이스가 `ActorAccessPolicy` 호출) |
| `OffsetDateTime` → `Instant` | 날짜 파싱 |
| 상태 코드와 `Location` 헤더 | 비즈니스 규칙 |
| — | 로깅 (4.7이 application 계층에) |

**접근 판정을 컨트롤러에 두지 않는다.** 두면 Task 4와 Task 5가 같은 호출을 각자 복제하고, 유스케이스를 직접 테스트할 때 경계가 빠진다.

## 등록은 201, 조회는 200

`POST /api/sales`는 `201 Created`와 `Location: /api/sales/{uuid}`를 낸다. 취소도 201이다. 취소에는 조회 엔드포인트가 없으므로 `Location`을 붙이지 않는다.

## 파일

`adapter/in/web/SaleController.java`.

테스트는 없다. 4.8이 검증한다.

## 완료 기준

1. 세 메서드 전부 `ActorContext` 파라미터를 선언한다.
2. 날짜가 `String`이다.
3. 등록이 201, `Location` 헤더가 있다.
4. 컨트롤러에 접근 판정 코드가 없다.
5. 컨트롤러에 로깅이 없다.
