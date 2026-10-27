package com.orbit.notification.domain;

/** 참여 요청 결과 알림 문구에 쓰는 참여 유형. organization의 희망·확정 유형을 리스너가 이 값으로 옮긴다. */
public enum ParticipationType {
    STAFF("직원"),
    TECHNICIAN("기사");

    private final String label;

    ParticipationType(String label) {
        this.label = label;
    }

    /** 문구에 쓰는 이름. */
    public String label() {
        return label;
    }
}
