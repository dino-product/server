package com.orbit.media.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.orbit.media.domain.UploadedFile;

/** 업로드 파일 기록 저장소. 돌려주는 Domain 객체는 영속 상태와 분리된 사본이라 {@link #save}해야 반영된다. */
public interface UploadedFileRepository {

    UploadedFile save(UploadedFile file);

    /** 식별자에 해당하는 기록. 없는 식별자는 빠진다. */
    List<UploadedFile> listByIds(Collection<UUID> ids);

    /**
     * 연결을 확정하려고 행을 잠근다(호출자 트랜잭션이 끝날 때까지). 주어진 식별자의 기록과, 그 발주사의 그 대상에 그 용도로 지금 연결된 기록을 함께 식별자 순으로 잠가
     * 같은 파일·대상을 동시에 바꾸는 요청을 한 줄로 세운다.
     */
    List<UploadedFile> lockForAttachment(Collection<UUID> ids, long organizationId, String purpose, String owner);
}
