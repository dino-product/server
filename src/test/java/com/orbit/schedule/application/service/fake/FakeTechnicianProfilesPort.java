package com.orbit.schedule.application.service.fake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.orbit.schedule.application.port.out.LoadTechnicianProfilesPort;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/**
 * 서비스 단위 테스트용 LoadTechnicianProfilesPort. 스캔되지 않도록 애너테이션을 붙이지 않는다. 테스트가 넣은 조직별 기사 표시 정보만 돌려주고(활성 기사 목록은
 * 활성으로 넣은 것만), 표시 정보를 찾은 요청의 기사 집합을 기록한다. 계약이 순서를 보장하지 않으므로 활성 기사 목록은 넣은 역순으로 돌려줘 호출자의 정렬
 * 누락이 드러나게 한다.
 */
public class FakeTechnicianProfilesPort implements LoadTechnicianProfilesPort {

    private final Map<OrganizationId, Map<TechnicianId, TechnicianProfile>> profiles = new HashMap<>();
    private final Map<OrganizationId, List<TechnicianProfile>> active = new HashMap<>();
    private final List<Set<TechnicianId>> lookups = new ArrayList<>();

    /** 비활성·해지된 기사 계약처럼 표시 정보만 있는 기사. */
    public void given(OrganizationId organizationId, TechnicianProfile profile) {
        profiles.computeIfAbsent(organizationId, ignored -> new HashMap<>()).put(profile.technicianId(), profile);
    }

    /** 활성 기사. 표시 정보도 함께 찾을 수 있다. */
    public void givenActive(OrganizationId organizationId, TechnicianProfile profile) {
        given(organizationId, profile);
        active.computeIfAbsent(organizationId, ignored -> new ArrayList<>()).add(profile);
    }

    public List<Set<TechnicianId>> lookups() {
        return List.copyOf(lookups);
    }

    @Override
    public List<TechnicianProfile> listActive(OrganizationId organizationId) {
        Objects.requireNonNull(organizationId, "fake port received null organizationId");
        return List.copyOf(active.getOrDefault(organizationId, List.of()).reversed());
    }

    @Override
    public Map<TechnicianId, TechnicianProfile> findProfiles(
            OrganizationId organizationId, Set<TechnicianId> technicianIds) {
        Objects.requireNonNull(organizationId, "fake port received null organizationId");
        lookups.add(Set.copyOf(technicianIds));
        Map<TechnicianId, TechnicianProfile> found = new HashMap<>();
        Map<TechnicianId, TechnicianProfile> ofOrganization = profiles.getOrDefault(organizationId, Map.of());
        for (TechnicianId technicianId : technicianIds) {
            TechnicianProfile profile = ofOrganization.get(technicianId);
            if (profile != null) {
                found.put(technicianId, profile);
            }
        }
        return Map.copyOf(found);
    }
}
