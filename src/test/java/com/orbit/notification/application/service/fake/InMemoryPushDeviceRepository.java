package com.orbit.notification.application.service.fake;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.orbit.notification.application.port.out.PushDeviceRepository;
import com.orbit.notification.domain.PushDevice;

/** 앱 푸시 기기의 메모리 구현. 토큰마다 한 기기이며 같은 토큰을 다시 등록하면 같은 식별자로 계정·플랫폼·등록 시각을 바꾼다. */
public class InMemoryPushDeviceRepository implements PushDeviceRepository {

    private final Map<String, PushDevice> byToken = new LinkedHashMap<>();
    private long sequence;

    @Override
    public PushDevice register(PushDevice device) {
        Long id = Optional.ofNullable(byToken.get(device.token()))
                .flatMap(PushDevice::id)
                .orElseGet(() -> ++sequence);
        PushDevice stored = PushDevice.reconstitute(
                id, device.accountId(), device.platform(), device.token(), device.registeredAt());
        byToken.put(device.token(), stored);
        return stored;
    }

    @Override
    public boolean deleteOwnedBy(Long pushDeviceId, Long accountId) {
        return byToken.values()
                .removeIf(device -> device.id().orElseThrow().equals(pushDeviceId) && device.isOwnedBy(accountId));
    }

    @Override
    public List<PushDevice> listByAccountId(Long accountId) {
        return byToken.values().stream()
                .filter(device -> device.isOwnedBy(accountId))
                .sorted(Comparator.comparing(device -> device.id().orElseThrow()))
                .toList();
    }

    @Override
    public void deleteByToken(String token) {
        byToken.remove(token);
    }

    public Optional<PushDevice> findByToken(String token) {
        return Optional.ofNullable(byToken.get(token));
    }

    public int count() {
        return byToken.size();
    }
}
