# Task 7.2 — API 명세와 curl 예시 명세

부모: [`task-7-readme-submission-spec.md`](./task-7-readme-submission-spec.md) · 의존 Task 5 · 20분

## 엔드포인트 표

| 메서드 | 경로 | ADMIN | CREATOR |
| --- | --- | --- | --- |
| POST | `/api/sales` | O | 403 |
| POST | `/api/sales/{saleId}/cancellations` | O | 403 |
| GET | `/api/creators/{creatorId}/sales?from=&to=` | 전부 | 본인만 |
| GET | `/api/creators/{creatorId}/settlements/{yearMonth}` | 전부 | 본인만 |
| GET | `/api/admin/settlements?from=&to=` | O | 403 |

Task 5.1의 역할 매트릭스를 그대로 옮긴다.

## curl 예시

**모든 예시에 `X-Actor-Id`와 `X-Actor-Role`을 넣는다.** 헤더 없이 복사한 예시는 400으로 실패하고, 평가자는 "API가 안 된다"고 판단한다.

시드 데이터로 바로 돌아가는 값을 쓴다. 예시를 그대로 붙여넣으면 문서에 적힌 응답이 나와야 한다.

```bash
# creator-1의 2025-03 정산 → payout 120000
curl -s localhost:8080/api/creators/creator-1/settlements/2025-03 \
  -H 'X-Actor-Id: creator-1' -H 'X-Actor-Role: CREATOR'

# 운영자 기간 집계 → totalPayout 168000
curl -s 'localhost:8080/api/admin/settlements?from=2025-03-01&to=2025-03-31' \
  -H 'X-Actor-Id: admin-1' -H 'X-Actor-Role: ADMIN'

# 판매 등록 (시각에 오프셋 필수)
curl -s -X POST localhost:8080/api/sales \
  -H 'X-Actor-Id: admin-1' -H 'X-Actor-Role: ADMIN' -H 'Content-Type: application/json' \
  -d '{"courseId":"course-1","amount":50000,"paidAt":"2025-06-10T10:00:00+09:00"}'
```

응답 본문도 함께 싣는다. 평가자가 돌려보기 전에 무엇이 나오는지 알 수 있어야 한다.

## 오류 응답 포맷

**RFC 9457 Problem Details를 쓴다.** 커스텀 포맷을 발명하지 않았다는 것 자체가 설명이 된다.

```json
{ "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "sale-3: 원결제 80000, 기존 취소 30000, 요청 60000",
  "code": "REFUND_AMOUNT_EXCEEDED" }
```

`Content-Type: application/problem+json`.

**모든 실패가 이 한 가지 모양임을 명시한다.** Bean Validation 실패와 액터 헤더 오류도 포함한다. Task 4.1이 세 종류로 갈릴 뻔한 것을 하나로 모았다.

`type`이 `about:blank`인 이유를 한 줄 적는다 — 오류 문서 사이트를 만들지 않았고 RFC가 기본값을 허용한다. 기계가 읽을 판별자는 `code` 확장 멤버가 맡는다.

| `code` (확장 멤버) | status | 언제 |
| --- | ---: | --- |
| `SALE_NOT_FOUND` | 404 | 없는 판매에 취소 |
| `COURSE_NOT_FOUND` | 404 | 없는 강의로 판매 등록 |
| `REFUND_AMOUNT_EXCEEDED` | 409 | 누적 환불이 원결제액 초과 |
| `ACTOR_ACCESS_DENIED` | 403 | 타인 자원 또는 운영자 전용 |
| `INVALID_SETTLEMENT_PERIOD` | 400 | `2025-13`, 종료일 < 시작일 |
| `VALIDATION_FAILED` | 400 | 금액 0 이하, 필수 필드 누락 |
| `MALFORMED_REQUEST` | 400 | 오프셋 없는 시각 |
| `INVALID_ACTOR_HEADER` | 400 | 액터 헤더 누락·형식 오류 |

**400과 403이 다른 이유를 한 줄 적는다.** 400은 신원을 모르는 것, 403은 신원을 알고 거부하는 것이다. 클라이언트가 재시도할지 포기할지 판단하는 근거가 된다.

## 오류 예시도 curl로

```bash
# 누적 초과 환불 → 409
# 타인 정산 조회 → 403
```

성공 경로만 적으면 오류 설계를 했는지 알 수 없다. 최소 두 개는 실제 curl로 보인다.

## 시각 포맷

**오프셋을 포함한 ISO-8601만 받는다.** `2025-03-05T10:00:00+09:00`. 오프셋이 없으면 400이다.

이유를 한 줄 적는다 — 오프셋 없이 받으면 서버가 무슨 시간대로 해석했는지 요청만 보고 알 수 없고, 경계에 걸린 값은 귀속 월이 바뀐다.

## 완료 기준

1. 엔드포인트 5개가 표로 있다.
2. 모든 curl에 액터 헤더가 있다.
3. 성공 3개, 오류 2개 이상의 curl 예시가 있다.
4. 예시가 시드 데이터로 실제 동작하고 응답이 문서와 일치한다.
5. 오류 code 8종이 표로 있다.
6. 400과 403의 차이가 설명돼 있다.
