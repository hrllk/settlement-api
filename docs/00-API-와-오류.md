# API와 오류

엔드포인트 5개와 오류 계약입니다. 아래 JSON은 전부 애플리케이션을 띄워 실제로 받은 응답입니다.

## 목차

1. [엔드포인트](#엔드포인트)
2. [시각과 기간 규칙](#시각과-기간-규칙)
3. [조회](#조회)
   - [크리에이터 월별 정산 조회 API](#크리에이터-월별-정산-조회-api)
   - [운영자 기간 정산 집계 API](#운영자-기간-정산-집계-api)
   - [크리에이터 판매 목록 조회 API](#크리에이터-판매-목록-조회-api)
4. [등록](#등록)
   - [판매 등록 API](#판매-등록-api)
   - [취소 등록 API](#취소-등록-api)
5. [오류](#오류)

## 엔드포인트

액터는 헤더 두 개로 표기합니다. `X-Actor-Id`, `X-Actor-Role`(`ADMIN` 또는 `CREATOR`).

| API명 | 메서드 | 경로 | ADMIN | CREATOR |
| --- | --- | --- | --- | --- |
| [판매 등록](#판매-등록-api) | POST | `/api/sales` | O | 403 |
| [취소 등록](#취소-등록-api) | POST | `/api/sales/{saleId}/cancellations` | O | 403 |
| [크리에이터 판매 목록 조회](#크리에이터-판매-목록-조회-api) | GET | `/api/creators/{creatorId}/sales?from=&to=` | 전부 | 본인만 |
| [크리에이터 월별 정산 조회](#크리에이터-월별-정산-조회-api) | GET | `/api/creators/{creatorId}/settlements/{yearMonth}` | 전부 | 본인만 |
| [운영자 기간 정산 집계](#운영자-기간-정산-집계-api) | GET | `/api/admin/settlements?from=&to=` | O | 403 |

## 시각과 기간 규칙

**시각은 오프셋 포함 ISO-8601만 받습니다.** `2025-03-05T10:00:00+09:00`. 오프셋이 없으면
400입니다. Jackson이 오프셋 없는 값을 UTC로 **조용히** 파싱하기 때문에, 9시간 어긋난 값이
아무 오류 없이 저장되고 경계에 걸린 값은 귀속 월까지 바뀝니다.

**`from`/`to`는 종료일 하루 전체를 포함합니다.** `from=2025-03-01&to=2025-03-31`은 3월 31일을
포함하며, 내부적으로 `[03-01 00:00 KST, 04-01 00:00 KST)`로 바뀝니다.

## 조회

### 크리에이터 월별 정산 조회 API

`GET /api/creators/{creatorId}/settlements/{yearMonth}` · 본인 CREATOR 또는 ADMIN

한 달치 정산 요약을 돌려줍니다. 판매도 취소도 없는 달은 404가 아니라 200에 전 항목 0입니다.

```bash
# creator-1의 2025-03 정산
curl -s localhost:8080/api/creators/creator-1/settlements/2025-03 \
  -H 'X-Actor-Id: creator-1' -H 'X-Actor-Role: CREATOR'
```
```json
{"creatorId":"creator-1","yearMonth":"2025-03","grossSales":260000,"saleCount":4,
 "refunds":110000,"cancelCount":2,"netSales":150000,"fee":30000,"payout":120000}
```

### 운영자 기간 정산 집계 API

`GET /api/admin/settlements?from=&to=` · ADMIN 전용

기간 내 전체 크리에이터의 정산을 한 번에 돌려줍니다. **기간 전체를 단일 구간으로 계산하므로
월별 조회의 합과 다릅니다** — 근거는 [정산 규칙](01-정산-규칙과-KST-경계.md)에 있습니다.

```bash
# 운영자 기간 집계
curl -s 'localhost:8080/api/admin/settlements?from=2025-03-01&to=2025-03-31' \
  -H 'X-Actor-Id: admin-1' -H 'X-Actor-Role: ADMIN'
```
```json
{"from":"2025-03-01","to":"2025-03-31","creators":[
  {"creatorId":"creator-1","grossSales":260000,"saleCount":4,"refunds":110000,
   "cancelCount":2,"netSales":150000,"fee":30000,"payout":120000},
  {"creatorId":"creator-2","grossSales":60000,"saleCount":1,"refunds":0,
   "cancelCount":0,"netSales":60000,"fee":12000,"payout":48000},
  {"creatorId":"creator-3","grossSales":0,"saleCount":0,"refunds":0,
   "cancelCount":0,"netSales":0,"fee":0,"payout":0}],
 "totalPayout":168000}
```

실적 없는 creator-3도 0원으로 들어갑니다. 판매·취소 자료만 훑으면 그 존재를 알 방법이 없어
목록에서 통째로 빠집니다.

### 크리에이터 판매 목록 조회 API

`GET /api/creators/{creatorId}/sales?from=&to=` · 본인 CREATOR 또는 ADMIN

기간 내 판매를 환불 상태와 함께 돌려줍니다.

```bash
# 판매 목록 — sale-5를 1월 구간으로 조회
curl -s 'localhost:8080/api/creators/creator-2/sales?from=2025-01-01&to=2025-01-31' \
  -H 'X-Actor-Id: creator-2' -H 'X-Actor-Role: CREATOR'
```
```json
{"creatorId":"creator-2","sales":[
  {"saleId":"sale-5","courseId":"course-3","studentId":"student-5","amount":60000,
   "paidAt":"2025-01-31T23:30:00+09:00","refundStatus":"FULL"}]}
```

**`refundStatus`가 `FULL`인 것이 핵심입니다.** 취소는 2월인데 1월 구간 조회에서도 `FULL`입니다.
[환불 상태만 기간과 무관합니다](01-정산-규칙과-KST-경계.md#환불-상태만-기간과-무관합니다)를 보십시오.

## 등록

### 판매 등록 API

`POST /api/sales` · ADMIN 전용

결제가 완료되면 호출됩니다. 없는 강의는 404입니다. 구매 주체는 본문의 `studentId`이고,
호출 주체는 결제 시스템을 대신하는 운영자입니다.

```bash
curl -s -X POST localhost:8080/api/sales \
  -H 'X-Actor-Id: admin-1' -H 'X-Actor-Role: ADMIN' -H 'Content-Type: application/json' \
  -d '{"courseId":"course-1","studentId":"student-1","amount":50000,"paidAt":"2025-06-10T10:00:00+09:00"}'
```
```json
{"saleId":"250970b2-5529-489f-95de-47bd09d2c4ff","courseId":"course-1","studentId":"student-1",
 "amount":50000,"paidAt":"2025-06-10T10:00:00+09:00"}
```

`saleId`는 서버가 만드는 UUID입니다. 시드의 `sale-1` 형태는 형식 제약이 아닙니다.

### 취소 등록 API

`POST /api/sales/{saleId}/cancellations` · ADMIN 전용

환불이 발생하면 호출됩니다. 누적 취소액이 원결제를 넘으면 409, 결제보다 이른 취소도 409입니다.
취소에는 조회 엔드포인트가 없어 `Location` 헤더를 붙이지 않습니다.

```bash
# 취소 등록
curl -s -X POST localhost:8080/api/sales/{saleId}/cancellations \
  -H 'X-Actor-Id: admin-1' -H 'X-Actor-Role: ADMIN' -H 'Content-Type: application/json' \
  -d '{"amount":30000,"cancelledAt":"2025-06-20T10:00:00+09:00"}'
```

## 오류

**모든 실패가 RFC 9457 Problem Details 한 가지 모양입니다.** `Content-Type: application/problem+json`.
포맷을 발명하지 않고 Spring 내장 `ProblemDetail`을 씁니다.

```bash
# 누적 초과 환불 → 409
curl -s -X POST localhost:8080/api/sales/{saleId}/cancellations \
  -H 'X-Actor-Id: admin-1' -H 'X-Actor-Role: ADMIN' -H 'Content-Type: application/json' \
  -d '{"amount":60000,"cancelledAt":"2025-07-03T10:00:00+09:00"}'
```
```json
{"type":"urn:problem-type:refund-amount-exceeded","title":"Conflict","status":409,
 "detail":"refund exceeds sale amount: saleId=832244b6..., saleAmount=80000, alreadyCancelled=30000, requested=60000",
 "instance":"/api/sales/832244b6.../cancellations","code":"REFUND_AMOUNT_EXCEEDED"}
```

`type`이 `urn:`인 것은 오류 문서 사이트를 호스팅하지 않기 때문입니다. RFC 9457이 허용하는
형태입니다. `code`는 같은 값의 짧은 표기이며 클라이언트 분기용 확장 멤버입니다.

| `code` | status | 언제 |
| --- | ---: | --- |
| `SALE_NOT_FOUND` | 404 | 없는 판매에 취소 |
| `COURSE_NOT_FOUND` | 404 | 없는 강의로 판매 등록 |
| `REFUND_AMOUNT_EXCEEDED` | 409 | 누적 환불이 원결제액 초과 |
| `CANCEL_BEFORE_PAYMENT` | 409 | 취소 시각이 결제 시각보다 이름 |
| `ACTOR_ACCESS_DENIED` | 403 | 타인 자원 또는 운영자 전용 |
| `INVALID_SETTLEMENT_PERIOD` | 400 | `2025-13`, 종료일 < 시작일, 지원 범위 밖 날짜 |
| `VALIDATION_FAILED` | 400 | 금액 0 이하 또는 10억 원 초과, 필수 필드 누락 |
| `MALFORMED_REQUEST` | 400 | 오프셋 없는 시각 |
| `INVALID_ACTOR_HEADER` | 400 | 액터 헤더 누락·형식 오류 |
| `MISSING_PARAMETER` | 400 | `from` 또는 `to` 누락 |

**400과 403은 다른 경로입니다.** 400은 신원을 **모르는** 것이고 403은 신원을 **알고 거부**하는
것입니다. 클라이언트가 재시도할지 포기할지 판단하는 근거가 됩니다.

**`code`가 붙는 것은 우리가 이름 붙인 실패뿐입니다.** 405나 없는 경로처럼 프레임워크가 만드는
실패는 `application/problem+json`이지만 `code`와 `type`이 없습니다. 거기에 `code`를 붙이려면
Spring의 프레임워크 예외 목록을 복제해야 하고, 그 목록은 버전마다 바뀝니다.

```json
{"detail":"Method 'PATCH' is not supported.","instance":"/api/sales",
 "status":405,"title":"Method Not Allowed"}
```

---

관련 문서 — [정산 규칙과 KST 경계](01-정산-규칙과-KST-경계.md) · [아키텍처와 요청 흐름](03-아키텍처와-요청-흐름.md)
