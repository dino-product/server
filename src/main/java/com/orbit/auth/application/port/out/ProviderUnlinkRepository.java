package com.orbit.auth.application.port.out;

import java.util.List;

import com.orbit.auth.domain.ProviderUnlink;

/** 닫힌 계정의 제공자 연결 해제할 일을 해제가 끝날 때까지 보관한다. */
public interface ProviderUnlinkRepository {

    void saveAll(List<ProviderUnlink> unlinks);
}
