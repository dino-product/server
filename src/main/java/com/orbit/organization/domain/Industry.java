package com.orbit.organization.domain;

/** 발주사 업종. 저장값은 상수 이름이며 화면 표시명은 클라이언트가 정한다. */
public enum Industry {
    /** 냉난방 설비 */
    HVAC,
    /** 전기·전자 */
    ELECTRICAL_ELECTRONICS,
    /** 배관·설비 */
    PLUMBING,
    /** 가전 A/S */
    APPLIANCE_SERVICE,
    /** 종합 시설관리 */
    FACILITY_MANAGEMENT,
    /** 기타 */
    OTHER
}
