# Task 4.1 — 예외·전역 처리기·접근 정책·빈 등록 명세

부모: [`task-4-sales-cancel-api-spec.md`](./task-4-sales-cancel-api-spec.md) · 형제 의존 없음. Task 1(액터 타입)과 Task 3(`FeePolicy`, `SettlementCalculator`, `InvalidSettlementPeriod`)이 선행 · 25분

**Task 4에서 가장 먼저 한다.** 뒤 서브태스크 전부와 Task 5가 여기에 의존한다.

## 1. 도메인 예외 2종

```java
package com.liveclass.settlement.domain.settlement;

public class SaleNotFound extends RuntimeException {
    public SaleNotFound(String saleId) { super("sale not found: " + saleId); }
}
public class CourseNotFound extends RuntimeException { ... }
```

**`RefundAmountExceeded`는 여기서 만들지 않는다.** 4.2가 `domain/sales`에 만든다. 그 애그리게이트가 던지는 예외이므로 던지는 쪽에 둔다.

이 구분이 중요한 이유는 **같은 이름의 클래스를 두 패키지에 만들면 컴파일은 통과하고 런타임이 틀리기 때문이다.** 전역 처리기가 `domain.settlement` 것을 잡도록 import하고 애그리게이트가 `domain.sales` 것을 던지면, `@ExceptionHandler`가 매칭되지 않아 409 대신 500이 나간다. 4.8 케이스 6이 잡긴 하지만 원인을 찾는 데 시간이 든다.

전역 처리기는 `com.liveclass.settlement.domain.sales.RefundAmountExceeded`를 import한다.

둘 다 `domain.settlement`에 둔다. Task 3의 `InvalidSettlementPeriod`와 같은 자리다. **Spring 애노테이션을 붙이지 않는다** — `@ResponseStatus`를 쓰면 도메인이 HTTP를 알게 되고, 상태 코드가 처리기와 애노테이션 두 곳에 흩어진다.

## 2. 액터 타입을 `application`으로 옮긴다

Task 1은 `ActorContext`, `ActorRole`, `ActorContextArgumentResolver`를 전부 `adapter.in.actor`에 두었다. 그때는 컨트롤러만 쓰는 타입이라 맞았다.

**이제 유스케이스가 `ActorContext`를 받으므로 `application → adapter.in` 의존이 생긴다.** 헥사고날에서 인바운드 어댑터는 application을 봐야 하고 반대는 안 된다. PRD가 명시한 아키텍처 제약이라 채점자가 보는 지점이다.

| 타입 | 이동 후 |
| --- | --- |
| `ActorContext`, `ActorRole` | `application.actor` |
| `ActorAccessDenied`, `ActorAccessPolicy` | `application.actor` |
| `ActorContextArgumentResolver` | `adapter.in.actor` (그대로) |

"이 요청을 누가 보냈는가"는 유스케이스의 입력이지 HTTP의 개념이 아니다. **헤더에서 꺼내는 해석기만 어댑터다.**

Task 1 변경은 패키지 이동과 import 경로뿐이다. 로직은 안 바뀐다.

**단 "테스트가 그대로 돈다"는 아니다.** `ActorContextArgumentResolverTest`는 `package com.liveclass.settlement.adapter.in.actor`라 `ActorContext`와 `ActorRole`을 **같은 패키지로 쓰고 있어 import가 없다.** 두 타입이 `application.actor`로 가면 그 테스트가 컴파일되지 않는다.

고칠 곳은 셋이다.

| 파일 | 조치 |
| --- | --- |
| `ActorContextArgumentResolver.java` | `application.actor.ActorContext`, `ActorRole` import 추가 |
| `ActorContextArgumentResolverTest.java` | 같은 두 import 추가. 파일은 제자리에 둔다 |

**`WebMvcConfig`는 고치지 않는다.** 실물을 확인했다 — `ActorContextArgumentResolver` 하나만 import하고 `ActorContext`나 `ActorRole`을 직접 참조하지 않는다. 해석기가 `adapter.in.actor`에 남으므로 그 import도 그대로다.

테스트를 옮기지 않는 이유는 그게 해석기를 테스트하기 때문이다. 해석기는 어댑터에 남으므로 테스트도 남는다.

## 3. 접근 예외와 정책

```java
package com.liveclass.settlement.application.actor;

public class ActorAccessDenied extends RuntimeException { ... }

@Component
public class ActorAccessPolicy {
    public void requireSelfOrAdmin(ActorContext actor, String creatorId) {
        if (actor.role() == ActorRole.ADMIN) return;
        if (!actor.actorId().equals(creatorId)) throw new ActorAccessDenied(...);
    }
    public void requireAdmin(ActorContext actor) {
        if (actor.role() != ActorRole.ADMIN) throw new ActorAccessDenied(...);
    }
}
```

**정책 구현이 Task 4에 있는 이유.** `ActorAccessDenied`의 정의와 403 변환이 여기 있으므로 그 예외를 던지는 코드도 같은 자리에 둔다. Task 5에 두면 Task 4가 Task 5를 호출하는데 Task 5는 Task 4의 전역 처리기를 기다리는 순환이 된다.

**어느 엔드포인트에 무엇이 붙는지는 Task 5가 소유한다.** 이 클래스는 판정만 한다. 배치는 역할 매트릭스가 정한다.

`requireAdmin`은 Task 4의 세 엔드포인트 중 두 곳(등록)이 쓰고, Task 5의 운영자 집계가 쓴다.

## 4. 전역 예외 처리기

```java
package com.liveclass.settlement.adapter.in.web;

public record ErrorResponse(String code, String message, int status) { }

@RestControllerAdvice
public class GlobalExceptionHandler {
    // 404 SaleNotFound, CourseNotFound
    // 409 RefundAmountExceeded
    // 403 ActorAccessDenied
    // 400 InvalidSettlementPeriod
    // 400 MethodArgumentNotValidException      ← Bean Validation
    // 400 ResponseStatusException              ← 액터 헤더 (Task 1)
    // 400 HttpMessageNotReadableException     ← 오프셋 없는 시각 등 역직렬화 실패
    // 400 MissingServletRequestParameterException ← from/to 누락
}
```

`code`는 예외 이름을 `UPPER_SNAKE_CASE`로 바꾼 값이다. 프레임워크 예외 넷은 예외 이름이 사용자에게 의미가 없으므로 따로 정한다.

| 예외 | `code` | status |
| --- | --- | ---: |
| `SaleNotFound` | `SALE_NOT_FOUND` | 404 |
| `CourseNotFound` | `COURSE_NOT_FOUND` | 404 |
| `RefundAmountExceeded` | `REFUND_AMOUNT_EXCEEDED` | 409 |
| `ActorAccessDenied` | `ACTOR_ACCESS_DENIED` | 403 |
| `InvalidSettlementPeriod` | `INVALID_SETTLEMENT_PERIOD` | 400 |
| `MethodArgumentNotValidException` | `VALIDATION_FAILED` | 400 |
| `ResponseStatusException` | `INVALID_ACTOR_HEADER` | 예외가 든 값 |
| `HttpMessageNotReadableException` | `MALFORMED_REQUEST` | 400 |
| `MissingServletRequestParameterException` | `MISSING_PARAMETER` | 400 |

**이 표가 코드값의 단일 원본이다.** 4.5와 4.8이 여기를 참조한다. 흩어지면 테스트가 단언할 문자열을 찾으러 문서를 세 개 뒤져야 한다.

**`MethodArgumentNotValidException`을 반드시 잡는다.** 안 잡으면 Spring이 자체 `ProblemDetail` 본문을 내보내 포맷이 갈린다. 여러 필드가 실패하면 첫 번째 위반의 메시지를 쓰고 `code`는 `VALIDATION_FAILED`로 둔다.

**`ResponseStatusException`도 잡는다.** Task 1의 `ActorContextArgumentResolver`가 헤더 오류에 이 예외를 던진다. Task 1 코드는 고치지 않고 여기서 흡수한다. `code`는 `INVALID_ACTOR_HEADER`, `status`는 예외가 들고 있는 값을 그대로 쓴다.

**`MissingServletRequestParameterException`도 잡는다.** `GET /api/creators/{id}/sales`에서 `from`이나 `to`를 빼면 이 예외가 난다. 안 잡으면 "모든 실패가 한 가지 모양"이라는 주장이 거짓이 된다. `code`는 `MISSING_PARAMETER`.

**`Exception`을 잡는 catch-all을 두지 않는다.** `IllegalArgumentException`과 `NullPointerException`은 Task 3의 값 타입 불변식 위반, 즉 우리 코드의 버그다. Spring 기본 500으로 나가게 두어야 스택트레이스가 로그에 남는다. **400으로 싸잡으면 프로그래밍 버그가 사용자 오류로 위장돼 사라진다.** Task 3 명세가 명시적으로 요구한 제약이다.

## 5. 수수료 정책 빈 등록

```java
package com.liveclass.settlement.config;

@Configuration
public class DomainConfig {
    @Bean
    FeePolicy feePolicy(@Value("${settlement.fee.basis-points:2000}") int basisPoints) {
        return new FixedRateFeePolicy(basisPoints);
    }
}
```

**`SettlementCalculator`는 여기서 등록하지 않는다. Task 5가 한다.** Task 4는 정산 계산을 하지 않으므로 쓰지 않는 빈을 만들 이유가 없다. 쓰는 태스크가 등록한다.

### 빈 등록이 도메인 순수성을 깨지 않는 이유

도메인 순수성은 **도메인이 Spring을 아느냐**의 문제다. Spring이 도메인을 아는 것은 상관없다.

```java
// 순수성이 깨지는 방식 -- 하지 않는다
@Component
public record FixedRateFeePolicy(int basisPoints) { }   // 도메인이 Spring에 묶인다

// 이 명세가 택한 방식
// domain/settlement/FixedRateFeePolicy.java  -- 애노테이션 0
// config/DomainConfig.java                   -- 조립만 여기서
```

`@Bean`을 `config`에 두면 의존 방향이 도메인 바깥에서 안쪽을 향한다. 도메인은 Spring의 존재를 모른다.

증거는 테스트다. Task 3의 계산기 테스트 42건이 **Spring 컨텍스트 없이 돈다.** 도메인에 애노테이션이 하나라도 있으면 그게 성립하지 않는다. 구현 후 `domain` 패키지에 `org.springframework` import가 0건인지 확인한다.

**빈으로 만드는 이유는 조립 지점을 한 곳에 모으기 위해서다.** 빈이 없으면 요율 설정 바인딩이 유스케이스마다 반복되고, 요율을 읽는 곳이 흩어져 하나만 고치면 두 API가 다른 요율로 계산한다.

`application.yml`에 `settlement.fee.basis-points: 2000`이 **이미 있다** (Task 2에서 추가). 새로 넣지 않는다. `@Value`의 기본값 `:2000`은 설정이 지워졌을 때의 방어일 뿐이다.

**요율 상수를 도메인에서 가져오지 않는다.** `FixedRateFeePolicy`에는 공개 상수가 없다. 실제 구현은 `record FixedRateFeePolicy(int basisPoints)`와 0~10000 범위 검증뿐이고, `2000`이라는 값은 Task 3의 테스트 픽스처에만 package-private으로 있다. 테스트 소스를 프로덕션이 참조할 수 없다.

설정 프로퍼티로 두면 Task 3 전제 9의 "변경 가능성을 설계에 반영"이 애노테이션 하나로 완성된다. 요율을 바꾸려면 yml 한 줄만 고치면 되고 재컴파일이 없다.

**`FeePolicy`를 Task 4가 등록하는 이유는 설정 바인딩이 여기 있기 때문이다.** Task 4가 Spring 배선이 생기는 첫 Task이고, 요율 프로퍼티를 읽는 지점이 하나여야 한다. `SettlementCalculator`는 그 정책을 주입받을 뿐이므로 쓰는 쪽인 Task 5가 `DomainConfig`에 메서드를 더한다.

## 파일

`domain/settlement/SaleNotFound.java`, `CourseNotFound.java`
(`RefundAmountExceeded`는 4.2가 `domain/sales`에 만든다. 여기서 만들지 않는다)
`src/test/java/.../adapter/in/actor/ActorContextArgumentResolverTest.java` (import 2줄 추가)
`application/actor/ActorContext.java`, `ActorRole.java` (Task 1에서 이동)
`application/actor/ActorAccessDenied.java`, `ActorAccessPolicy.java` (신규)
`adapter/in/actor/ActorContextArgumentResolver.java` (import 경로만 수정)
`adapter/in/web/GlobalExceptionHandler.java`, `ErrorResponse.java`
`config/DomainConfig.java`

테스트는 없다. 4.8이 전부 검증한다.

## 완료 기준

1. 컴파일되고 컨텍스트가 기동한다.
2. 예외 9종이 표대로 매핑된다 (도메인 5 + 검증 + 액터 헤더 + 역직렬화 + 파라미터 누락).
3. `IllegalArgumentException` / `NullPointerException` 핸들러가 **없다.**
4. `@ExceptionHandler(Exception.class)`가 없다.
5. 도메인 예외에 `@ResponseStatus`가 없다.
6. `FeePolicy`가 빈으로 등록된다. `SettlementCalculator`는 등록하지 않는다 — Task 5 소관이다.
6-b. `domain` 패키지에 `org.springframework` import가 0건이다.
7. `application` 패키지가 `adapter`를 import하지 않는다.
8. Task 1의 기존 테스트 4건이 import 수정 후 통과한다.
9. `RefundAmountExceeded`가 저장소 전체에 **하나만** 존재한다 (`domain/sales`).
