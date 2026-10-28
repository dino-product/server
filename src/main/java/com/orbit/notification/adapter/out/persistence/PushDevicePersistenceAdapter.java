package com.orbit.notification.adapter.out.persistence;

import org.springframework.stereotype.Repository;

import com.orbit.notification.application.port.out.PushDeviceRepository;
import com.orbit.notification.domain.PushDevice;

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
}
