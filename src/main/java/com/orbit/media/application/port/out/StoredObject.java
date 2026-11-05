package com.orbit.media.application.port.out;

/**
 * 저장소에 있는 객체의 메타데이터.
 *
 * @param sizeBytes 객체 크기
 * @param contentType 저장된 Content-Type. 없으면 null
 * @param eTag 이 내용의 ETag. 확인한 바이트와 뒤이어 읽거나 복사하는 바이트가 같은지 묶는 데 쓴다
 */
public record StoredObject(long sizeBytes, String contentType, String eTag) {}
