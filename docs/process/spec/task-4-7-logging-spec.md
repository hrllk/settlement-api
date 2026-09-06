# Task 4.7 — 로깅 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 의존 4.2~4.4 · 5분

## 남기는 것

PRD가 "핵심 명령·조회 성공과 도메인 실패를 기본 로그로 남긴다"를 요구한다.

| 이벤트 | 수준 | 위치 | 내용 |
| --- | --- | --- | --- |
| 판매 등록 성공 | INFO | `RegisterSaleUseCase` | saleId, courseId, amount, actorId |
| 취소 등록 성공 | INFO | `RegisterCancelUseCase` | cancelId, saleId, amount, actorId |
| 판매 목록 조회 성공 | INFO | `ListCreatorSalesUseCase` | creatorId, 기간, 건수, actorId |
| 도메인 실패 5종 | WARN | `GlobalExceptionHandler` | 예외 이름, 메시지, 요청 경로 |

정산 조회 로깅은 Task 5가 같은 방식으로 추가한다.

## 계층 규칙

**application 계층에만 둔다.** 도메인에 SLF4J를 넣으면 순수 계산 코드가 로깅 프레임워크에 묶인다. Task 3 전제 11이 같은 규칙을 도메인 쪽에서 선언했다.

컨트롤러에도 두지 않는다. 같은 이벤트가 두 번 찍히고, 유스케이스를 직접 부르는 경로에서는 로그가 사라진다.

도메인 실패는 예외를 잡는 곳이 남긴다. 유스케이스가 던지고 처리기가 잡으므로 처리기가 소유한다. 던지는 쪽에서도 남기면 한 요청에 같은 실패가 두 줄 찍힌다.

## 수준을 나누는 기준

INFO는 정상 흐름, WARN은 사용자가 유발한 실패다.

**도메인 실패를 ERROR로 두지 않는다.** 초과 환불 거부는 시스템 오류가 아니라 규칙이 제대로 작동한 결과다. ERROR로 두면 정상 동작이 알람을 울린다.

**`IllegalArgumentException` / `NullPointerException`은 로깅하지 않는다.** 4.1이 이 둘을 잡지 않으므로 Spring 기본 500 경로로 가고 스택트레이스가 그대로 남는다. 우리가 WARN으로 잡아 삼키면 프로그래밍 버그의 스택트레이스가 사라진다.

## 무엇을 안 남기나

- 요청 본문 전체 — 필요한 필드만 남긴다
- 액터 헤더 원본 — `actorId`만 남기고 역할은 필요할 때만
- 조회 결과 내용 — 건수만 남긴다
- 정상 흐름의 DEBUG 추적 — 3시간 과제에서 노이즈다

## 파일

기존 유스케이스 3개와 `GlobalExceptionHandler`에 로그 문장을 추가한다. 새 파일이 없다.

테스트는 없다. 로그 문자열을 단언하는 테스트는 리팩터링마다 깨진다.

## 완료 기준

1. 유스케이스 3개가 성공 시 INFO를 남긴다.
2. 전역 처리기가 도메인 실패 5종에 WARN을 남긴다.
3. `domain` 패키지에 SLF4J import가 없다.
4. 컨트롤러에 로깅이 없다.
5. `IllegalArgumentException` / `NullPointerException`을 잡아 로깅하지 않는다.
