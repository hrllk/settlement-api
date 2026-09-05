# Task 5.1 — 역할 매트릭스 명세

부모: [`task-5-settlement-query-api-spec.md`](./task-5-settlement-query-api-spec.md) · 의존 없음 · 5분

코드가 아니라 **결정**이다. 판정 코드는 Task 4.1의 `ActorAccessPolicy`가 이미 갖고 있다. 이 문서는 어느 엔드포인트에 어떤 호출이 붙는지를 확정한다.

## 매트릭스

| 엔드포인트 | ADMIN | CREATOR | 호출 |
| --- | --- | --- | --- |
| `POST /api/sales` | 허용 | 거부 | `requireAdmin` |
| `POST /api/sales/{id}/cancellations` | 허용 | 거부 | `requireAdmin` |
| `GET /api/creators/{id}/sales` | 전부 | 본인만 | `requireSelfOrAdmin(actor, id)` |
| `GET /api/creators/{id}/settlements/{ym}` | 전부 | 본인만 | `requireSelfOrAdmin(actor, id)` |
| `GET /api/admin/settlements` | 허용 | 거부 | `requireAdmin` |

위반은 전부 `ActorAccessDenied` → 403.

앞 두 줄과 세 번째 줄은 Task 4의 엔드포인트다. **매트릭스는 다섯 줄 전부를 소유한다.** 규칙이 다섯 곳에 흩어지면 어느 엔드포인트가 무방비인지 표 없이는 알 수 없다.

## 막아야 하는 두 가지

**크리에이터가 전체 매출을 보는 것.** `X-Actor-Role: ADMIN`은 헤더라 누구나 쓸 수 있지만, 정직한 클라이언트가 `CREATOR`로 운영자 API를 부르는 것은 막아야 한다. 헤더 위조는 실제 인증이 없다는 한계이고 README 가정 8로 남긴다. 위조하지 않은 경우까지 통과시킬 이유는 없다.

**크리에이터 A가 경로를 B로 바꿔 타인 정산을 보는 것.** `X-Actor-Id`와 경로 `creatorId`가 다르면 거부한다. 전형적인 IDOR이고, 경로 파라미터를 한 글자 바꾸는 것만으로 밟힌다.

## 판정 위치

**유스케이스에서 호출한다. 컨트롤러가 아니다.**

컨트롤러에 두면 세 가지가 생긴다. Task 4와 Task 5가 같은 호출을 각자 복제하고, 유스케이스를 직접 부르는 테스트에서 경계가 빠지고, 새 진입점(배치, 다른 컨트롤러)이 생기면 검사가 통째로 사라진다.

컨트롤러는 `ActorContext`를 유스케이스에 넘기기만 한다.

## opt-in의 구멍

Task 1의 해석기는 필터가 아니다. 컨트롤러 메서드가 `ActorContext` 파라미터를 선언해야만 동작한다. **선언을 빠뜨린 메서드는 헤더 검사도 인가 판정도 없이 열린다.**

컴파일러가 잡아주지 않는다. 5.6의 리플렉션 가드 테스트가 이 매트릭스의 다섯 줄이 실제로 코드에 붙어 있는지 검사한다.

## 이 태스크가 만드는 것

없다. 결정만 한다. 5.2·5.3·5.4가 이 표대로 `ActorAccessPolicy`를 호출하고, 5.6이 표대로 검증한다. Task 4는 이미 자기 세 줄을 구현했다.

## 완료 기준

1. 다섯 엔드포인트 전부 규칙이 정해져 있다.
2. Task 4의 세 엔드포인트 규칙이 이 표와 일치한다.
3. 5.6 테스트가 이 표를 근거로 작성된다.
