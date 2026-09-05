# Task 5.5 — 응답 DTO 명세

부모: [`task-5-settlement-query-api-spec.md`](./task-5-settlement-query-api-spec.md) · 의존 없음 · 10분

## 타입

```java
package com.liveclass.settlement.adapter.in.web.dto;

public record MonthlySettlementResponse(
        String creatorId, String yearMonth,
        long grossSales, int saleCount,
        long refunds,    int cancelCount,
        long netSales, long fee, long payout) { }

public record CreatorPayoutItem(
        String creatorId,
        long grossSales, int saleCount,
        long refunds,    int cancelCount,
        long netSales, long fee, long payout) { }

public record AdminSettlementResponse(
        String from, String to,
        List<CreatorPayoutItem> creators,
        long totalPayout) { }
```

## 7개 금액·건수 필드를 전부 내보낸다

`tasks.json`의 "건수"를 "판매 건수와 취소 건수"로 고쳤다. 원본 과제가 둘을 요구한다.

**두 건수가 따로 필요한 이유는 이중 집계 기준 때문이다.** creator-2의 2025-02는 판매 건수 0, 취소 건수 1이다. 하나로 합치면 이 달이 "1건"으로 보여 판매가 있었는지 없었는지 알 수 없다.

`netSales`, `fee`, `payout`도 전부 내보낸다. 클라이언트가 `grossSales − refunds`를 다시 계산하게 두면 반올림이나 음수 처리에서 서버와 갈릴 수 있다. 특히 `fee`는 음수 순 판매액에서 0으로 막히므로 클라이언트가 20%를 곱해서는 절대 나오지 않는다.

## `payout`이 음수일 수 있다

creator-2의 2025-02는 −60,000이다. 크리에이터가 플랫폼에 돌려줘야 할 환불 부담액이다.

**응답 스키마에서 이 필드를 부호 없는 타입으로 두면 안 된다.** `long`이고 음수를 그대로 내보낸다. 0으로 깎거나 절댓값을 취하면 정보가 사라진다.

수수료는 0으로 막고 정산 예정액은 음수를 허용하는 비대칭이 여기 드러난다. Task 3 전제 6의 근거를 README에 남긴다.

## `SettlementSummary`를 그대로 내보내지 않는다

도메인 record를 응답으로 직렬화하면 필드 이름 변경이 곧 API 변경이 된다. DTO를 따로 두면 도메인이 자유롭게 바뀔 수 있다.

지금은 필드가 1:1이라 DTO가 군더더기로 보인다. 그래도 둔다 — `creatorId`와 `yearMonth`는 요약에 없는 값이고, 운영자 응답은 목록과 합계를 더한다. 어차피 모양이 다르다.

## 날짜를 문자열로 되돌려준다

`yearMonth`, `from`, `to`는 요청에 온 문자열을 그대로 담는다. 응답에 요청 조건이 있어야 여러 호출의 결과를 섞지 않는다.

## 파일

`adapter/in/web/dto/MonthlySettlementResponse.java`, `CreatorPayoutItem.java`, `AdminSettlementResponse.java`.

## 완료 기준

1. 세 record가 있다.
2. 금액·건수 7개 필드가 전부 있다. 판매 건수와 취소 건수가 따로다.
3. `payout`이 `long`이고 음수를 담는다.
4. 도메인 `SettlementSummary`가 직접 직렬화되지 않는다.
