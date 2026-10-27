package com.orbit.media.application.port.out;

import java.util.Optional;

/**
 * 파일 객체 저장소. 객체 삭제는 하지 않고 만료 표시만 남긴다(삭제는 저장소 수명 주기 규칙이 함). 서명 메서드는 저장소를 부르지 않는다. 모든 메서드는 저장소에 닿지
 * 못하거나 저장소가 오류로 답하거나, 서명에 쓸 자격 증명을 얻지 못하면 {@link ObjectStorageUnavailableException}을 던진다.
 */
public interface ObjectStoragePort {

    /** {@code key}에 이 형식·크기로만 올릴 수 있는 업로드 주소를 서명한다. */
    PresignedUpload presignUpload(String key, String contentType, long contentLength);

    /** 객체 메타데이터. 없으면 비어 있다. */
    Optional<StoredObject> find(String key);

    /**
     * 객체 앞부분 최대 {@code length}바이트. 객체가 더 짧으면 그만큼만 돌려준다. {@link #find}로 확인한 내용({@code eTag})일 때만 읽고, 그 사이 지워졌거나
     * 다른 내용으로 바뀌었으면 비어 있다.
     */
    Optional<byte[]> readFirstBytes(String key, int length, String eTag);

    /**
     * 같은 저장소 안에서 복사한다. 원본이 {@link #find}로 확인한 내용({@code eTag})일 때만 복사하고, 그 사이 지워졌거나 다른 내용으로 바뀌었으면 복사하지 않고
     * false다. 대상이 있으면 덮어쓴다(같은 원본의 재시도는 결과가 같다). 대상의 메타데이터는 원본(업로더가 붙인 헤더)을 쓰지 않고 {@code contentType}과 저장소가
     * 정한 캐시·표시 방식으로 고정한다.
     *
     * @param contentType 대상에 둘 형식(발급 때 기록한 값)
     */
    boolean copy(String sourceKey, String targetKey, String eTag, String contentType);

    /** 객체에 만료 표시(태그 {@code status=expired})를 남긴다. */
    void markExpired(String key);

    /** 내려받기 주소를 서명한다. */
    String presignDownload(String key);
}
