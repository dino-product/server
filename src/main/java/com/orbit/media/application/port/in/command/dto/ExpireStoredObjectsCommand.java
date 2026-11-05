package com.orbit.media.application.port.in.command.dto;

import java.util.List;

/** @param objectKeys 만료 표시를 남길 객체 키 */
public record ExpireStoredObjectsCommand(List<String> objectKeys) {

    public ExpireStoredObjectsCommand {
        objectKeys = List.copyOf(objectKeys);
    }
}
