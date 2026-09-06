# Task 7.1 — 실행·테스트 명령과 요구 환경 명세

부모: [`task-7-readme-submission-spec.md`](./task-7-readme-submission-spec.md) · 의존 Task 1 · 10분

## 담을 내용

```markdown
## 실행

요구 환경: **Java 21**. Gradle은 설치하지 않아도 된다 (Wrapper 포함).

    ./gradlew bootRun     # http://localhost:8080
    ./gradlew test        # 테스트 108건

H2 콘솔: http://localhost:8080/h2-console
  JDBC URL  jdbc:h2:mem:creator-settlement
  User      sa        Password  (없음)

인메모리 DB라 재시작하면 초기 데이터로 돌아간다.
```

## Java 21을 명시하는 이유

`build.gradle`의 toolchain이 21로 고정돼 있다. Java 17 환경에서 `./gradlew test`를 돌리면 Gradle이 툴체인을 자동 내려받으려다 실패하거나, 실패 메시지가 "toolchain을 못 찾음"이라 원인이 안 드러난다.

버전을 첫 줄에 적어 평가자가 5초 만에 확인할 수 있게 한다.

## Wrapper를 강조하는 이유

`gradle-wrapper.jar`가 커밋돼 있어 로컬 Gradle 설치가 필요 없다. 이것이 실제로 되는지는 7.8의 클린 클론 점검이 확인한다.

많은 전역 `.gitignore`가 `*.jar`를 제외한다. Wrapper jar가 빠지면 `./gradlew`가 첫 줄에서 죽고, 평가자는 프로젝트를 아예 못 띄운다. Task 1에서 `.gitignore`에 부정 규칙을 넣어 막았다.

## H2 콘솔 접속 정보를 적는 이유

Task 1에서 콘솔을 켠 목적이 평가자가 초기 데이터를 눈으로 확인하는 것이다. JDBC URL을 안 적으면 콘솔 화면에서 기본값 `jdbc:h2:~/test`로 접속을 시도해 빈 DB를 보게 된다.

## 적지 않는 것

- IDE 설정, 플러그인 안내 — 명령줄로 충분하다
- Docker — 과제 명시적 제외
- 프로파일 분리 — 프로파일이 하나뿐이다

## 완료 기준

1. Java 21이 명시돼 있다.
2. `./gradlew bootRun`과 `./gradlew test` 두 명령이 있다.
3. 테스트 건수가 실제와 일치한다.
4. H2 콘솔 경로와 JDBC URL이 있다.
5. 인메모리라 재시작 시 초기화된다는 설명이 있다.
