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

/**
 * 등록 → 취소 → 정산 조회를 한 흐름으로 태운다. <b>어느 Task도 혼자서는 배선
 * 전체를 못 본다.</b>
 *
 * <p>200 확인으로 끝내지 않고 금액을 단언한다. 상태 코드만 보면 배선이 끊겨 전
 * 항목 0이 나와도 통과한다. {@code fee} 12,000은 특히 의미가 있다 — 계산기가
 * 안 불렸으면 0이고, {@code FeePolicy} 빈이 없으면 컨텍스트가 아예 안 뜬다.
 *
 * <p>액터를 단계마다 바꾼다. 등록은 ADMIN, 조회는 본인 CREATOR다. 같은 헤더로
 * 전부 돌리면 역할 전환이 검증되지 않는다.
 *
 * <p><b>2025-06을 쓴다.</b> 시드는 1~3월만 쓴다. {@code DB_CLOSE_DELAY=-1}이라
 * 인메모리 DB가 JVM 수명 내내 살아 있어서, 롤백이 한 번이라도 새면 creator-1의
 * 3월 기대값 120,000원이 조용히 틀어지고 <b>깨지는 것은 이 파일이 아니라 다른
 * 파일</b>이 된다. 월을 분리하면 롤백이 실패해도 시드 기반 단언은 안 깨진다.
 *
 * <p>{@code SaleControllerTest}도 2025-06에 쓰므로 금액을 100,000으로 달리 잡았다.
 * 거기 롤백이 새면 이 테스트가 150,000을 보고 실패한다 — 그 자체가 신호다.
 */
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
                                {"courseId":"course-1","amount":100000,
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
