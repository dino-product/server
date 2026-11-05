package com.orbit.schedule.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.orbit.schedule.application.port.out.ProgressBoardBusyException;
import com.orbit.schedule.application.port.out.ProgressBoardSnapshot;
import com.orbit.schedule.application.port.out.ProgressBoardSnapshotPort;
import com.orbit.schedule.domain.OrganizationId;

/** 저장하지 않고 매번 만드는 스냅샷 포트. 미리 준 스냅샷이 있으면 만들지 않고 돌려주고, 바쁨으로 정하면 거절한다. */
final class FakeProgressBoardSnapshots implements ProgressBoardSnapshotPort {

    private final List<OrganizationId> invalidated = new ArrayList<>();
    private ProgressBoardSnapshot stored;
    private boolean busy;

    void given(ProgressBoardSnapshot snapshot) {
        stored = snapshot;
    }

    void busy() {
        busy = true;
    }

    List<OrganizationId> invalidated() {
        return List.copyOf(invalidated);
    }

    @Override
    public ProgressBoardSnapshot load(
            OrganizationId organizationId, Instant from, Instant to, Supplier<ProgressBoardSnapshot> builder) {
        if (busy) {
            throw new ProgressBoardBusyException("busy");
        }
        return stored != null ? stored : builder.get();
    }

    @Override
    public void invalidate(OrganizationId organizationId) {
        invalidated.add(organizationId);
    }
}
