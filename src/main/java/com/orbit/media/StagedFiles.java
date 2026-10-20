package com.orbit.media;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link MediaFiles#stage} 결과. {@link MediaFiles#attach}에 그대로 넘긴다.
 *
 * @param organizationId 요청한 발주사
 * @param accountId 요청한 계정
 * @param purpose 용도 코드
 * @param fileIds 요청한 파일 식별자(순서 유지)
 * @param copied 이번 확인에서 연결 위치로 복사한 PENDING 파일. 나머지는 이미 같은 대상에 연결된 파일이다
 * @param rejection 첫 거부. 없으면 null
 */
public record StagedFiles(
        Long organizationId,
        Long accountId,
        String purpose,
        List<String> fileIds,
        Set<String> copied,
        FileRejection rejection) {

    public StagedFiles {
        fileIds = List.copyOf(fileIds);
        copied = Set.copyOf(copied);
    }

    /** 확인할 파일이 없을 때(저장소를 부르지 않음). */
    public static StagedFiles empty(Long organizationId, Long accountId, String purpose) {
        return new StagedFiles(organizationId, accountId, purpose, List.of(), Set.of(), null);
    }

    public Optional<FileRejection> findRejection() {
        return Optional.ofNullable(rejection);
    }

    /**
     * 거부된 파일과 사유.
     *
     * @param fileId 요청에 들어 있던 식별자 문자열 그대로
     */
    public record FileRejection(String fileId, Reason reason) {}

    /** 거부 사유. */
    public enum Reason {
        /** 같은 식별자가 요청에 두 번 이상 있음. */
        DUPLICATED,
        /** 발급 기록이 없거나, 요청자·발주사·용도가 다르거나, 다른 대상에 연결됐거나 만료됐거나, 아직 저장소에 올라오지 않음. */
        NOT_UPLOADED,
        /** 올라온 파일의 형식이 발급 때 밝힌 형식이 아니거나 이미지 시그니처가 맞지 않음. */
        CONTENT_TYPE_MISMATCH,
        /** 올라온 파일이 발급 때 밝힌 크기보다 큼. */
        TOO_LARGE
    }
}
