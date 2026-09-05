package com.liveclass.settlement.config;

import com.liveclass.settlement.domain.settlement.FeePolicy;
import com.liveclass.settlement.domain.settlement.FixedRateFeePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도메인 객체의 조립 지점. 도메인 클래스에 {@code @Component}를 붙이지 않기 위해
 * 빈 등록을 여기 모은다.
 */
@Configuration
public class DomainConfig {

    /** 요율 원본은 {@code application.yml}. 기본값 2000은 설정 누락 방어용. */
    @Bean
    FeePolicy feePolicy(@Value("${settlement.fee.basis-points:2000}") int basisPoints) {
        return new FixedRateFeePolicy(basisPoints);
    }
}
