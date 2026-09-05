package com.liveclass.settlement.config;

import com.liveclass.settlement.domain.settlement.FeePolicy;
import com.liveclass.settlement.domain.settlement.FixedRateFeePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도메인 객체의 조립 지점.
 *
 * <p><b>빈 등록이 도메인 순수성을 깨지 않는다.</b> 순수성은 도메인이 Spring을
 * 아느냐의 문제이지 Spring이 도메인을 아느냐가 아니다. {@code FixedRateFeePolicy}에
 * {@code @Component}를 붙이면 도메인이 Spring에 묶여 계산기 단위 테스트가 컨텍스트
 * 없이 못 돈다. {@code @Bean}을 여기 두면 의존 방향이 바깥에서 안쪽을 향한다.
 *
 * <p>조립을 한 곳에 모으는 이유는 요율 설정 바인딩이 유스케이스마다 반복되면
 * 요율을 읽는 곳이 흩어져 하나만 고쳤을 때 두 API가 다른 요율로 계산하기 때문이다.
 *
 * <p>{@code SettlementCalculator}는 여기서 등록하지 않는다. Task 4는 정산 계산을
 * 하지 않는다. 쓰는 태스크인 Task 5가 이 클래스에 메서드를 더한다.
 */
@Configuration
public class DomainConfig {

    /**
     * 요율 값은 도메인이 갖지 않는다. {@code FixedRateFeePolicy}에 공개 상수가
     * 없고 {@code application.yml}의 {@code settlement.fee.basis-points}가 원본이다.
     * 기본값 {@code :2000}은 설정이 지워졌을 때의 방어일 뿐이다.
     */
    @Bean
    FeePolicy feePolicy(@Value("${settlement.fee.basis-points:2000}") int basisPoints) {
        return new FixedRateFeePolicy(basisPoints);
    }
}
