# Task 1 — Spring Boot 기본 구조 구성 명세

## 배경

과제 B의 판매·취소·정산 기능을 단계적으로 구현하기 위한 최소 Spring Boot 실행 기반을 구성한다. 현재 저장소에는 계획과 Taskmaster 작업 정의만 있고 애플리케이션 코드는 없다.

## 현재 상태

- 계획: `docs/plan/task-1-spring-boot-bootstrap-plan.md`
- 작업 정의: `.taskmaster/tasks/tasks.json`의 Task 1
- 구현 코드, Gradle 구성, 테스트가 없다.

## 변경 내용

### 빌드와 실행 환경

- Java 21, Spring Boot Gradle 플러그인 `4.1.1`, Spring Dependency Management 플러그인 `1.1.7`, Gradle Wrapper `9.7.1`을 사용한다.
- 루트 패키지는 `com.liveclass.settlement`으로 둔다.
- `group`은 `com.liveclass`, `version`은 `0.0.1-SNAPSHOT`, Gradle 저장소는 `mavenCentral()`로 둔다.
- 의존성은 Spring Initializr 기준으로 `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `spring-boot-h2console`, 런타임 H2와 각 Starter의 테스트 모듈, JUnit Platform Launcher를 추가한다.
- `test` 태스크는 `useJUnitPlatform()`을 사용한다.

### H2 설정

설정 파일은 `src/main/resources/application.yml` 하나만 둔다. Spring Initializr가 생성한 `application.properties`는 삭제해 설정 소스가 둘로 갈라지지 않게 한다. `spring.application.name=settlement`은 yml로 옮긴다.

아래의 메모리 데이터베이스 설정을 둔다.

- URL: `jdbc:h2:mem:creator-settlement;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`. `MODE=PostgreSQL`은 쓰지 않는다. 이 과제에 PostgreSQL 인스턴스가 없어 부분 에뮬레이션이 조용한 차이만 만든다.
- 드라이버: `org.h2.Driver`
- 사용자: `sa`, 비밀번호: 빈 문자열
- JPA 속성: `spring.jpa.hibernate.ddl-auto=create-drop`, `spring.jpa.show-sql=false`, `spring.jpa.defer-datasource-initialization=true`
- H2 콘솔 속성: `spring.h2.console.enabled=true` (경로 `/h2-console`)

Task 1에서는 엔티티가 없으므로 Hibernate가 만들 테이블도 없다. `create-drop`은 Task 2에서 엔티티만 추가하면 스키마 파일 유지보수 없이 테이블이 생기도록 미리 고정해 두는 설정이다. `defer-datasource-initialization=true`는 Task 2의 `data.sql`이 Hibernate DDL보다 먼저 실행되는 함정을 막는다. Task 1에서 스키마·초기 데이터 파일은 만들지 않는다.

### 패키지와 최소 타입

```text
com.liveclass.settlement
├── SettlementApplication.java
├── adapter
│   ├── in
│   │   └── actor
│   │       ├── ActorRole.java
│   │       ├── ActorContext.java
│   │       └── ActorContextArgumentResolver.java
│   └── out
│       └── .gitkeep
├── application
│   └── .gitkeep
├── domain
│   └── .gitkeep
└── config
    └── WebMvcConfig.java
```

- `ActorRole`은 `ADMIN`, `CREATOR`만 가진 enum이다.
- `ActorContext`는 `String actorId`, `ActorRole role`을 가진 Java record다.
- `ActorContextArgumentResolver#supportsParameter`는 매개변수 타입이 `ActorContext`인 경우에만 `true`를 반환한다. 별도 애너테이션은 만들지 않는다.
- `ActorContextArgumentResolver#resolveArgument`는 `X-Actor-Id`, `X-Actor-Role`을 읽어 `ActorContext`를 생성한다.
- `X-Actor-Id`는 `strip()` 후 공백이 아니고 1~100자여야 한다. `ActorContext`에는 `strip()`한 값을 저장한다.
- `X-Actor-Role`은 `strip()` 후 정확히 대문자 `ADMIN` 또는 `CREATOR`여야 한다. `admin`처럼 소문자는 대문자로 변환하지 않고 거부한다.
- 누락·빈 값·잘못된 역할은 `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)`로 실패시킨다. 실제 컨트롤러와 오류 응답 검증은 Task 4에서 한다.
- `WebMvcConfig`는 `@Configuration`과 `WebMvcConfigurer`를 사용해 `addArgumentResolvers`에서 위 해석기 하나를 등록한다.
- `application`, `domain`, `adapter.out`에는 `.gitkeep`만 둔다. 포트·서비스·엔티티 같은 가짜 구현을 추가하지 않는다.

## 제외 범위

- JPA 엔티티, 리포지토리, 초기 데이터, API 컨트롤러, DTO
- 실제 인증·인가, 공통 예외 응답, Docker, 운영 데이터베이스
- 정산 도메인 규칙과 판매·취소 기능

## 완료 기준

1. `./gradlew test`가 Java 21 환경에서 통과한다.
2. Gradle Wrapper `9.7.1`로 외부 Gradle 설치 없이 빌드와 테스트를 재현할 수 있다. `gradle/wrapper/gradle-wrapper.jar`가 커밋되어 있고 `gradlew`의 실행 권한이 `100755`이다.
3. `.gitignore`가 `build/`와 `.gradle/`을 무시하고, Wrapper 파일은 무시하지 않는다.
4. `SettlementApplicationContextTest`가 `@SpringBootTest(classes = SettlementApplication.class)`로 컨텍스트를 기동한다.
5. 테스트는 주입된 `ApplicationContext`, `DataSource`, `EntityManagerFactory`가 null이 아님을 단언하고, `DataSource#getConnection()`이 예외 없이 연결을 반환하는지 확인한다.
6. `ActorContextArgumentResolverTest`는 정상 헤더의 `ActorContext` 생성, 필수 헤더 누락, 잘못된 역할값을 각각 검증한다. 오류 두 경우는 `ResponseStatusException`과 `HttpStatus.BAD_REQUEST`를 단언한다.
7. H2 데이터소스와 JPA 자동 설정이 위 설정으로 기동한다.
8. 위 트리의 패키지 경계와 액터 타입이 존재한다.
9. JPA 엔티티, API, 가짜 초기 데이터가 포함되지 않는다.

## 테스트 계획

| 계층 | 테스트 파일 | 검증 | 수량 |
| --- | --- | --- | ---: |
| 통합 | `SettlementApplicationContextTest` | Spring Boot 컨텍스트, H2, JPA 자동 설정 기동 | 1 |
| 단위 | `ActorContextArgumentResolverTest` | 정상·누락·잘못된 역할 헤더 | 3 |

단위 테스트는 Spring 컨테이너 없이 `MockHttpServletRequest`에 헤더를 넣고 `ServletWebRequest`로 감싼 뒤, `ActorContext`를 반환하는 더미 메서드의 `MethodParameter`를 만들어 `resolveArgument`를 직접 호출한다. 필요한 `spring-test` 클래스는 `spring-boot-starter-webmvc-test`가 제공하므로 의존성을 추가하지 않는다.
| HTTP | 후속 Task | 실제 컨트롤러가 없으므로 이관 | 0 |

## 파일 목록

| 경로 | 변경 |
| --- | --- |
| `settings.gradle`, `build.gradle` | 프로젝트 이름, Java 21, Spring Boot 의존성 |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Gradle Wrapper 9.7.1 (`gradle-wrapper.jar` 포함, `gradlew`는 실행 권한 `100755`) |
| `.gitignore` | `build/`, `.gradle/` 무시. Wrapper 파일은 추적 유지 |
| `src/main/resources/application.yml` | H2와 JPA 설정 |
| `src/main/java/com/liveclass/settlement/SettlementApplication.java` | Spring Boot 진입점 |
| `src/main/java/com/liveclass/settlement/adapter/in/web/*` | 액터 역할·컨텍스트·해석기 |
| `src/main/java/com/liveclass/settlement/config/WebMvcConfig.java` | MVC 해석기 등록 |
| `src/main/java/com/liveclass/settlement/{application,domain,adapter/out}/.gitkeep` | 빈 계층 디렉터리 유지 |
| `src/test/java/com/liveclass/settlement/SettlementApplicationContextTest.java` | 컨텍스트 기동 테스트 |
| `src/test/java/com/liveclass/settlement/adapter/in/web/ActorContextArgumentResolverTest.java` | 액터 헤더 해석 단위 테스트 |

## 롤백

데이터 변경이 없으므로 Task 1 커밋을 되돌리면 된다.

## 예상 소요

약 30~45분.

## 엔지니어링 검토 결과

### 이미 있는 것

- `docs/plan/`의 Task 1 계획과 `.taskmaster/tasks/tasks.json`의 Task 1 정의를 재사용한다.
- 구현 코드와 기존 런타임 흐름은 없다. 따라서 새 구조와 충돌하거나 대체할 대상도 없다.

### 검토 결론

- 아키텍처: `adapter.in`의 HTTP 경계와 `config`의 MVC 등록만 구현하고, `application`, `domain`, `adapter.out`은 `.gitkeep`으로만 유지한다.
- 코드 품질: 빈 계층에 가짜 Java 타입을 추가하지 않는다.
- 테스트: 컨텍스트 기동 1건과 액터 해석기 분기 3건을 작성한다.
- 성능: 엔티티·쿼리·HTTP 엔드포인트가 없어 N+1, 캐시, 부하 대응은 후속 Task 범위다.

### 범위 밖

- 실제 컨트롤러의 액터 헤더 HTTP 응답 검증은 Task 4에서 수행한다.
- JPA 엔티티·초기 데이터는 Task 2, 정산 규칙은 Task 3에서 추가한다.
- 병렬화: 모든 변경이 프로젝트 기반과 `adapter.in`에 집중되므로 순차 구현한다.

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
| --- | --- | ---: | ---: | --- | --- |
| CEO Review | `/autoplan` | 범위와 전략 | 1 | CLEAR | 최소 부트스트랩 유지 |
| Codex Review | `/spec` | 독립 실행 가능성 | 3 | CLEAR | 7/10, 모호성 보완 완료 |
| Eng Review | `/plan-eng-review` | 아키텍처와 테스트 | 1 | CLEAR | 테스트 공백 1건을 3개 단위 테스트로 보완 |
| Design Review | 해당 없음 | UI/UX | 0 | SKIPPED | 백엔드 전용 |
| DX Review | `/autoplan` | 로컬 실행 경험 | 1 | CLEAR | Gradle Wrapper와 단일 기동 경로 |

**VERDICT:** CEO + ENG CLEARED — Task 1 구현 준비 완료.

NO UNRESOLVED DECISIONS
