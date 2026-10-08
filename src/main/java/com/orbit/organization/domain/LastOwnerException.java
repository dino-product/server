package com.orbit.organization.domain;

/** 발주사의 마지막 총관리자를 해제하려 할 때. 발주사에는 총관리자가 1명 이상 있어야 한다. */
public final class LastOwnerException extends IllegalStateException {

    public LastOwnerException() {
        super("the last owner of an organization cannot be revoked");
    }
}
