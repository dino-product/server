package com.orbit.media.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.orbit.media.application.port.in.command.ExpireStoredObjectsUseCase;
import com.orbit.media.application.port.in.command.dto.ExpireStoredObjectsCommand;
import com.orbit.media.application.port.out.ObjectStoragePort;
import com.orbit.media.application.port.out.ObjectStorageUnavailableException;

/**
 * 만료 기록이 커밋된 파일의 객체에 만료 표시를 남긴다. DB는 쓰지 않아 트랜잭션을 두지 않는다. 표시에 실패하면 객체가 남을 뿐 연결된 파일에는 영향이 없으므로 경고만 남기고
 * 다음 객체를 처리한다(재시도는 보관 배치의 범위).
 */
@Service
public class ExpireStoredObjectsService implements ExpireStoredObjectsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpireStoredObjectsService.class);

    private final ObjectStoragePort storage;

    public ExpireStoredObjectsService(ObjectStoragePort storage) {
        this.storage = storage;
    }

    @Override
    public void expire(ExpireStoredObjectsCommand command) {
        for (String key : command.objectKeys()) {
            try {
                storage.markExpired(key);
            } catch (ObjectStorageUnavailableException exception) {
                log.warn("failed to mark media object as expired: {}", key, exception);
            }
        }
    }
}
