# Task 7 — README와 제출 전 점검 명세

## 배경

평가자가 5분 안에 실행하고, 설계 판단의 근거를 코드를 읽지 않고도 알 수 있게 한다.

**이 프로젝트에는 문서가 없으면 버그로 읽히는 동작이 하나 있다.** 운영자 기간 집계가 월별 정산의 합이 아니다. creator-2의 1~3월이 운영자 조회에서 48,000원, 월별 조회 세 번의 합으로는 36,000원이다. 평가자가 두 API를 돌리면 반드시 본다. 정책 자체는 Task 3에서 확정했다. **최대 리스크는 정책이 아니라 미문서화다.**

## 현재 상태

- 계획: `docs/plan/task-7-readme-submission-plan.md`
- Task 1~6 완료. 테스트 93건
- `docs/coverage-audit.md` (Task 6.4)
- README 없음

## 서브태스크

| ID | 명세 | 의존 | 예상 |
| --- | --- | --- | ---: |
| 7.1 | [실행·테스트 명령과 요구 환경](./task-7-1-run-commands-spec.md) | Task 1 | 10분 |
| 7.2 | [API 명세와 curl 예시](./task-7-2-api-doc-spec.md) | Task 5 | 20분 |
| 7.3 | [데이터 모델·ERD·초기 데이터 표](./task-7-3-data-model-spec.md) | Task 2 | 15분 |
| 7.4 | [KST 경계와 이중 집계 기준](./task-7-4-kst-boundary-spec.md) | Task 3 | 15분 |
| 7.5 | [가정과 이탈 근거 21항](./task-7-5-assumptions-spec.md) | Task 6 | 30분 |
| 7.6 | [미구현 범위와 확장 경로](./task-7-6-out-of-scope-spec.md) | Task 6 | 10분 |
| 7.7 | [AI 활용 내역](./task-7-7-ai-usage-spec.md) | — | 10분 |
| 7.8 | [제출 전 클린 점검](./task-7-8-clean-check-spec.md) | 7.1~7.7 | 20분 |

약 130분.

## 의존성은 서브태스크마다 다르다

`tasks.json`의 Task 7 의존성 `[6]`은 **7.5~7.8에만 걸린다.** 7.1~7.4는 Task 5가 끝나면 쓸 수 있다.

Task 7이 마지막이라 시간에 쫓기면 가장 먼저 잘리는데, 원본 과제가 명시적으로 요구하는 항목이 여기 몰려 있다. **7.1~7.4를 Task 6 진행 중에 미리 써두는 편이 안전하다.**

## 결과물 구조

`README.md` 하나에 전부 담는다. 절 순서는 서브태스크 번호와 같다.

```
실행 (7.1)
API (7.2)
데이터 모델 (7.3)
KST 경계와 이중 집계 (7.4)
가정과 이탈 근거 (7.5)
미구현 범위와 확장 경로 (7.6)
AI 활용 내역 (7.7)
```

문서를 여러 파일로 쪼개지 않는다. 평가자가 링크를 따라다니게 하면 안 읽는다. `docs/` 아래 계획·명세 문서는 링크로만 언급한다.

## 제외 범위

- 코드 변경 — 점검에서 결함이 나오면 해당 Task로 되돌린다
- Docker, CI 설정, 배포 문서 — 과제 명시적 제외
- API 문서 자동 생성 (Swagger 등) — 엔드포인트 5개에 도구를 붙일 이유가 없다

## 파일

`README.md`. 그리고 7.8이 점검 결과를 `docs/coverage-audit.md`에 덧붙인다.

## 완료 기준

1. README에 7개 절이 전부 있다.
2. 가정 21항이 빠짐없이 기술된다.
3. 운영자 집계가 월별 합이 아니라는 설명이 가정 목록 최상단에 있다.
4. 클린 클론에서 `./gradlew clean test`가 통과한다.
5. README의 모든 curl 예시가 실제 응답과 일치한다.
6. README의 모든 수치가 테스트 기대값과 일치한다.

## 롤백 · 소요

문서만 추가한다. 약 130분.

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

**테스트 93건 예상** — Task 1: 4, Task 2: 11, Task 3: 42, Task 4: 22, Task 5: 12, Task 6: 2. Task 1~5는 실측이고 6은 명세가 약속한 수다.

**VERDICT:** CEO + ENG + OUTSIDE VOICE CLEARED — 구현 착수 가능.

NO UNRESOLVED DECISIONS
