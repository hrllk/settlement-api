# Task 5.6 — 접근 경계 테스트와 opt-in 가드 명세

부모: [`task-5-settlement-query-api-spec.md`](./task-5-settlement-query-api-spec.md) · 의존 5.4 · 20분 · 테스트 6

## 접근 경계 테스트

`SettlementControllerTest`에 둔다. `@SpringBootTest` + `@AutoConfigureMockMvc`.

| # | 케이스 | 헤더 | 기대 |
| --- | --- | --- | --- |
| 1 | CREATOR가 타인 정산 조회 | `creator-2` / CREATOR → `/creators/creator-1/settlements/2025-03` | **403** `ACTOR_ACCESS_DENIED` |
| 2 | CREATOR가 운영자 API 호출 | `creator-1` / CREATOR → `/admin/settlements` | **403** |
| 3 | ADMIN이 타인 정산 조회 | `admin-1` / ADMIN → `/creators/creator-1/settlements/2025-03` | 200 |
| 4 | 잘못된 연월 | `creator-1` / CREATOR → `/settlements/2025-13` | **400** `INVALID_SETTLEMENT_PERIOD` |

**1번이 IDOR을 잡는다.** 경로의 `creatorId`를 한 글자 바꾸는 것만으로 남의 정산이 보이면 안 된다. `requireSelfOrAdmin`이 `X-Actor-Id`와 경로를 비교하는지 확인한다.

**2번이 가장 값어치 있다.** `/api/admin/settlements`는 전체 크리에이터의 매출과 합계가 나가는 유일한 엔드포인트다. 여기가 뚫리면 한 크리에이터가 경쟁자 매출을 전부 본다.

**4번이 400임을 확인하는 이유는 컨트롤러 바인딩 함정 때문이다.** 5.4가 `@PathVariable String`을 쓰지 않고 `YearMonth`로 바꾸면 Spring이 먼저 거부해 `code`가 `INVALID_SETTLEMENT_PERIOD`가 아닌 다른 값이 된다. 상태 코드만 보면 둘 다 400이라 통과한다. **`code` 값까지 단언한다.**

CREATOR 본인 조회 200은 5.2가 이미 단언하므로 여기서 반복하지 않는다.

## opt-in 가드

`ControllerActorGuardTest` — 별도 파일. `@SpringBootTest`.

```java
@Test
void 모든_컨트롤러_핸들러가_ActorContext를_선언한다() {
    // RequestMappingHandlerMapping에서 핸들러 메서드를 전부 꺼낸다
    // 각 메서드의 파라미터에 ActorContext 타입이 있는지 검사
    // 없으면 메서드 이름을 전부 모아 한 번에 실패시킨다
}
```

`ApplicationContext`에서 `RequestMappingHandlerMapping`을 주입받아 `getHandlerMethods()`를 순회한다. 애플리케이션 패키지(`com.liveclass.settlement`)에 속한 핸들러만 본다 — Spring Boot의 기본 오류 컨트롤러가 섞여 들어오면 항상 실패한다.

## 왜 이 가드가 필요한가

Task 1의 `ActorContextArgumentResolver`는 **필터가 아니다.** `supportsParameter`가 파라미터 타입을 보고 동작하므로, 파라미터를 선언하지 않은 핸들러는 해석기를 아예 거치지 않는다. 헤더 검사도, 그 뒤의 인가 판정도 없다.

컴파일은 통과한다. 테스트도 통과한다. 응답도 200으로 정상이다. **아무것도 실패하지 않으면서 엔드포인트 하나가 무방비로 열린다.**

이것이 이 프로젝트에서 컴파일러가 잡아주지 않는 유일한 구조적 위험이다. 새 엔드포인트를 추가하는 사람이 규칙을 몰라도 이 테스트가 막는다.

**Task 4의 컨트롤러도 함께 검사한다.** 가드는 Task 5에 있지만 대상은 애플리케이션 전체다. Task 4에 두지 않은 이유는 Task 5의 컨트롤러가 생기기 전에는 검사 대상이 절반뿐이기 때문이다.

## 가드가 검사하지 않는 것

`ActorContext`를 선언했지만 유스케이스에서 `ActorAccessPolicy`를 부르지 않는 경우는 못 잡는다. 그건 리플렉션으로 알 수 없다. 1~3번 테스트가 그 층을 덮는다.

두 층이 다른 것을 잡는다. 가드는 **해석기를 거치는가**, 접근 테스트는 **거친 뒤 판정하는가**.

## 파일

`src/test/java/.../adapter/in/web/SettlementControllerTest.java` (1~4번)
`src/test/java/.../adapter/in/web/ControllerActorGuardTest.java` (가드)

## 완료 기준

1. 접근 경계 4건이 통과한다.
2. 4번이 상태 코드와 `code` 값을 모두 단언한다.
3. 가드가 Task 4·5의 컨트롤러 메서드 5개를 전부 통과시킨다.
4. 핸들러에서 `ActorContext`를 일부러 지우면 가드가 실패한다.
5. 가드가 Spring 기본 오류 컨트롤러를 대상에서 제외한다.
