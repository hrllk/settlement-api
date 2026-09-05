# Task 6.2 — HTTP 통합 흐름 1건 명세

부모: [`task-6-core-scenario-tests-spec.md`](./task-6-core-scenario-tests-spec.md) · 의존 6.1 · 25분 · 테스트 1

## 흐름

```
1. POST /api/sales                              ADMIN
   { courseId: "course-1", amount: 100000, paidAt: "2025-06-10T10:00:00+09:00" }
   → 201. 응답 본문에서 서버가 생성한 saleId(UUID)를 꺼낸다

2. POST /api/sales/{saleId}/cancellations       ADMIN
   { amount: 40000, cancelledAt: "2025-06-20T10:00:00+09:00" }
   → 201

3. GET /api/creators/creator-1/settlements/2025-06    creator-1 / CREATOR
   → 200. 아래 값을 전부 단언한다
      grossSales  100000    saleCount    1
      refunds      40000    cancelCount  1
      netSales     60000
      fee          12000    payout       48000
```

## 200 확인으로 끝내지 않는다

이 테스트의 존재 이유는 **배선 검증**이다. 계산 정확성은 Task 3이 단위로 잠갔다.

상태 코드만 보면 배선이 끊겨 전 항목 0이 나와도 통과한다. 실제 금액을 단언해야 액터 헤더 해석 → 컨트롤러 → 유스케이스 → 커맨드 포트 → JPA → 조회 포트 → 계산기 → 응답 DTO의 사슬 중 하나라도 끊긴 것이 드러난다.

`fee` 12,000원은 특히 의미가 있다. 순 판매액 60,000의 20%다. 계산기가 안 불렸으면 0이 나오고, `FeePolicy` 빈이 등록되지 않았으면 컨텍스트가 아예 안 뜬다.

## 2단계가 1단계 응답에 의존한다

`saleId`는 서버가 만드는 UUID다. 테스트가 미리 알 수 없으므로 1단계 응답 본문에서 꺼내야 한다.

Task 4가 생성 ID를 응답에 담지 않으면 **이 테스트를 쓸 수 없다.** `RegisterSaleUseCase.register(...)`가 ID를 반환하고 `SaleResponse`가 `saleId` 필드로 담는다. 둘 다 구현돼 있다.

## 2025-06을 쓰는 이유

시드는 2025년 1~3월만 쓴다. 통합 테스트는 **시드가 건드리지 않는 달**에 데이터를 넣는다.

`@Transactional`로 롤백도 건다. 둘 다 건다.

롤백만 믿지 않는 이유는 `DB_CLOSE_DELAY=-1` 때문이다. 인메모리 DB가 JVM 수명 내내 살아 있어서 롤백이 한 번이라도 새면 creator-1의 3월 기대값 120,000원이 조용히 틀어진다. 그리고 **깨지는 것은 이 파일이 아니라 다른 파일의 시드 기반 단언**이다. 원인을 찾기 가장 어려운 종류의 실패다.

월을 분리하면 롤백이 실패해도 아무것도 안 깨진다. Task 4.8이 같은 규칙을 쓴다.

`course-1`은 creator-1 소유다. 3단계에서 creator-1로 조회하는 것과 맞는다.

## 액터를 단계마다 바꾼다

1·2단계는 ADMIN, 3단계는 creator-1 / CREATOR다. 등록은 운영자만 할 수 있고 정산은 본인이 본다는 역할 매트릭스가 한 흐름 안에서 확인된다.

같은 헤더로 전부 돌리면 역할 전환이 검증되지 않는다.

## 환경

`SettlementE2ETest` — `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`.

`MockMvc`를 쓴다. `TestRestTemplate` + `RANDOM_PORT`를 쓰면 서버가 별도 스레드에서 돌아 `@Transactional` 롤백이 안 걸린다. 그러면 2025-06 데이터가 DB에 남는다.

## 파일

`src/test/java/.../SettlementE2ETest.java`.

## 완료 기준

1. 세 단계가 한 테스트 메서드 안에서 순서대로 돈다.
2. 2단계가 1단계 응답의 `saleId`를 쓴다. 하드코딩하지 않는다.
3. 3단계가 7개 필드를 전부 단언한다.
4. 2025-06을 쓴다.
5. `@Transactional`이 붙어 있다.
6. 이 테스트를 돌린 뒤 Task 2·3·4·5의 시드 기반 단언이 계속 통과한다.
