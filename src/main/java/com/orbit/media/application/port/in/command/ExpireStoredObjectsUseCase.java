package com.orbit.media.application.port.in.command;

import com.orbit.media.application.port.in.command.dto.ExpireStoredObjectsCommand;

/** 만료로 바뀐 파일의 저장소 객체에 만료 표시를 남긴다. 만료 기록이 커밋된 뒤에만 부른다. */
public interface ExpireStoredObjectsUseCase {

    void expire(ExpireStoredObjectsCommand command);
}
