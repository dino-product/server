package com.orbit.media.application.port.out;

/**
 * 저장소에 있는 객체의 메타데이터.
 *
 * @param sizeBytes 객체 크기
 * @param contentType 저장된 Content-Type. 없으면 null
 */
public record StoredObject(long sizeBytes, String contentType) {}
