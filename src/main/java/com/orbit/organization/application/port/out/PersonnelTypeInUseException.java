package com.orbit.organization.application.port.out;

/** 현재 지정된 유형을 물리 삭제할 때의 DB FK 제약 충돌. */
public final class PersonnelTypeInUseException extends RuntimeException {
    public PersonnelTypeInUseException(Throwable cause) {
        super("personnel type is assigned", cause);
    }
}
