package com.liveclass.settlement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 배선 전체를 한 흐름으로 본다. 시드와 안 겹치게 2025-06 을 쓰고 롤백도 건다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementE2ETest {

    @Autowired
    MockMvc mvc;

    private static final String ADMIN = "admin-1";

    @Test
    @DisplayName("판매 등록 → 취소 → 정산 조회가 한 흐름으로 이어진다")
    void registerCancelThenSettle() throws Exception {
        // 1. ADMIN이 판매를 등록한다. saleId는 서버가 만드는 UUID다.
        String created = mvc.perform(post("/api/sales")
                        .header("X-Actor-Id", ADMIN).header("X-Actor-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-1","studentId":"student-9",
                                 "amount":100000,
                                 "paidAt":"2025-06-10T10:00:00+09:00"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String saleId = extractSaleId(created);

        // 2. 같은 판매에 부분 취소. 경로에 1단계 응답의 saleId를 쓴다.
        mvc.perform(post("/api/sales/" + saleId + "/cancellations")
                        .header("X-Actor-Id", ADMIN).header("X-Actor-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":40000,"cancelledAt":"2025-06-20T10:00:00+09:00"}
                                """))
                .andExpect(status().isCreated());

        // 3. 본인 CREATOR가 정산을 조회한다. 일곱 필드를 전부 단언한다.
        mvc.perform(get("/api/creators/creator-1/settlements/2025-06")
                        .header("X-Actor-Id", "creator-1").header("X-Actor-Role", "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossSales").value(100_000))
                .andExpect(jsonPath("$.saleCount").value(1))
                .andExpect(jsonPath("$.refunds").value(40_000))
                .andExpect(jsonPath("$.cancelCount").value(1))
                .andExpect(jsonPath("$.netSales").value(60_000))
                .andExpect(jsonPath("$.fee").value(12_000))
                .andExpect(jsonPath("$.payout").value(48_000));
    }

    private static String extractSaleId(String body) {
        Matcher m = Pattern.compile("\"saleId\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        if (!m.find()) {
            throw new AssertionError("saleId not found in: " + body);
        }
        return m.group(1);
    }
}
