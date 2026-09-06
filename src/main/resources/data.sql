-- 원본 과제 샘플 데이터 17행. 크리에이터 3 + 강의 4 + 판매 7 + 취소 3.
--
-- 저장값은 UTC, 주석은 KST다. 주석이 없으면 리뷰어가 값의 정오를 판단할 수 없다.
-- sale-5의 저장값 2025-01-31T14:30:00Z는 UTC로만 보면 1월 31일이라 이상해
-- 보이지 않는다. 그런데 KST 1월 구간은 [2024-12-31T15:00Z, 2025-01-31T15:00Z)이고
-- 이 값은 그 안에 30분 남기고 들어간다. 그 30분이 이 프로젝트에서 가장 중요한
-- 경계다 -- 여기가 틀리면 creator-2의 1월과 2월 정산이 통째로 뒤집힌다.
--
-- Task 1의 spring.jpa.defer-datasource-initialization=true 가 이 파일을
-- Hibernate DDL 이후에 실행시킨다. 그 설정이 지워지면 테이블 없는 상태로
-- INSERT가 나가 기동이 통째로 실패한다.
--
-- Task 3의 추가 테스트 케이스(수수료 버림 33,333원, 동일 판매 다수 부분 취소)를
-- 여기 넣으면 안 된다. 넣는 순간 creator-1의 2025-03 기대값 120,000원이 깨진다.
-- 그 값은 원본 과제가 명시한 숫자라 바꿀 수 없다.

insert into creators (id, name) values
  ('creator-1', '김강사'),
  ('creator-2', '이강사'),
  ('creator-3', '박강사');

insert into courses (id, creator_id, title) values
  ('course-1', 'creator-1', 'Spring Boot 입문'),
  ('course-2', 'creator-1', 'JPA 실전'),
  ('course-3', 'creator-2', 'Kotlin 기초'),
  ('course-4', 'creator-3', 'MSA 설계');

insert into sales (id, course_id, student_id, amount, paid_at) values
  ('sale-1', 'course-1', 'student-1',  50000, '2025-03-05T01:00:00Z'),   -- 2025-03-05 10:00 KST
  ('sale-2', 'course-1', 'student-2',  50000, '2025-03-15T05:30:00Z'),   -- 2025-03-15 14:30 KST
  ('sale-3', 'course-2', 'student-3',  80000, '2025-03-20T00:00:00Z'),   -- 2025-03-20 09:00 KST
  ('sale-4', 'course-2', 'student-4',  80000, '2025-03-22T02:00:00Z'),   -- 2025-03-22 11:00 KST
  ('sale-5', 'course-3', 'student-5',  60000, '2025-01-31T14:30:00Z'),   -- 2025-01-31 23:30 KST  <- 월 경계
  ('sale-6', 'course-3', 'student-6',  60000, '2025-03-10T07:00:00Z'),   -- 2025-03-10 16:00 KST
  ('sale-7', 'course-4', 'student-7', 120000, '2025-02-14T01:00:00Z');   -- 2025-02-14 10:00 KST

-- 취소 3건은 원본 과제에 없어 직접 정의했다. 금액과 귀속 월은 기대 결과를
-- 재현하도록 확정했고, 시각은 Task 2가 정한다. 세 건 모두 10:00 KST다.
-- 자정과 자정 직전에서 멀어 어떤 구간 구현에서도 귀속 월이 흔들리지 않는다.
-- 경계 검증은 Task 3의 단위 테스트 픽스처가 세 방향으로 이미 잠갔다.
insert into cancels (id, sale_id, amount, cancelled_at) values
  ('cancel-1', 'sale-3', 80000, '2025-03-25T01:00:00Z'),    -- 2025-03-25 10:00 KST  전액
  ('cancel-2', 'sale-4', 30000, '2025-03-26T01:00:00Z'),    -- 2025-03-26 10:00 KST  부분
  ('cancel-3', 'sale-5', 60000, '2025-02-03T01:00:00Z');    -- 2025-02-03 10:00 KST  전액, 월 경계
