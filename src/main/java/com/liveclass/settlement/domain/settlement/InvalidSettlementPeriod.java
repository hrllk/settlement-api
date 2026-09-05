package com.liveclass.settlement.domain.settlement;

/**
 * 정산 기간 입력이 잘못됐을 때 던진다.
 *
 * <p>이 프로젝트에서 사용자가 만들 수 있는 유일한 정산 도메인 예외다.
 * 전역 예외 처리기(Task 4)가 400으로 변환한다. 나머지 도메인 예외
 * ({@link IllegalArgumentException}, {@link NullPointerException})는
 * 프로그래밍 오류이므로 400으로 매핑하지 않는다.
 */
public class InvalidSettlementPeriod extends RuntimeException {

    public InvalidSettlementPeriod(String message) {
        super(message);
    }
}
