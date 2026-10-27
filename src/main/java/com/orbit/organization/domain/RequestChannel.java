package com.orbit.organization.domain;

/** 요청자가 회사를 찾은 참여 경로([조직·계정] 기능명세 §5). 셋 다 같은 회사 코드로 회사를 확인하므로 클라이언트가 경로를 함께 보낸다. */
public enum RequestChannel {
    /** 회사 코드를 직접 입력. */
    COMPANY_CODE,
    /** 영구 참여 링크로 접속. */
    JOIN_LINK,
    /** QR 코드 인식. */
    QR_CODE
}
