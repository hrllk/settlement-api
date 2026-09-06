# Task 7.8 — 제출 전 클린 점검 명세

부모: [`task-7-readme-submission-spec.md`](./task-7-readme-submission-spec.md) · 의존 7.1~7.7 · 20분

로컬에서 되는 것과 평가자 환경에서 되는 것은 다르다. 이 절차가 그 차이를 잡는다.

## 절차

### 1. 클린 클론

```bash
cd $(mktemp -d)
git clone <repo> check && cd check
./gradlew clean test
```

**작업 디렉터리가 아니라 새로 클론한 곳에서 돌린다.** 커밋 안 된 파일에 의존하면 여기서 드러난다.

Task 1에서 실제로 겪은 문제가 있다. `.gitignore`의 `out/` 패턴이 `adapter/out` 소스 패키지를 통째로 삼켰다. `git add -n`으로 확인해서 잡았지만, 못 잡았으면 클론한 저장소에 그 패키지가 없어 컴파일이 실패했을 것이다.

### 2. Wrapper 확인

```bash
git ls-files -s gradlew gradle/wrapper/gradle-wrapper.jar
```

`gradlew`가 `100755`, `gradle-wrapper.jar`가 추적 상태여야 한다.

많은 전역 `.gitignore`가 `*.jar`를 제외한다. jar가 빠지면 `./gradlew`가 첫 줄에서 죽고 평가자는 프로젝트를 못 띄운다. 실행 권한이 빠지면 `Permission denied`가 난다.

### 3. 기동

```bash
./gradlew bootRun
```

로그에 예외가 없어야 한다. `Started SettlementApplication`과 `H2 console available at '/h2-console'`을 확인한다.

`data.sql` 실행 오류가 여기서 드러난다. 기동은 됐는데 시드가 안 들어간 경우도 있으므로 H2 콘솔로 행 수를 확인한다.

### 4. curl 예시 실전 검증

README의 **모든** curl을 실제로 실행하고 응답을 문서와 대조한다. 성공 3개, 오류 2개 이상.

문서의 응답과 실제가 다르면 문서가 감점 요인이 된다. 평가자가 가장 먼저 하는 일이 복사·붙여넣기다.

### 5. git 청결

```bash
git status --short          # 비어 있어야 한다
git ls-files | grep -E '^(build|\.gradle|\.claude)/'   # 결과 없어야 한다
```

빌드 산출물, `.gradle/`, `.claude/` 워크트리가 추적되면 안 된다.

### 6. 수치 대조

README에 적힌 모든 금액을 테스트 기대값과 대조한다.

| 값 | 나오는 곳 |
| --- | --- |
| creator-1 2025-03 → 120,000 | 7.3 표, 7.2 curl 응답, Task 3 테스트 |
| 운영자 2025-03 → 168,000 | 7.2 curl 응답, Task 5 테스트 |
| 운영자 2025-01~03 → 264,000 | 7.5 가정 1, Task 5 테스트 |
| 월별 합산 → 252,000 | 7.5 가정 1 |
| 시드 17행 | 7.3 표, `data.sql`, Task 2 테스트 |

README와 테스트가 갈리면 어느 쪽이 맞는지 평가자가 알 수 없다.

### 7. 오류 포맷 확인

일부러 실패시켜 응답 모양을 본다.

```bash
# -i 로 헤더까지 본다. -s 만 쓰면 Content-Type 을 확인할 수 없다.
curl -si localhost:8080/api/creators/creator-1/settlements/2025-13 \
  -H 'X-Actor-Id: creator-1' -H 'X-Actor-Role: CREATOR'
curl -si localhost:8080/api/creators/creator-1/settlements/2025-03   # 헤더 없이
```

**둘 다 `Content-Type: application/problem+json`이고 `type`·`title`·`status`·`detail`·`instance`·`code`를 가져야 한다.** `type`이 없으면 `setType`을 빠뜨린 것이다 — 기본값 `about:blank`는 직렬화에서 생략된다. `code`가 없으면 Task 4.1의 처리기를 거치지 않고 Spring 기본 처리로 샌 것이다.

프레임워크 실패도 한 번 본다.

```bash
curl -si -X PATCH localhost:8080/api/sales \
  -H 'X-Actor-Id: admin-1' -H 'X-Actor-Role: ADMIN'
```

**405이고 `application/problem+json`이되 `code`와 `type`은 없다.** 그게 정상이다. 본문이 통째로 비었으면 `spring.mvc.problemdetails.enabled`가 꺼진 것이다.

### 8. 커버리지 감사 반영

Task 6.4의 `docs/coverage-audit.md`가 채워졌는지, 빈 행이 없는지 확인하고 README 가정 21로 옮긴다.

## 체크리스트

| # | 항목 | 통과 |
| ---: | --- | --- |
| 1 | 클린 클론에서 `./gradlew clean test` | |
| 2 | `gradlew` 100755, wrapper jar 추적 | |
| 3 | `bootRun` 기동, 예외 없음, 시드 17행 | |
| 4 | README curl 전부 실행, 응답 일치 | |
| 5 | `git status` 비어 있음, 산출물 미추적 | |
| 6 | README 수치 = 테스트 기대값 | |
| 7 | 오류 전부 problem+json. 우리 예외는 `code`까지, 프레임워크 실패는 `code` 없이 | |
| 8 | 커버리지 감사 빈 행 없음 | |

결과를 `docs/coverage-audit.md` 하단에 덧붙인다.

## 결함이 나오면

**여기서 코드를 고치지 않는다.** 해당 Task로 되돌린다. 제출 직전에 급하게 고친 코드가 테스트를 안 거치고 들어가는 것이 가장 위험하다.

## 완료 기준

1. 8개 항목이 전부 통과한다.
2. 클린 클론에서 테스트가 통과한다.
3. curl 예시가 실제 응답과 일치한다.
4. 점검 결과가 문서로 남는다.
