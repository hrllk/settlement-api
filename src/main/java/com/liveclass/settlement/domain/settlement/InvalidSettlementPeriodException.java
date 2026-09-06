package com.liveclass.settlement.domain.settlement;

/** 정산 기간 입력이 잘못됐다. 전역 예외 처리기가 400으로 변환한다. */
public class InvalidSettlementPeriodException extends RuntimeException {

    public InvalidSettlementPeriodException(String message) {
        super(message);
    }
}
