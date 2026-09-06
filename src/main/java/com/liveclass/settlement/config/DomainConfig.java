package com.liveclass.settlement.config;

import com.liveclass.settlement.domain.settlement.FeePolicy;
import com.liveclass.settlement.domain.settlement.FixedRateFeePolicy;
import com.liveclass.settlement.domain.settlement.SettlementCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 도메인 조립 지점. 도메인에 {@code @Component}를 붙이지 않으려고 여기 모은다. */
@Configuration
public class DomainConfig {

    /** 요율 원본은 {@code application.yml}. 기본값 2000은 설정 누락 방어용. */
    @Bean
    FeePolicy feePolicy(@Value("${settlement.fee.basis-points:2000}") int basisPoints) {
        return new FixedRateFeePolicy(basisPoints);
    }

    /** 쓰는 태스크가 등록한다. Task 5의 두 유스케이스가 {@code SettlementQuery}로 쓴다. */
    @Bean
    SettlementCalculator settlementCalculator(FeePolicy feePolicy) {
        return new SettlementCalculator(feePolicy);
    }
}
