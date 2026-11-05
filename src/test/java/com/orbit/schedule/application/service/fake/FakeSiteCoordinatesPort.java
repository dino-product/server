package com.orbit.schedule.application.service.fake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.orbit.schedule.application.port.out.SiteCoordinatesPort;
import com.orbit.schedule.domain.SiteCoordinates;

/**
 * 서비스 단위 테스트용 SiteCoordinatesPort. 스캔되지 않도록 애너테이션을 붙이지 않는다. 등록한 기본주소만 좌표를 찾고, 그 밖의 주소는 찾지 못한 것(실제 포트의
 * 실패와 같은 빈 값)으로 본다. 조회한 주소를 순서대로 기록해 다시 부르지 않아야 하는 경우를 확인하게 한다.
 */
public class FakeSiteCoordinatesPort implements SiteCoordinatesPort {

    private final Map<String, SiteCoordinates> coordinates = new HashMap<>();
    private final List<String> lookups = new ArrayList<>();

    public void givenCoordinates(String baseAddress, SiteCoordinates siteCoordinates) {
        coordinates.put(baseAddress, siteCoordinates);
    }

    public List<String> lookups() {
        return List.copyOf(lookups);
    }

    @Override
    public Optional<SiteCoordinates> find(String baseAddress) {
        Objects.requireNonNull(baseAddress, "fake port received null baseAddress");
        lookups.add(baseAddress);
        return Optional.ofNullable(coordinates.get(baseAddress));
    }
}
