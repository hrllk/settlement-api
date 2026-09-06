# Task 6 — 핵심 판단 시나리오 검증 명세

## 배경

Task 3·4·5가 각자 잠그지 못하는 것만 검증한다. 이미 단언된 숫자를 다시 단언하지 않는다.

### Spring Boot 4 임포트 경로

Boot 4가 테스트 자동설정을 모듈별로 쪼갰다. **Boot 3 임포트를 쓰면 컴파일이 안 된다.**

| 애노테이션 | 패키지 |
| --- | --- |
| `@SpringBootTest` | `org.springframework.boot.test.context` (그대로) |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.jdbc.test.autoconfigure` |
| `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure` |

`spring-boot-test-autoconfigure` jar에는 이 셋이 없다. Task 1이 넣은 `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`가 각각 가져온다.

## 소유권 원칙

**규칙을 소유한 Task가 그 규칙의 테스트도 소유한다.** 그래야 각 Task가 혼자 완결되고 머지 순서를 바꿔도 검증에 구멍이 안 생긴다.

| 검증 대상 | 소유 | 이유 |
| --- | --- | --- |
| 월별 정산 6행, 환불 상태 4행 | Task 3 | 계산기 단위. Spring·H2 없이 돈다 |
| 잘못된 기간 거부, 경계 3방향, 수수료 버림 | Task 3 | `SettlementPeriod` 도메인 규칙 |
| 누적 초과 환불, 오류 상태 코드 5종 | Task 4 | 등록 규칙과 전역 처리기를 소유한다 |
| `sale-5` 환불 상태 `FULL` | Task 4 | 판매 목록 엔드포인트를 소유한다 |
| 운영자 목록 조립과 전체 합계, 접근 경계, opt-in 가드 | Task 5 | 유스케이스와 역할 매트릭스를 소유한다 |
| **HTTP 통합 흐름 1건** | **Task 6** | 어느 Task도 혼자서는 배선 전체를 못 본다 |
| **커버리지 감사** | **Task 6** | 앞 Task가 약속을 안 지켜도 아무도 모른다 |

초기 계획은 누적 초과 환불을 Task 4·6이, 운영자 합계를 Task 5·6이 둘 다 소유하고 있었다. 뿌리는 Task 3 계획서의 자기모순이다 — 완료 기준은 "전체 합계는 Task 5가 단언한다"고 하는데 항목 19는 "Task 6이 추가한다"고 한다. 전자를 택했다.

PRD가 요구하는 "3월 계산, 부분 환불, 월 경계 취소, 판매 없는 월"은 **저장소 전체 기준의 커버리지 요구**이지 Task별 요구가 아니다. Task 3이 계산기 단위로 잠그면 충족된다. 같은 숫자를 두 파일에 박으면 시드를 바꿀 때 한쪽만 고치는 사고가 난다.

"각자 자기 것을 테스트한다"의 유일한 약점은 **한 Task가 약속을 안 지켜도 아무도 모른다**는 것이다. 6.4 커버리지 감사가 그 틈을 맡는다.

## 현재 상태

- 계획: `docs/plan/task-6-core-scenario-tests-plan.md`
- Task 3: 계산기 단위 42건
- Task 4: API 16건 + 애그리게이트 6건 = 22건
- Task 5: 정산·접근·가드 12건
- Task 1·2: 컨텍스트 4건, 어댑터·시드 11건

Task 6까지 실측 **94건**.

## 서브태스크

| ID | 명세 | 의존 | 예상 | 테스트 |
| --- | --- | --- | ---: | ---: |
| 6.1 | [시드와 픽스처 분리 확인](./task-6-1-fixture-reuse-spec.md) | Task 2·3 | 5분 | 0 |
| 6.2 | [HTTP 통합 흐름 1건](./task-6-2-http-integration-spec.md) | 6.1 | 25분 | 1 |
| 6.3 | [결정성 점검](./task-6-3-determinism-spec.md) | 6.2 | 10분 | 1 |
| 6.4 | [커버리지 감사](./task-6-4-coverage-audit-spec.md) | 6.2 | 15분 | 0 |

약 55분, 새 테스트 3건.

**표의 의존은 부모 Task 의존(`[4,5]`)에 더해지는 것이다.** 6.1은 Task 2의 `data.sql`과 Task 3의 픽스처 위치를 확인하므로 둘 다 존재해야 한다.

Task 6이 작은 것은 의도다. 앞 Task들이 자기 몫을 다 했다면 남는 것이 이만큼이다.

## 제외 범위

위 소유권 표에서 Task 3·4·5로 표시된 전부.

## 파일

| 경로 | 서브태스크 |
| --- | --- |
| `src/test/java/.../SettlementE2ETest.java` | 6.2 |
| `docs/coverage-audit.md` | 6.4 |

6.1과 6.3은 새 파일을 만들지 않는다.

## 완료 기준

1. `./gradlew test`가 통과한다.
2. HTTP 통합 1건이 금액을 단언한다. 200 확인으로 끝내지 않는다.
3. 전체 스위트를 두 번 연속 돌려 같은 결과가 나온다.
4. 통합 테스트를 돌린 뒤에도 시드 기반 단언이 전부 통과한다.
5. 어떤 테스트도 `now()`를 호출하지 않는다.
6. 커버리지 감사에서 소유권 표의 모든 항목에 실제 테스트가 대응된다.

## 롤백 · 소요

테스트와 문서만 추가한다. 약 55분.

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
| --- | --- | --- | ---: | --- | --- |
| CEO Review | `/plan-ceo-review` | 범위와 전략 | 1 | CLEAR | 계획 단계에서 HOLD_SCOPE 확정, 25건 반영 |
| Eng Review | `/plan-eng-review` | 아키텍처와 테스트 | 1 | CLEAR | 4건 적발, 전부 반영 |
| Outside Voice | Codex (독립) | 교차 검증 | 1 | CLEAR | 블로커 6 + 중간 3, 전부 검증 후 반영 |
| Design Review | 해당 없음 | UI/UX | 0 | SKIPPED | 백엔드 전용 |
| DX Review | 해당 없음 | 로컬 실행 | 0 | SKIPPED | Task 1에서 완료 |

**세트 검수 결과.** 38건을 한 세트로 봤다. 따로 봤으면 안 잡혔을 것이 대부분이다.

**엔지니어링 검수 4건**
1. 헥사고날 방향 역전. 유스케이스가 `ActorContext`를 받으면서 `application → adapter.in` 의존이 생겼다. 액터 타입을 `application.actor`로 옮기고 해석기만 어댑터에 남겼다.
2. 판매 단건 조회가 `SaleData`를 돌려주면 `creatorId`를 채울 방법이 없어 NPE. 단건 경로는 `Sale` 애그리게이트로, 목록 경로는 전용 `SaleRecord`로 갈랐다.
3. 5.2와 5.3이 조회·계산 세 줄을 복제. `SettlementQuery`로 뺐다.
4. 테스트 공백 2건(전액 환불 경계, CREATOR 등록 403)과 `NoCurrentTimeUsageTest` 자기 매칭 버그.

**Outside Voice 6 + 3 (전부 실물로 검증)**
1. `FixedRateFeePolicy.PLATFORM_DEFAULT_BP`가 없다. 검수 도중 Task 3 세션이 지웠다. 설정 프로퍼티 주입으로 바꿨다.
2. `saveCancel`이 `void`인데 `CancelResponse`가 `cancelId`를 요구.
3. Task 3의 `SaleData`에 `courseId`가 없는데 `SaleItem`이 요구. 판매 목록을 `SalesQueryPort`의 읽기 모델로 분리했다.
4. 우산 파일 목록과 4.1의 `ActorAccessPolicy` 경로 불일치.
5. Task 3의 `SettlementFixtures`가 package-private이라 Task 6이 import 불가. Task 6은 애초에 그게 필요 없어 분리 확인으로 바꿨다.
6. 서브태스크 의존성이 교차 Task를 안 적어 DAG가 거짓.
7. **Boot 4 테스트 애노테이션 패키지 이동.** `spring-boot-test-autoconfigure` jar에 `@DataJpaTest`·`@AutoConfigureTestDatabase`·`@AutoConfigureMockMvc`가 없다. jar를 열어 확인했다. Boot 3 임포트를 쓰면 컴파일이 안 된다.
8. 목록 쿼리와 크리에이터 목록에 정렬 계약이 없어 응답 순서가 비결정적.
9. `from`/`to` 누락 시 `MissingServletRequestParameterException`이 포맷 통일을 깬다.

**CROSS-MODEL:** Codex가 낸 9건 중 반박한 것은 없다. 전부 실제 코드와 jar를 열어 확인했다. 1번은 제가 처음 읽었을 때 존재했으나 검수 도중 다른 세션이 지운 것이라, 두 번째 확인이 없었으면 놓쳤을 지점이다. 7번은 Boot 4 모듈 분할을 몰랐으면 구현자가 컴파일 오류로 시간을 태웠을 항목이다.

**테스트 94건 실측** — Task 1: 4, Task 2: 11, Task 3: 42, Task 4: 22, Task 5: 12, Task 6: 3.

**VERDICT:** CEO + ENG + OUTSIDE VOICE CLEARED — 구현 착수 가능.

NO UNRESOLVED DECISIONS
