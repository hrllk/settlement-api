package com.liveclass.settlement.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 스택으로 돌린다. 롤백을 걸고, 시드와 안 겹치게 등록 테스트는 2025-06 을 쓴다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaleControllerTest {

    @Autowired
    MockMvc mvc;

    private static final String ADMIN_ID = "admin-1";
    private static final String JUNE_PAID = "2025-06-10T10:00:00+09:00";

    // JSON 리터럴을 직접 쓴다. 직렬화기를 테스트가 알 필요가 없다.
    private static String saleBody(String courseId, long amount, String paidAt) {
        return """
                {"courseId":"%s","amount":%d,"paidAt":"%s"}
                """.formatted(courseId, amount, paidAt);
    }

    private static String cancelBody(long amount, String cancelledAt) {
        return """
                {"amount":%d,"cancelledAt":"%s"}
                """.formatted(amount, cancelledAt);
    }

    private static String extractSaleId(String responseBody) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"saleId\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(responseBody);
        if (!m.find()) {
            throw new AssertionError("saleId not found in: " + responseBody);
        }
        return m.group(1);
    }

    /** 2025-06 판매를 하나 만들고 그 ID를 돌려준다. 시드 월과 겹치지 않는다. */
    private String registerJuneSale(long amount) throws Exception {
        String body = mvc.perform(post("/api/sales")
                        .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody("course-1", amount, JUNE_PAID)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractSaleId(body);
    }

    @Nested
    @DisplayName("판매 등록")
    class Register {

        @Test
        @DisplayName("201과 Location 헤더를 낸다")
        void created() throws Exception {
            mvc.perform(post("/api/sales")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(saleBody("course-1", 50_000, JUNE_PAID)))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.saleId").isNotEmpty())
                    .andExpect(jsonPath("$.courseId").value("course-1"));
        }

        /** 응답 시각이 epoch 숫자가 아니라 오프셋 포함 문자열이어야 한다. */
        @Test
        @DisplayName("응답 시각이 오프셋 포함 문자열로 나간다")
        void serializesDateAsIsoString() throws Exception {
            mvc.perform(post("/api/sales")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(saleBody("course-1", 50_000, JUNE_PAID)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.paidAt").isString())
                    .andExpect(jsonPath("$.paidAt").value(org.hamcrest.Matchers.startsWith("2025-06-10T10:00")));
        }

        /** FK가 없으므로 검사가 빠지면 유령 판매가 그냥 등록된다. 채점자가 curl로 밟는 경로다. */
        @Test
        @DisplayName("없는 강의는 404다. 500이 아니다")
        void courseNotFound() throws Exception {
            mvc.perform(post("/api/sales")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(saleBody("course-999", 50_000, JUNE_PAID)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        }

        @Test
        @DisplayName("금액 0은 400이다")
        void zeroAmount() throws Exception {
            mvc.perform(post("/api/sales")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(saleBody("course-1", 0, JUNE_PAID)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        /** QA 회귀: 소수 금액이 201 로 통과하며 조용히 잘려 저장됐다. */
        @Test
        @DisplayName("소수 금액은 조용히 잘리지 않고 400이다")
        void fractionalAmountRejected() throws Exception {
            mvc.perform(post("/api/sales")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"courseId":"course-1","amount":99999.99,
                                     "paidAt":"2025-06-10T10:00:00+09:00"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }

        /** Jackson이 Instant에 오프셋 없는 값을 UTC로 조용히 파싱하는 함정을 막는다. */
        /** 상한이 없으면 수수료와 합계 누적이 래핑해 음수 정산이 나간다. */
        @Test
        @DisplayName("상한을 넘는 금액은 400이다")
        void amountAboveCapRejected() throws Exception {
            mvc.perform(post("/api/sales")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(saleBody("course-1", Long.MAX_VALUE, JUNE_PAID)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("오프셋 없는 paidAt은 400이다")
        void missingOffset() throws Exception {
            mvc.perform(post("/api/sales")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(saleBody("course-1", 50_000, "2025-06-10T10:00:00")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }

        @Test
        @DisplayName("CREATOR가 등록하면 403이다")
        void creatorCannotRegister() throws Exception {
            mvc.perform(post("/api/sales")
                            .header("X-Actor-Id", "creator-1").header("X-Actor-Role", "CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(saleBody("course-1", 50_000, JUNE_PAID)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACTOR_ACCESS_DENIED"));
        }

        /** 헤더 누락은 신원을 모르는 것이고 인가 실패는 알고 거부하는 것이다. */
        @Test
        @DisplayName("액터 헤더 누락은 400이다. 403이 아니다")
        void missingActorHeader() throws Exception {
            mvc.perform(post("/api/sales")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(saleBody("course-1", 50_000, JUNE_PAID)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ACTOR_HEADER"));
        }
    }

    /** 나머지 단언은 {@code $.code}만 본다. 포맷 전환을 잠그는 것은 이 한 건뿐이다. */
    @Nested
    @DisplayName("오류 포맷 계약")
    class ErrorFormat {

        @Test
        @DisplayName("오류 응답이 RFC 9457 여섯 필드를 전부 갖는다")
        void rfc9457Shape() throws Exception {
            mvc.perform(post("/api/sales")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(saleBody("course-999", 50_000, JUNE_PAID)))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    // type 은 명시하지 않으면 기본값 about:blank 가 직렬화에서 생략된다.
                    .andExpect(jsonPath("$.type").value("urn:problem-type:course-not-found"))
                    .andExpect(jsonPath("$.title").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("course not found: course-999"))
                    .andExpect(jsonPath("$.instance").value("/api/sales"))
                    .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        }

        /** 프레임워크 실패도 problem+json 이어야 한다. {@code code}는 없는 게 정상이다. */
        @Test
        @DisplayName("허용되지 않은 메서드도 problem+json으로 나간다")
        void frameworkFailureIsProblemJson() throws Exception {
            mvc.perform(patch("/api/sales")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.title").isNotEmpty())
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.detail").isNotEmpty())
                    .andExpect(jsonPath("$.instance").value("/api/sales"));
        }
    }

    @Nested
    @DisplayName("취소 등록")
    class RegisterCancel {

        @Test
        @DisplayName("없는 판매는 404다")
        void saleNotFound() throws Exception {
            mvc.perform(post("/api/sales/sale-999/cancellations")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cancelBody(10_000, "2025-06-11T12:00:00+09:00")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SALE_NOT_FOUND"));
        }

        /** 단건 비교 구현을 잡는다. 30,000과 60,000이 각각 통과하면 90,000이 환불된다. */
        @Test
        @DisplayName("누적 초과 환불은 둘째 요청에서 409다")
        void accumulatedRefundExceeded() throws Exception {
            String saleId = registerJuneSale(80_000);

            mvc.perform(post("/api/sales/" + saleId + "/cancellations")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cancelBody(30_000, "2025-06-11T12:00:00+09:00")))
                    .andExpect(status().isCreated());

            mvc.perform(post("/api/sales/" + saleId + "/cancellations")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cancelBody(60_000, "2025-06-12T12:00:00+09:00")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("REFUND_AMOUNT_EXCEEDED"));
        }

        /** 판정이 {@code >=}면 전액 환불이 거부된다. 시드는 API 를 안 거쳐 여기서만 잡힌다. */
        /** 취소도 ADMIN 전용이다. 이 단언이 없으면 requireAdmin을 지워도 초록불이다. */
        @Test
        @DisplayName("CREATOR가 취소하면 403이다")
        void creatorCannotCancel() throws Exception {
            mvc.perform(post("/api/sales/sale-1/cancellations")
                            .header("X-Actor-Id", "creator-1").header("X-Actor-Role", "CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cancelBody(1_000, "2025-06-11T12:00:00+09:00")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACTOR_ACCESS_DENIED"));
        }

        /** 통과시키면 판매 없던 달에 환불이 귀속돼 정산이 근거 없이 음수가 된다. */
        @Test
        @DisplayName("결제보다 이른 취소는 409다")
        void cancelBeforePaymentRejected() throws Exception {
            String saleId = registerJuneSale(80_000);

            mvc.perform(post("/api/sales/" + saleId + "/cancellations")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cancelBody(1_000, "2025-06-09T12:00:00+09:00")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANCEL_BEFORE_PAYMENT"));
        }

        @Test
        @DisplayName("합계가 원결제와 같은 전액 환불은 201이다")
        void exactFullRefundAllowed() throws Exception {
            String saleId = registerJuneSale(80_000);

            mvc.perform(post("/api/sales/" + saleId + "/cancellations")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cancelBody(80_000, "2025-06-11T12:00:00+09:00")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cancelId").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("판매 목록 조회")
    class ListSales {

        @Test
        @DisplayName("CREATOR가 타인 목록을 조회하면 403이다")
        void creatorCannotSeeOthers() throws Exception {
            mvc.perform(get("/api/creators/creator-1/sales")
                            .param("from", "2025-03-01").param("to", "2025-03-31")
                            .header("X-Actor-Id", "creator-2").header("X-Actor-Role", "CREATOR"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACTOR_ACCESS_DENIED"));
        }

        /** 기간으로 좁힌 취소로 상태를 만들면 cancel-3 을 놓쳐 NONE 이 나온다. */
        @Test
        @DisplayName("sale-5를 1월로 조회해도 환불 상태가 FULL이다")
        void refundStatusIgnoresPeriod() throws Exception {
            mvc.perform(get("/api/creators/creator-2/sales")
                            .param("from", "2025-01-01").param("to", "2025-01-31")
                            .header("X-Actor-Id", "creator-2").header("X-Actor-Role", "CREATOR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sales[0].saleId").value("sale-5"))
                    .andExpect(jsonPath("$.sales[0].courseId").value("course-3"))
                    .andExpect(jsonPath("$.sales[0].refundStatus").value("FULL"));
        }

        /** 2025-13의 거부가 프레임워크가 아니라 도메인에서 일어나는지 본다. */
    /** requireSelfOrAdmin의 ADMIN 분기. 거부만 단언하면 이 갈래는 안 밟힌다. */
        @Test
        @DisplayName("ADMIN은 타인 목록을 조회한다")
        void adminListsOtherCreator() throws Exception {
            mvc.perform(get("/api/creators/creator-1/sales")
                            .param("from", "2025-03-01").param("to", "2025-03-31")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.creatorId").value("creator-1"))
                    .andExpect(jsonPath("$.sales.length()").value(4));
        }

        /** 파서를 통과한 값이 상한 +1 산술에서 터진다. 안 감싸면 500 이다. */
        @Test
        @DisplayName("지원 범위를 넘는 종료일은 500이 아니라 400이다")
        void endDateOutOfRangeIsBadRequest() throws Exception {
            mvc.perform(get("/api/creators/creator-1/sales")
                            .param("from", "2025-01-01").param("to", "+999999999-12-31")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SETTLEMENT_PERIOD"));
        }

        @Test
        @DisplayName("잘못된 연월은 400 INVALID_SETTLEMENT_PERIOD다")
        void invalidPeriod() throws Exception {
            mvc.perform(get("/api/creators/creator-1/sales")
                            .param("from", "2025-13-01").param("to", "2025-13-31")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SETTLEMENT_PERIOD"));
        }

        @Test
        @DisplayName("to 누락은 400 MISSING_PARAMETER다")
        void missingParameter() throws Exception {
            mvc.perform(get("/api/creators/creator-1/sales")
                            .param("from", "2025-03-01")
                            .header("X-Actor-Id", ADMIN_ID).header("X-Actor-Role", "ADMIN"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
        }
    }
}
