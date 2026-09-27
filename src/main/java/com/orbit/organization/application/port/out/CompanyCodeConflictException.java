package com.orbit.organization.application.port.out;

/** 회사 코드의 DB unique 제약에 대한 동시 발급 충돌. */
public final class CompanyCodeConflictException extends RuntimeException {
    public CompanyCodeConflictException(Throwable cause) {
        super("company code already exists", cause);
    }
}
