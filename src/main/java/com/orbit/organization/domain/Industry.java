package com.orbit.organization.domain;

public enum Industry {
    HVAC("냉난방 설비"),
    ELECTRICAL_ELECTRONICS("전기·전자"),
    PLUMBING("배관·설비"),
    APPLIANCE_SERVICE("가전 A/S"),
    FACILITY_MANAGEMENT("종합 시설관리"),
    OTHER("기타");

    private final String displayName;

    Industry(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
