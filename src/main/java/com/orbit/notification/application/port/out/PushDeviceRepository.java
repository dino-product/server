package com.orbit.notification.application.port.out;

import java.util.List;

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

    /** 계정에 등록된 기기를 등록 순서로 돌려준다. 부른 쪽 트랜잭션이 없으면 읽기 트랜잭션을 따로 연다. */
    List<PushDevice> listByAccountId(Long accountId);

    /** 제공자가 더 받지 않는 토큰의 기기를 지운다. 이미 없으면 그대로 둔다. 부른 쪽 트랜잭션이 없으면 따로 커밋한다. */
    void deleteByToken(String token);

    /** 계정의 기기를 모두 지운다. 없으면 그대로 둔다. */
    void deleteByAccountId(Long accountId);
}
