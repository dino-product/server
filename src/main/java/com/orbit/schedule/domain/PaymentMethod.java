package com.orbit.schedule.domain;

/** 작업 등록 시 지정하는 결제수단. 화면 표시 문자열은 Web 경계에서 정한다. */
public enum PaymentMethod {
    ON_SITE_CARD,
    ON_SITE_CASH,
    BANK_TRANSFER;

    /** 완료보고에서 이 계획 결제수단에 대응하는 실제 결제수단. 계획과 실제는 1:1로 대응한다. 아직 호출처는 없고, 정산 연동 때 둘을 비교하는 데 쓴다. */
    public ActualPaymentMethod actualCounterpart() {
        return switch (this) {
            case ON_SITE_CARD -> ActualPaymentMethod.CREDIT_CARD;
            case ON_SITE_CASH -> ActualPaymentMethod.CASH;
            case BANK_TRANSFER -> ActualPaymentMethod.BANK_TRANSFER;
        };
    }
}
