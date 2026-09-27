package com.orbit.organization.application.port.out;

/** 같은 회사·같은 유형 종류의 이름 DB unique 제약 충돌. */
public final class PersonnelTypeNameConflictException extends RuntimeException {
    public PersonnelTypeNameConflictException(Throwable cause) {
        super("personnel type name already exists", cause);
    }
}
