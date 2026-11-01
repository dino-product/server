package com.orbit.realtime.domain;

/** 실시간 연결을 연 화면의 종류. 연결 수 지표를 나누는 데만 쓰고 권한·전달 대상에는 쓰지 않는다. */
public enum ClientType {
    DESKTOP_WEB("desktop-web"),
    MOBILE_WEB("mobile-web"),
    APP("app"),
    UNKNOWN("unknown");

    private final String tag;

    ClientType(String tag) {
        this.tag = tag;
    }

    /** 지표 태그와 요청 헤더({@code X-Orbit-Client})에 쓰는 이름. */
    public String tag() {
        return tag;
    }
}
