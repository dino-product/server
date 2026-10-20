package com.orbit.media;

/** 확인({@link MediaFiles#stage}) 뒤 다른 요청이 먼저 파일을 연결·만료해 이번 연결을 확정할 수 없음. 호출자의 트랜잭션은 롤백해야 한다. */
public class AttachmentConflictException extends RuntimeException {

    public AttachmentConflictException(String fileId) {
        super("file changed after staging: " + fileId);
    }
}
