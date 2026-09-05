# Task 6.1 — 시드와 픽스처 분리 확인 명세

부모: [`task-6-core-scenario-tests-spec.md`](./task-6-core-scenario-tests-spec.md) · 의존 없음 · 5분

## Task 3의 픽스처를 import하지 않는다

초기 계획은 Task 6이 "공용 픽스처를 만든다"고 했다가, 검토에서 "Task 3이 만들고 Task 6이 재사용한다"로 바뀌었다. **둘 다 틀렸다.**

Task 3의 실제 구현은 이렇다.

```java
// domain/settlement/SettlementFixtures.java  (테스트 소스)
final class SettlementFixtures {                     // package-private
    static final int PLATFORM_FEE_BP = 2_000;        // package-private
    static final SaleData SALE_1 = sale("sale-1", CREATOR_1, 50_000, "2025-03-05T10:00:00+09:00");
```

클래스도 멤버도 package-private이고 `com.liveclass.settlement.domain.settlement` 패키지에 있다. Task 6의 E2E 테스트는 다른 패키지라 import할 수 없다.

**가시성을 열어달라고 요청하지 않는다.** 두 가지 이유다. Task 3은 다른 세션이 지금 구현 중이라 요청 자체가 결합이다. 그리고 **Task 6은 그 상수가 필요 없다** — 6.2는 시드를 안 건드리고 2025-06에 자기 숫자(100,000 / 40,000)를 넣는다.

## 하는 일

상수가 세 군데에 나뉘어 있다. 각각이 자기 자리에 있는지 확인한다.

| 무엇 | 어디 | 소유 | 형식 |
| --- | --- | --- | --- |
| 시드 17행 | `src/main/resources/data.sql` | Task 2.6 | SQL 리터럴 |
| 계산기 픽스처 | `domain/settlement/SettlementFixtures.java` | Task 3 | Java 상수 (package-private) |
| E2E 입력 | `SettlementE2ETest` 안 | Task 6.2 | 지역 상수, 2025-06 |

**세 집합이 서로 겹치지 않아야 한다.** 겹치면 한쪽을 바꿀 때 다른 쪽이 조용히 깨진다.

## 시드와 픽스처를 섞지 않는다

두 가지가 있고 용도가 다르다.

| | 무엇 | 어디 | 누가 |
| --- | --- | --- | --- |
| 시드 | `data.sql` 17행 | `src/main/resources` | Task 2.6 |
| 픽스처 | 단위 테스트용 입력 | 테스트 소스 | Task 3 |

Task 3의 추가 케이스(순 판매액 33,333원 수수료 버림, 동일 판매 다수 부분 취소)는 **픽스처에만 있다.** 시드에 넣으면 creator-1의 2025-03 기대값 120,000원이 깨진다. 그 값은 원본 과제가 명시한 숫자라 바꿀 수 없다.

반대로 시드의 17행은 픽스처가 아니다. `data.sql`이 유일한 정의이고 Java 상수로 복제하지 않는다.

## 불가피한 중복 하나

`sale-5`의 값은 `data.sql`(SQL 리터럴)과 Task 3 픽스처(Java 상수) 양쪽에 있다. 형식이 달라 한쪽을 다른 쪽에서 생성할 수 없다.

시드를 바꾸면 두 곳을 **둘 다** 고쳐야 한다. 6.4 커버리지 감사가 이 대응을 점검 항목으로 갖는다.

## 파일

새 파일 없음. 확인만 한다. Task 3의 소스를 수정하지 않는다.

## 완료 기준

1. 세 상수 집합의 위치가 확인됐다.
2. Task 6이 Task 3의 픽스처를 import하지 않는다.
3. 6.2가 시드가 쓰지 않는 2025-06 값을 자기 안에 갖는다.
4. Task 3의 추가 케이스가 `data.sql`에 없다.
