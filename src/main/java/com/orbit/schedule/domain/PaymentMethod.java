package com.orbit.schedule.domain;

/** 작업 등록 시 지정하는 결제수단. */
public enum PaymentMethod {
    ON_SITE_CARD("현장 카드"),
    ON_SITE_CASH("현장 현금"),
    BANK_TRANSFER("계좌이체"),
    INVOICE_POSTPAID("청구서 발행 후불");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
