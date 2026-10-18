package com.orbit.schedule.domain;

/** 요청자가 본 작업 버전이 지금 버전과 다를 때. 그 사이 다른 사람이 작업을 바꿨으므로 상태·입력 오류와 구분해 최신 정보를 다시 보게 한다. */
public final class StaleWorkVersionException extends RuntimeException {

    public StaleWorkVersionException(long expectedVersion, long currentVersion) {
        super("work version " + expectedVersion + " is stale; current version is " + currentVersion);
    }
}
