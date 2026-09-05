# Task 4.5 — DTO와 Bean Validation 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 의존 없음 · 10분

## 요청 DTO

```java
package com.liveclass.settlement.adapter.in.web.dto;

public record RegisterSaleRequest(
        @NotBlank String courseId,
        @Positive long amount,
        @NotNull OffsetDateTime paidAt) { }

public record RegisterCancelRequest(
        @Positive long amount,
        @NotNull OffsetDateTime cancelledAt) { }
```

## 응답 DTO

```java
public record SaleResponse(String saleId, String courseId, long amount, OffsetDateTime paidAt) { }
public record CancelResponse(String cancelId, String saleId, long amount, OffsetDateTime cancelledAt) { }
public record CreatorSalesResponse(String creatorId, List<SaleItem> sales) { }
public record SaleItem(String saleId, String courseId, long amount,
                       OffsetDateTime paidAt, RefundStatus refundStatus) { }
```

## 시각은 `OffsetDateTime`으로 받는다

**`Instant`가 아니라 `OffsetDateTime`이다.** 이유는 오프셋 강제다.

Jackson은 `Instant` 필드에 `"2025-03-05T10:00:00"`처럼 오프셋 없는 값이 오면 UTC로 가정해 조용히 파싱한다. 그러면 KST 10시로 보낸 요청이 UTC 10시(KST 19시)로 저장되고, 아무 오류 없이 9시간 어긋난 정산이 나온다. `sale-5`처럼 경계에 걸린 값이면 귀속 월까지 바뀐다.

`OffsetDateTime`은 오프셋이 없으면 파싱에 실패한다. `HttpMessageNotReadableException`이 나고 요청이 거부된다.

응답도 `OffsetDateTime`으로 내보낸다. `Instant`로 직렬화하면 `Z`로만 나가 평가자가 KST 값을 암산해야 한다.

유스케이스에 넘길 때 `.toInstant()`로 변환한다. 컨트롤러가 그 경계를 담당한다.

**`spring.jackson.serialization.write-dates-as-timestamps=false`를 `application.yml`에 넣는다.** 안 넣으면 Jackson이 시각을 epoch 숫자 배열로 직렬화해 README curl 예시가 읽을 수 없게 된다.

## 검증 실패의 응답 포맷

`@Valid`가 붙은 요청 바디의 위반은 `MethodArgumentNotValidException`이 되고 4.1의 전역 처리기가 400 + `{code: "VALIDATION_FAILED", ...}`로 바꾼다.

**오프셋 누락은 다른 예외다.** Jackson 역직렬화 단계에서 실패하므로 `HttpMessageNotReadableException`이 난다. 4.1의 처리기가 이것도 잡아 `code: "MALFORMED_REQUEST"`, 400으로 내보내야 한다. 안 잡으면 Spring 기본 본문으로 나가 포맷이 갈린다.

이 항목은 4.1 명세의 처리기 목록에 없었다. **4.1을 구현할 때 함께 넣는다.**

## 금액 검증을 DTO에서 하는 이유

`@Positive`는 요청 형식의 문제다. 0원이나 음수 판매는 도메인 규칙 위반이 아니라 애초에 말이 안 되는 입력이다. 도메인 예외를 만들면 예외가 6종이 되고, 유스케이스마다 같은 검사를 반복하게 된다.

Task 3의 `SaleData` / `CancelData`는 금액 부호를 검증하지 않는다. 그 판단이 여기 있다는 것을 Task 3 명세가 명시했다.

## 파일

`adapter/in/web/dto/` 아래 record 7개. `application.yml`에 Jackson 설정 한 줄.

테스트는 없다. 4.8이 검증한다.

## 완료 기준

1. 요청 DTO에 `@NotBlank`, `@Positive`, `@NotNull`이 있다.
2. 시각 필드가 `OffsetDateTime`이다.
3. 오프셋 없는 값이 400으로 거부된다.
4. 응답 시각이 오프셋 포함 문자열로 직렬화된다. epoch 숫자가 아니다.
