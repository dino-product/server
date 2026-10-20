package com.orbit.media;

import java.util.List;
import java.util.Objects;

/**
 * 연결 전 확인 요청. 발주사·계정·용도가 발급 때와 같은 PENDING 파일만 새로 연결할 수 있고, 이미 {@code owner}에 같은 용도로 연결된 파일은 업로더와 관계없이 그대로
 * 둘 수 있다(다른 관리자가 기본정보를 고칠 때).
 *
 * @param organizationId 요청한 발주사. 검증 전 값이어도 되며 맞지 않으면 파일이 거부된다
 * @param accountId 요청한 계정
 * @param purpose 용도 코드({@link UploadSpec#purpose()})
 * @param fileIds 연결할 파일 식별자 목록(순서 유지). 비어 있으면 저장소를 부르지 않는다
 * @param owner 이미 연결된 대상이 있으면 그 식별 문자열, 새 대상이면 null
 */
public record StageRequest(Long organizationId, Long accountId, String purpose, List<String> fileIds, String owner) {

    public static final int MAX_OWNER_LENGTH = 100;

    public StageRequest {
        UploadSpec.requirePurpose(purpose);
        fileIds = fileIds == null ? List.of() : List.copyOf(fileIds);
        if (owner != null) {
            requireOwner(owner);
        }
    }

    /** 연결 대상 식별 문자열 형식(빈 값 아님, 최대 {@link #MAX_OWNER_LENGTH}자). */
    public static void requireOwner(String owner) {
        Objects.requireNonNull(owner, "owner");
        if (owner.isBlank() || owner.length() > MAX_OWNER_LENGTH) {
            throw new IllegalArgumentException("owner must be 1-" + MAX_OWNER_LENGTH + " characters");
        }
    }
}
