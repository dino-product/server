package com.orbit.media.domain;

/** 업로드 파일의 상태. PENDING → ATTACHED → EXPIRED로만 바뀐다. */
public enum UploadedFileStatus {
    /** 업로드 주소를 발급함. 객체는 {@code incoming/}에 있거나 아직 없다. */
    PENDING,
    /** 대상에 연결됨. 객체는 {@code media/}에 있다. */
    ATTACHED,
    /** 대상에서 빠짐. 객체에 만료 표시를 남기며 저장소 수명 주기 규칙이 지운다. */
    EXPIRED
}
