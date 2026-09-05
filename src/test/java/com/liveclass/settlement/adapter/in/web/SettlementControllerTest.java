package com.liveclass.settlement.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 조회만 하므로 {@code @Transactional}이 없다. 쓰기가 없으면 롤백할 것도 없다.
 * 컨텍스트 캐시 키를 정하는 것은 아래 두 애노테이션뿐이라
 * {@code SaleControllerTest}와 같은 컨텍스트를 쓴다 — 실측으로 확인했다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettlementControllerTest {

    @Autowired
    MockMvc mvc;

    private static final String ADMIN = "admin-1";

    @Nested
    @DisplayName("크리에이터 월별 정산")
    class Monthly {

        /**
         * 계산 재검증이 아니라 <b>배선 검증</b>이다. 계산의 정확성은 Task 3이 단위로
         * 잠갔다. 값을 단언하는 이유는 200만 보면 배선이 끊겨 전 항목 0이 나와도
         * 통과하기 때문이다.
         */
        @Test
        @DisplayName("creator-1 본인의 2025-03이 payout 120,000이다")
        void ownMonth() throws Exception {
            mvc.perform(get("/api/creators/creator-1/settlements/2025-03")
                            .header("X-Actor-Id", "creator-1")
                            .header("X-Actor-Role", "CREATOR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.creatorId").value("creator-1"))
                    .andExpect(jsonPath("$.yearMonth").value("2025-03"))
                    .andExpect(jsonPath("$.grossSales").value(260_000))
                    .andExpect(jsonPath("$.saleCount").value(4))
                    .andExpect(jsonPath("$.refunds").value(110_000))
                    .andExpect(jsonPath("$.cancelCount").value(2))
                    .andExpect(jsonPath("$.netSales").value(150_000))
                    .andExpect(jsonPath("$.fee").value(30_000))
                    .andExpect(jsonPath("$.payout").value(120_000));
        }

        /** 404가 아니다. "정산이 없다"와 "크리에이터가 없다"를 구분할 수 없게 된다. */
        @Test
        @DisplayName("빈 월은 200에 전 항목 0이다")
        void emptyMonth() throws Exception {
            mvc.perform(get("/api/creators/creator-3/settlements/2025-03")
                            .header("X-Actor-Id", "creator-3")
                            .header("X-Actor-Role", "CREATOR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.grossSales").value(0))
                    .andExpect(jsonPath("$.saleCount").value(0))
                    .andExpect(jsonPath("$.refunds").value(0))
                    .andExpect(jsonPath("$.cancelCount").value(0))
                    .andExpect(jsonPath("$.netSales").value(0))
                    .andExpect(jsonPath("$.fee").value(0))
                    .andExpect(jsonPath("$.payout").value(0));
        }
    }

    @Nested
    @DisplayName("운영자 기간 집계")
    class Admin {

        @Test
        @DisplayName("2025-03 전체 합계가 168,000이다")
        void march() throws Exception {
            mvc.perform(get("/api/admin/settlements")
                            .param("from", "2025-03-01").param("to", "2025-03-31")
                            .header("X-Actor-Id", ADMIN).header("X-Actor-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.from").value("2025-03-01"))
                    .andExpect(jsonPath("$.to").value("2025-03-31"))
                    .andExpect(jsonPath("$.creators.length()").value(3))
                    .andExpect(jsonPath("$.totalPayout").value(168_000));
        }

        /**
         * <b>이 프로젝트의 유일한 회귀 방어선이다.</b> 월별 합산으로 잘못 구현해도
         * 위 2025-03 테스트는 정답이 나온다 — 3월에는 음수 월이 없기 때문이다.
         * 1~3월 구간만이 단일 구간 264,000과 월별 합산 252,000을 가른다.
         */
        @Test
        @DisplayName("2025-01~03 전체 합계가 264,000이다. 월별 합산이면 252,000이라 실패한다")
        void quarterIsNotSumOfMonths() throws Exception {
            mvc.perform(get("/api/admin/settlements")
                            .param("from", "2025-01-01").param("to", "2025-03-31")
                            .header("X-Actor-Id", ADMIN).header("X-Actor-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.creators.length()").value(3))
                    .andExpect(jsonPath("$.totalPayout").value(264_000))
                    // creator-2 단건도 못박는다. 48,000 vs 월별 합 36,000.
                    .andExpect(jsonPath("$.creators[1].creatorId").value("creator-2"))
                    .andExpect(jsonPath("$.creators[1].payout").value(48_000));
        }

        /**
         * <b>{@code to}는 그 날 하루 전체를 포함한다.</b> {@code ofDateRange}가 종료일에
         * 하루를 더해 반열린 구간으로 바꾼다.
         *
         * <p>시드에 3월 31일 데이터가 없어 {@code to=03-31}과 {@code to=04-01}이 같은
         * 값을 낸다. 그래서 이 규칙은 하루짜리 구간으로만 잠글 수 있다. sale-1이
         * 2025-03-05 10:00 KST라, 하루 구간에 잡히고 전날까지로 자르면 빠진다.
         */
        @Test
        @DisplayName("to 는 종료일 하루 전체를 포함한다")
        void endDateIsInclusive() throws Exception {
            mvc.perform(get("/api/admin/settlements")
                            .param("from", "2025-03-05").param("to", "2025-03-05")
                            .header("X-Actor-Id", ADMIN).header("X-Actor-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.creators[0].grossSales").value(50_000))
                    .andExpect(jsonPath("$.creators[0].saleCount").value(1));

            mvc.perform(get("/api/admin/settlements")
                            .param("from", "2025-03-01").param("to", "2025-03-04")
                            .header("X-Actor-Id", ADMIN).header("X-Actor-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.creators[0].grossSales").value(0));
        }

        /** 판매·취소 자료만 훑으면 실적 0인 크리에이터는 목록에서 통째로 빠진다. */
        @Test
        @DisplayName("실적 없는 creator-3도 0원으로 목록에 있다")
        void zeroCreatorIncluded() throws Exception {
            mvc.perform(get("/api/admin/settlements")
                            .param("from", "2025-03-01").param("to", "2025-03-31")
                            .header("X-Actor-Id", ADMIN).header("X-Actor-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    // 순서는 Task 2의 어댑터가 id 오름차순으로 고정한다.
                    .andExpect(jsonPath("$.creators[2].creatorId").value("creator-3"))
                    .andExpect(jsonPath("$.creators[2].payout").value(0));
        }
    }

    @Nested
    @DisplayName("접근 경계")
    class Access {

        /** IDOR. 경로의 creatorId를 한 글자 바꾸는 것만으로 남의 정산이 보이면 안 된다. */
        @Test
        @DisplayName("CREATOR가 타인 정산을 조회하면 403이다")
        void otherCreatorSettlement() throws Exception {
            mvc.perform(get("/api/creators/creator-1/settlements/2025-03")
                            .header("X-Actor-Id", "creator-2")
                            .header("X-Actor-Role", "CREATOR"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACTOR_ACCESS_DENIED"));
        }

        /** 뚫리면 크리에이터 하나가 경쟁자 매출을 전부 본다. */
        @Test
        @DisplayName("CREATOR가 운영자 API를 부르면 403이다")
        void creatorCannotUseAdminApi() throws Exception {
            mvc.perform(get("/api/admin/settlements")
                            .param("from", "2025-03-01").param("to", "2025-03-31")
                            .header("X-Actor-Id", "creator-1")
                            .header("X-Actor-Role", "CREATOR"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACTOR_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("ADMIN은 타인 정산을 조회할 수 있다")
        void adminSeesAnyone() throws Exception {
            mvc.perform(get("/api/creators/creator-1/settlements/2025-03")
                            .header("X-Actor-Id", ADMIN).header("X-Actor-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payout").value(120_000));
        }

        /**
         * <b>{@code code}까지 단언한다.</b> 컨트롤러가 {@code @PathVariable YearMonth}로
         * 바꾸면 Spring이 먼저 거부해 코드가 달라지는데, 상태 코드만 보면 둘 다
         * 400이라 통과한다.
         */
        @Test
        @DisplayName("잘못된 연월은 400 INVALID_SETTLEMENT_PERIOD다")
        void invalidYearMonth() throws Exception {
            mvc.perform(get("/api/creators/creator-1/settlements/2025-13")
                            .header("X-Actor-Id", "creator-1")
                            .header("X-Actor-Role", "CREATOR"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SETTLEMENT_PERIOD"));
        }
    }
}
