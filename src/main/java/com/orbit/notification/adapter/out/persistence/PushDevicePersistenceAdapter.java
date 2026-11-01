package com.orbit.notification.adapter.out.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.notification.application.port.out.PushDeviceRepository;
import com.orbit.notification.domain.PushDevice;

/** 등록·해제는 부른 쪽 트랜잭션에 참여한다. 발신 대기열 디스패처는 트랜잭션 없이 부르므로 기기 조회·등록 해제 기기 삭제는 없으면 따로 연다. */
@Repository
class PushDevicePersistenceAdapter implements PushDeviceRepository {

    private final SpringDataPushDeviceRepository repository;

    PushDevicePersistenceAdapter(SpringDataPushDeviceRepository repository) {
        this.repository = repository;
    }

    @Override
    public PushDevice register(PushDevice device) {
        if (device.id().isPresent()) {
            throw new IllegalArgumentException("only new registrations can be saved");
        }
        repository.upsert(device.accountId(), device.platform().name(), device.token(), device.registeredAt());
        return repository
                .findByToken(device.token())
                .orElseThrow(() -> new IllegalStateException("registered push device must exist"))
                .toDomain();
    }

    @Override
    public boolean deleteOwnedBy(Long pushDeviceId, Long accountId) {
        return repository.deleteOwnedBy(pushDeviceId, accountId) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PushDevice> listByAccountId(Long accountId) {
        return repository.findByAccountIdOrderById(accountId).stream()
                .map(PushDeviceJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByToken(String token) {
        repository.deleteWithToken(token);
    }

    @Override
    public void deleteByAccountId(Long accountId) {
        repository.deleteOfAccount(accountId);
    }
}
