package com.orbit.media.domain;

import java.util.List;

/**
 * 연결된 대상에서 빠져 만료로 바뀐 파일들의 객체 키. 만료 기록과 같은 트랜잭션에서 발행하고, 커밋 뒤에만 저장소 객체에 만료 표시를 남긴다(롤백되면 아직 연결된 파일이라
 * 표시하지 않는다). 모듈 안에서만 쓰는 사실이다.
 */
public record UploadedFilesExpired(List<String> objectKeys) {

    public UploadedFilesExpired {
        objectKeys = List.copyOf(objectKeys);
    }
}
