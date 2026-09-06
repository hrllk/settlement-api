# Task 5·6·7 명세 검수 — 적발과 미결 논의

Task 1~4 구현이 끝난 뒤 남은 18개 서브태스크 명세를 실제 코드와 대조했다. 명세는 구현 전에 쓰였으므로 **코드가 기준이고 명세가 따라간다.**

검수 시점 실측: `./gradlew test` **79건 통과**, 도메인의 Spring/JPA/Lombok 임포트 0건.

---

## 1. 고친 것 — 사실 오류 (판단 불필요)

| # | 어디 | 무엇이 틀렸나 | 확인 방법 |
| --- | --- | --- | --- |
| 1 | 5·6·7 부모 스펙, 7.1, 6.4 | 테스트 건수 78 / Task 2:9 / Task 4:11 / 합계 90 | 실측 79건. Task 1:4, 2:11, 3:42, 4:22 |
| 2 | 5.3 | "어댑터에서 `creatorId` 오름차순으로 정렬한다" | **Task 2가 이미 `Sort.by("id")`로 함.** 지시 → 확인으로 |
| 3 | 5.3 ↔ 5.5 | `AdminSettlement(period, ...)` vs "요청 문자열을 그대로 담는다" | 충돌. `period` 제거 |
| 4 | 6.2 | `saveSale`이 ID를 반환하도록 보장한다 | 그런 메서드 없음. `RegisterSaleUseCase.register()`가 반환 |
| 5 | 7.2 | 오류 `code` **8종** | 표는 9행. 9종이 맞다 |
| 6 | 7.2 | `detail` 예시가 한국어 창작 문자열 | 실제는 `refund exceeds sale amount: saleId=...` |
| 7 | 7.5 | 커버리지 감사 "22행" | 26행 (Task 4 신규 4행 추가) |
| 8 | task-4 부모 | 새 테스트 21건 | 22건 (4.8이 15 → 16) |

## 2. 고친 것 — Task 4 구현이 명세를 앞질렀다

**2-a. 프레임워크 실패에는 `code`도 `type`도 없다.**

7.8 항목 7이 "오류 응답 **전부** RFC 9457 + `code`"라고 쓰는데 사실이 아니다. 실측:

```
PATCH /api/sales  →  405  application/problem+json
                      {"detail":"Method 'PATCH' is not supported.",
                       "instance":"/api/sales","status":405,"title":"Method Not Allowed"}
```

`code`와 `type`이 없다. 붙이려면 Spring의 프레임워크 예외 목록을 우리가 복제해야 하고 그 목록은 버전마다 바뀐다. **우리가 이름 붙인 실패에만 `code`를 준다**로 확정하고 7.2·7.8에 반영했다.

`spring.mvc.problemdetails.enabled`를 끄면 이 응답은 본문이 통째로 빈다. 7.8이 그 확인 curl을 갖는다.

**2-b. `type`은 명시해야 한다.** `forStatusAndDetail`의 기본 `about:blank`는 직렬화에서 생략된다. 4.1에 이미 반영됨.

**2-c. 6.3의 `@Order` 검사가 오탐을 낸다.** `GlobalExceptionHandler`에 `@Order(Ordered.HIGHEST_PRECEDENCE)`가 붙었다. 어드바이스 우선순위이지 테스트 순서가 아니다. 검사 범위를 `src/test`로 한정했다.

**2-d. 예외 두 개가 패키지를 옮겼다.** `SaleNotFoundException`·`CourseNotFoundException`가 `domain.settlement` → `domain.sales`. 5.x가 참조하지 않아 영향 없음. 4.1·4 부모는 반영 완료.

**2-e. `@Transactional`은 Spring 컨텍스트를 늘리지 않는다.** 4.8 명세가 "애노테이션 세 줄을 다르게 쓰면 컨텍스트가 또 하나 늘어난다"고 썼는데 사실이 아니다. 세 줄짜리와 두 줄짜리를 나란히 띄워 `ApplicationContext` 식별자를 찍었다.

```
CTX-A id=883862737   @SpringBootTest @AutoConfigureMockMvc @Transactional
CTX-B id=883862737   @SpringBootTest @AutoConfigureMockMvc
```

같은 객체다. 캐시 키를 정하는 것은 `@SpringBootTest`와 `@AutoConfigureMockMvc`뿐이고, `@Transactional`은 컨텍스트 설정이 아니라 `TransactionalTestExecutionListener`가 메서드마다 적용하는 것이다.

**Task 5에 미치는 영향:** 5.2·5.6의 `SettlementControllerTest`는 조회만 하므로 `@Transactional` 없이 두 줄만 써도 된다. 명세 원안이 맞았고, 4.8의 경고 문구가 틀렸다. 4.8을 고쳤다.

## 3. 고친 것 — 문서 공백

**`from`/`to`가 종료일을 포함한다는 설명이 어디에도 없었다.**

```java
ofDateRange("2025-03-01", "2025-03-31")
  → [2025-03-01 00:00 KST, 2025-04-01 00:00 KST)     // 종료일 + 1일
```

`to`에 적은 날은 **하루 전체가 들어간다.** 7.4가 월 구간의 반열림만 설명하고 일자 구간은 다루지 않았다. `SettlementPeriod.toExclusive()`라는 이름이 오히려 "31일은 빠진다"로 오해하게 만든다.

시드에 3월 31일 데이터가 없어 `to=03-31`과 `to=04-01`이 **같은 168,000**을 낸다. 7.2·7.4에 반영했다.

**정정:** 처음엔 "어떤 테스트도 이 규칙을 구분하지 못한다"고 적었는데 틀렸다. **하루짜리 구간이면 구분된다.** `from=to=2025-03-05`는 sale-1(50,000)을 잡고 `to=2025-03-04`는 놓친다. Task 5 구현에서 이 케이스를 테스트로 추가했다.

## 3-b. 미리 확인해 둔 것 (Task 7.8 선점)

7.8 체크리스트 중 지금 확인 가능한 것을 미리 돌렸다. Task 7이 다시 찾을 필요 없다.

| 7.8 항목 | 결과 |
| --- | --- |
| 2. `gradlew` 100755, wrapper jar 추적 | ✅ `100755 gradlew`, `100644 gradle-wrapper.jar` |
| 5. 빌드 산출물·`.gradle`·`.claude` 미추적 | ✅ `git ls-files` 결과 0건 |
| 6. 수치 대조 | ✅ 5절 검산표 |
| 7. 오류 포맷 | ✅ Task 4 검수에서 실측 |

남는 것은 클린 클론(1), 기동(3), curl 실전(4), 감사표(8)다. 전부 Task 5·6이 끝나야 할 수 있다.

**7.3의 인덱스·ERD 주장도 대조했다.** `idx_sales_course_paid (course_id, paid_at)`,
`idx_cancels_sale_cancelled (sale_id, cancelled_at)` 실제와 일치한다.

**6.1의 근거도 대조했다.** `SettlementFixtures`는 `final class`(package-private)이고
`PLATFORM_FEE_BP`도 package-private이다. Task 6이 import 못 한다는 전제가 맞다.

---

## 4. 논의가 필요한 것

### D1. 취소 동시성 — 문서로 남길 것인가, 막을 것인가 ⚠️ 진행 중

7.5 가정 11이 "동시성을 보장하지 않는다"로 남기기로 돼 있다. 그런데 **다른 세션이 지금 이걸 실증하고 있다** (`src/test/.../scratch/RefundRaceProbeTest.java`, `SaleRepositoryJpaAdapter`·`SaleJpaRepository` 수정 중).

sale-4(80,000, 기존 취소 30,000)에 40,000짜리 취소 두 건을 동시에 넣으면 각각은 잔여 50,000 이내라 통과하고 합계 80,000을 넘는다.

| 안 | 내용 | 비용 |
| --- | --- | --- |
| **A. 문서화만 (현행)** | 가정 11에 실측 결과를 근거로 붙인다 | 0분. "알고 남겼다"가 증명된다 |
| B. `@Version` 낙관적 락 | `SaleEntity`에 필드 하나 + 409 매핑 | 20~30분. 재시도 정책 질문이 따라온다 |
| C. `SELECT ... FOR UPDATE` | 조회에 비관적 락 | 20분. H2에서 동작 확인 필요 |

**의견: A + 실측 근거.** 3시간 예산 과제에서 "이 결함을 알고, 재현했고, 이런 이유로 안 막았다"가 조용히 막은 것보다 점수가 높다. 다만 이건 **다른 세션의 판단 영역**이라 여기서 확정하지 않는다. 결론이 나오면 가정 11 문구를 그쪽 결과에 맞춘다.

### D2. GSTACK REVIEW REPORT 블록이 세 스펙에 토씨까지 같다

`task-5`·`task-6`·`task-7` 부모 스펙 하단에 동일한 리포트가 복제돼 있다. 실제로는 셋을 한 세트로 본 **검수 1회**의 결과다. 각 Task가 개별 검수를 받은 것처럼 읽힌다.

내부 문서라 평가 대상은 아니지만, `docs/`를 링크로 언급하기로 했으므로 평가자가 열어볼 수는 있다.

| 안 | 내용 |
| --- | --- |
| **A. 한 곳으로 모은다** | `docs/spec/review-report.md` 하나, 세 스펙은 링크 |
| B. 그대로 둔다 | 중복이지만 각 문서가 자족적 |

**의견: A.** 방금 테스트 건수를 고치느라 같은 문장을 세 번 고쳤다. 다음에 또 그런다.

번호 매김도 어긋나 있다. "Outside Voice 6 + 3"인데 목록은 1~9 연번이라 어디까지가 블로커인지 안 보인다.

### D3. 감사표를 README에 통째로 실을 것인가

7.5 가정 21이 `docs/coverage-audit.md` 26행을 README에 그대로 옮기라고 한다. README가 이미 7개 절이고 가정만 21항이다.

| 안 | 내용 |
| --- | --- |
| A. 통째로 싣는다 (현행) | 평가자가 링크를 안 따라간다는 전제 |
| **B. 요약 + 링크** | Task별 건수 표만 싣고 26행은 링크 |

**의견: B.** "문서를 여러 파일로 쪼개지 않는다"는 원칙과 충돌하지만, 26행 표는 평가자가 읽는 문서가 아니라 **우리가 빠뜨림을 잡는 도구**다. Task별 건수 표(6줄)만 있으면 커버리지 주장은 성립한다.

### D4. Task 5 구현 착수 전 워크트리 정리

`task5/settlement-query-api` 워크트리가 `ffef886`(Task 4 머지 전)에 있다. 그대로 시작하면 `SettlementCalculator` 빈이 없어 컨텍스트가 안 뜬다. task6·task7도 같다.

**Task 5 착수 전 세 워크트리를 `main`(현재 `a92240b`) 기준으로 맞춰야 한다.**

---

## 5. 구현 착수 판정

| Task | 상태 | 근거 |
| --- | --- | --- |
| **5** | 착수 가능 | 6개 서브태스크 명세가 실제 API 시그니처와 일치. `ofYearMonth`/`ofDateRange`/`calculate` 확인 완료 |
| **6** | 5 완료 후 | 6.2가 Task 5의 정산 엔드포인트를 호출한다 |
| **7** | 5·6 완료 후 | 7.1~7.4는 5 직후 착수 가능 (7 부모 스펙이 이미 그렇게 적음) |

**검산 완료 — 명세의 모든 금액이 시드에서 재현된다.**

| 값 | 명세 | 검산 |
| --- | ---: | ---: |
| creator-1 2025-03 payout | 120,000 | 120,000 ✅ |
| 운영자 2025-03 totalPayout | 168,000 | 168,000 ✅ |
| 운영자 2025-01~03 totalPayout | 264,000 | 264,000 ✅ |
| creator-2 월별 합 (대조군) | 36,000 | 48,000 + (−60,000) + 48,000 = 36,000 ✅ |
| 전체 월별 합 (대조군) | 252,000 | 252,000 ✅ |

**5.3 테스트 2번이 유일한 회귀 방어선이라는 주장도 검산으로 확인했다.** 2025-03만 보면 단일 구간과 월별 합산이 둘 다 168,000이다. 3월에 음수 월이 없기 때문이다. 1~3월 구간만이 264,000과 252,000을 가른다.
