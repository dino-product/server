package com.orbit.schedule.application.service;

import java.util.Optional;

import com.orbit.schedule.application.port.out.SiteCoordinatesPort;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.SiteCoordinates;
import com.orbit.schedule.domain.Work;

/**
 * 작업 저장 트랜잭션 밖에서 미리 구한 현장 좌표와 그 조회에 쓴 기본주소. 외부 지오코딩이 느리거나 실패해도 DB 연결·잠금을 쥔 채 기다리지 않도록 등록·기본정보
 * 수정은 트랜잭션을 열기 전에 좌표를 구하고, 트랜잭션 안에서는 {@link #applyTo}로 담기만 한다. 트랜잭션 안의 작업이 조회에 쓴 주소와 같은 기본주소이고 아직 좌표가
 * 없을 때만 담으므로, 다른 주소의 좌표가 붙지 않는다. 좌표를 구하지 못했으면 좌표 없이 저장하고 다음 저장 때 다시 시도한다.
 *
 * @param baseAddress 조회에 쓴 기본주소. 조회하지 않았으면 null
 * @param coordinates 구한 좌표. 조회하지 않았거나 구하지 못했으면 null
 */
record SiteLookup(String baseAddress, SiteCoordinates coordinates) {

    private static final SiteLookup NONE = new SiteLookup(null, null);

    /** 조회하지 않음. */
    static SiteLookup none() {
        return NONE;
    }

    /**
     * 요청의 기본주소로 좌표를 구한다. 비었거나 길이 제한을 넘는 주소(트랜잭션 안에서 저장하지 못하거나 거부됨)는 조회하지 않는다. 기본주소는 {@link SiteAddress}처럼
     * 공백만 있는 값을 미입력으로 보고 나머지는 그대로 쓴다.
     */
    static SiteLookup lookUp(SiteCoordinatesPort siteCoordinatesPort, String requestedBaseAddress) {
        return lookupAddress(requestedBaseAddress)
                .map(address -> new SiteLookup(
                        address, siteCoordinatesPort.find(address).orElse(null)))
                .orElse(NONE);
    }

    /** 조회할 수 있는 기본주소. 비었거나 길이 제한을 넘으면 빈 값. */
    static Optional<String> lookupAddress(String requestedBaseAddress) {
        return Optional.ofNullable(requestedBaseAddress)
                .filter(address -> !address.isBlank() && address.length() <= SiteAddress.MAX_BASE_ADDRESS_LENGTH);
    }

    /** 작업의 기본주소가 조회에 쓴 주소와 같고 아직 좌표가 없으면 구한 좌표를 담는다. */
    void applyTo(Work work) {
        if (coordinates == null) {
            return;
        }
        work.customerInfo()
                .address()
                .filter(SiteAddress::needsCoordinates)
                .filter(address ->
                        address.baseAddress().filter(baseAddress::equals).isPresent())
                .ifPresent(address -> work.locateSite(coordinates));
    }
}
