package com.orbit.media;

/**
 * 사용자가 올리는 파일(지금은 이미지)의 업로드·연결·내려받기 주소. 파일 바이트는 서버를 거치지 않고 클라이언트가 발급받은 주소로 저장소에 직접 올린다. 누가 어떤 파일을
 * 올리고 볼 수 있는지는 파일을 쓰는 업무 모듈이 판단한 뒤 호출하며, 이 계약은 그 판단을 다시 하지 않는다(ADR-006).
 *
 * <p>흐름: {@link #issueUpload} → 클라이언트 업로드 → 업무 트랜잭션 밖에서 {@link #stage} → 업무 트랜잭션 안에서 {@link #attach}. 저장소 호출은 DB
 * 트랜잭션 밖에서 먼저 끝내고 DB 기록을 나중에 확정하므로, 장애가 나도 연결된 파일이 사라지지 않고 연결되지 못한 파일만 남는다.
 */
public interface MediaFiles {

    /**
     * 업로드 주소를 발급하고 업로드 대기(PENDING) 기록을 남긴다. 호출자의 트랜잭션에 참여한다(없으면 새로 연다). 형식이 허용 형식이 아니거나 크기가 1바이트 미만이거나
     * {@link UploadSpec#maxBytes()}를 넘으면 {@link UploadNotAllowedException}이다.
     */
    UploadTicket issueUpload(UploadRequest request);

    /**
     * 요청한 파일이 연결할 수 있는 상태인지 저장소에서 확인하고, 새로 올린 파일은 연결 위치로 복사한다. 트랜잭션 안에서 부르면 {@link IllegalStateException}이다(저장소
     * 지연이 DB 연결을 붙잡지 않게). 확인에 실패한 파일은 예외 대신 결과의 {@link StagedFiles#findRejection()}으로 돌려주므로, 호출자가 자기 오류 순서에 맞춰
     * 알린다. 첫 거부에서 멈추며 나머지 파일은 확인하지 않는다. 저장소에 닿지 못하면 {@link MediaStorageUnavailableException}이다.
     */
    StagedFiles stage(StageRequest request);

    /**
     * {@link #stage} 결과의 파일 목록을 {@code owner}의 그 용도 파일 전체로 확정한다. 호출자의 트랜잭션이 필요하며 함께 커밋·롤백된다. 새로 올린 파일은 연결하고, 이미
     * 같은 대상에 연결된 파일은 그대로 두며, 그 대상에 연결돼 있었지만 목록에서 빠진 파일은 만료로 바꾼 뒤 커밋 뒤에 저장소 객체에 만료 표시를 남긴다. 거부가 있는 결과를
     * 넘기면 {@link IllegalArgumentException}, 확인 뒤 다른 요청이 먼저 파일을 쓰거나 바꿨으면 {@link AttachmentConflictException}이다.
     *
     * @param owner 파일을 붙이는 대상의 식별 문자열(예: {@code work:10}). 호출 모듈이 정하며 최대 {@link StageRequest#MAX_OWNER_LENGTH}자
     */
    void attach(StagedFiles staged, String owner);

    /**
     * 연결된 파일을 내려받을 시간 제한 주소. 호출자가 이미 열람 권한을 확인한 파일에만 쓴다. 저장소를 부르지 않고 서명만 한다.
     *
     * @param fileId 연결된 파일의 식별자
     */
    String downloadUrl(Long organizationId, String fileId);
}
