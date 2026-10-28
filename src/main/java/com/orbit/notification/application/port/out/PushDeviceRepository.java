package com.orbit.notification.application.port.out;

import com.orbit.notification.domain.PushDevice;

/** 앱 푸시 기기 저장소. 기기는 토큰마다 한 행이다. */
public interface PushDeviceRepository {

    /**
     * 토큰의 기기를 이 등록의 계정·플랫폼·등록 시각으로 저장하고 저장된 기기를 돌려준다. 다른 계정의 기기였으면 이 계정으로 넘긴다. 같은 토큰을 동시에 등록해도 한 행만
     * 남고 나중에 저장한 등록이 이긴다.
     */
    PushDevice register(PushDevice device);

    /** 이 계정의 기기일 때만 지운다. 없거나 다른 계정의 기기면 지우지 않고 false다. */
    boolean deleteOwnedBy(Long pushDeviceId, Long accountId);
}
