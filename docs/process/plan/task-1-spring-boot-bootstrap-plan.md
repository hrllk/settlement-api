# Task 1 — Spring Boot 기본 구조 구성: 검토 계획

## 목표

후속 정산 기능을 얹을 수 있는 최소 Spring Boot 실행 기반을 만든다. 이 작업만으로 판매·취소·정산 API의 실제 기능은 구현하지 않는다.

## 포함 범위

- Spring Web, Validation, Spring Data JPA, H2 및 테스트 의존성을 구성한다.
- `adapter.in`, `application`, `domain`, `adapter.out`, `config`의 최소 패키지 경계를 만든다.
- 과제용 액터 헤더(`X-Actor-Id`, `X-Actor-Role`)를 해석할 위치와 전달 규칙을 정한다. 실제 인증·인가 정책의 완성은 후속 API 작업에서 한다.
- 로컬 H2 환경에서 애플리케이션 컨텍스트가 기동하는지 검증하고, 액터 헤더 해석 규칙을 단위 테스트로 고정한다.
- Java·Gradle 빌드 산출물을 `.gitignore`에 추가하고 Gradle Wrapper 파일은 추적 대상으로 유지한다.

## 제외 범위

- 판매·취소 엔티티 및 초기 데이터
- 정산 계산 규칙과 API 엔드포인트
- 실제 인증, 운영 환경 DB, Docker, 관측성 플랫폼
- 추상 기반 클래스·범용 포트 같은 공통화

## 제약과 결정

- 헥사고날 구조는 얇게 유지한다. 웹 어댑터 → 애플리케이션 유스케이스/포트 → 도메인 → JPA 어댑터의 역할만 분리한다.
- 금액은 후속 작업에서 원 단위 `long`을 사용한다.
- 시간·날짜 규칙은 후속 작업에서 `Asia/Seoul`을 기준으로 적용한다.

## 확정된 전제

- Java 21과 Gradle을 사용하며, 루트 패키지는 `com.liveclass.settlement`으로 둔다.
- 메모리 H2를 사용하고 `ddl-auto=create-drop`, `defer-datasource-initialization=true`로 둔다. Task 2가 엔티티만 추가하면 스키마가 생긴다.
- H2 URL에 `MODE=PostgreSQL`을 쓰지 않는다. H2 콘솔은 채점 편의를 위해 켠다.
- `ActorContext`와 액터 헤더 해석기만 둔다. 액터를 요구하는 후속 API에서만 누락·형식 오류를 `400`으로 처리한다.
- Spring Boot 기반의 최소 부트스트랩을 우선한다. 실제 접근 제어와 공통 오류 응답은 후속 API 작업에서 완성한다.

## 완료 기준

- Gradle Wrapper를 포함해 `./gradlew test`만으로 의존성 설치와 검증을 재현할 수 있다.
- 선택한 언어와 빌드 도구로 프로젝트를 재현 가능하게 기동할 수 있다.
- 위 다섯 패키지 경계가 존재하고, 책임이 문서 또는 코드 구조로 드러난다.
- H2 기반 애플리케이션 컨텍스트 테스트와 액터 헤더 해석기 단위 테스트가 통과한다.
- `gradle/wrapper/gradle-wrapper.jar`와 실행 권한이 있는 `gradlew`가 저장소에 커밋되어 있다.

## 구현 경계

```text
adapter.in   → HTTP와 액터 헤더 해석
application → 유스케이스·입출력 포트의 자리
domain      → 정산 규칙이 들어갈 자리
adapter.out  → JPA 구현체가 들어갈 자리
config      → H2·웹 설정
```

- Task 1에서 실제 소스는 애플리케이션 부트스트랩, `ActorContext`, 헤더 해석기와 그 등록 설정으로 제한한다.
- JPA 엔티티·리포지토리·컨트롤러·DTO·예외 응답은 만들지 않는다.
- 액터 정보가 필요한 컨트롤러가 생기는 Task 4부터 해석기를 사용한다.
- 검증은 `@SpringBootTest` 컨텍스트 기동 테스트 1건과 액터 헤더 해석기 단위 테스트 3건으로 제한한다. HTTP 통합 테스트는 실제 컨트롤러가 생기는 후속 작업에서 작성한다.

## 검토 결과

- CEO 검토: 3시간 안에 제출 가능한 최소 기반이라는 목적에 맞으며, 인증·가짜 엔티티·범용 추상화는 범위 밖으로 유지한다.
- 엔지니어링 검토: Java 21, 메모리 H2, `ddl-auto=none`, Gradle Wrapper로 재현성과 빈 데이터베이스 기동 조건을 고정했다.
- 개발자 경험 검토: 로컬 의존 도구를 강제하지 않는 Gradle Wrapper와 단일 컨텍스트 기동 테스트를 사용한다. 실행 안내는 Task 7 README에서 완성한다.
