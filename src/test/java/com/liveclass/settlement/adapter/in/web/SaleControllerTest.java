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

/**
 * {@code @WebMvcTest}를 쓰지 않는다. 유스케이스와 포트를 전부 목으로 만들어야
 * 하는데 그러면 검증하는 것이 배선이 아니라 목 설정이 된다. 시드가 이미 있으므로
 * 실제 스택으로 돌린다.
 *
 * {@code @Transactional}로 롤백한다. 그리고 등록 테스트는 2025-06을 쓴다.
 * 롤백이 한 번이라도 새면 creator-1의 3월 기대값 120,000원이 조용히 틀어지고,
 * 깨지는 것은 이 파일이 아니라 다른 파일이다. 월을 분리하면 롤백이 실패해도
 * 아무것도 안 깨진다.
 *
 * Task 5·6이 API 테스트를 추가할 때 이 애노테이션 세 줄을 그대로 쓴다.
 * 다르게 쓰면 Spring 컨텍스트가 하나 더 생기고, 같은 이름의 인메모리 DB를
 * create-drop으로 밟는 컨텍스트가 늘어난다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaleControllerTest {

    @Autowired
    MockMvc mvc;

    private static final String ADMIN_ID = "admin-1";
    private static final String JUNE_PAID = "2025-06-10T10:00:00+09:00";

    // JSON을 문자열로 직접 쓴다. Boot 4는 Jackson 3(tools.jackson)을 쓰는데
    // 직렬화기를 테스트가 알 필요가 없다. 리터럴이 요청 모양을 그대로 보여준다.
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

        /**
         * 응답 시각이 epoch 숫자가 아니라 오프셋 포함 문자열이어야 한다.
         * 숫자로 나가면 README curl 예시를 읽을 수 없고 평가자가 KST를 암산해야 한다.
         *
         * Boot 4는 Jackson 3을 쓰고 날짜를 기본으로 ISO 문자열로 내보낸다.
         * Jackson 2 시절의 spring.jackson.serialization.write-dates-as-timestamps는
         * 존재하지 않는 프로퍼티라 넣으면 컨텍스트 기동이 통째로 실패한다.
         */
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

        /**
         * QA 회귀: {@code amount: 1.5}가 201로 통과하며 <b>1로 잘려 저장됐다.</b>
         * Jackson의 {@code ACCEPT_FLOAT_AS_INT}가 기본 켜짐이라 소수를 long으로
         * 조용히 버린다. 99,999.99원을 보낸 요청이 99,999원이 되고 아무도 모른다.
         * 금액을 다루는 API에서 조용한 절단은 400보다 나쁘다.
         *
         * <p>발견: /qa · 2026-09-05 · 실제로 저장값이 1인 것을 확인했다.
         */
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

    /**
     * 나머지 오류 단언 10건은 전부 {@code $.code}만 본다. 그건 옛
     * {@code {code, message, status}} 포맷에서도 통과한다 -- 즉 되돌려도 초록불이다.
     * 이 한 건이 그 전환을 잠근다.
     *
     * 한 건으로 충분한 이유는 응답 모양을 결정하는 코드 경로가
     * {@code GlobalExceptionHandler}의 헬퍼 한 곳이기 때문이다. 거기서 나온 응답
     * 하나를 통째로 잠그면 된다. 같은 단언을 10번 복사하면 포맷을 바꿀 때
     * 고칠 곳이 10곳이 된다.
     */
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

        /**
         * 프레임워크가 던지는 실패도 같은 포맷이어야 한다.
         * {@code spring.mvc.problemdetails.enabled=true}가 없으면 405는 본문이
         * 비어 나가고, 그러면 "모든 오류가 한 모양"이라는 주장이 거짓이 된다.
         * {@code code}는 없다 -- 우리가 코드를 붙이지 않은 실패이고, 붙이려면
         * 프레임워크 예외 목록을 우리가 복제해야 한다.
         */
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

        /**
         * 부등호 하나짜리 실수를 잡는다. 판정이 {@code >=}면 전액 환불이 거부된다.
         * 시드의 cancel-1이 그 경우인데 시드는 data.sql로 들어가 API를 안 거치므로
         * 이 테스트가 없으면 아무것도 못 잡는다.
         */
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

        /**
         * 이 태스크에서 가장 틀리기 쉬운 규칙. findCancels(1/1, 2/1, creator-2)로
         * 상태를 만들면 cancel-3(2월 3일)이 창에 안 잡혀 NONE이 나온다.
         * findCancelsBySaleIds를 써야 FULL이 된다.
         */
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
