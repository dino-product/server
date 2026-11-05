package com.orbit.schedule.application.service;

import com.orbit.schedule.application.port.out.SiteCoordinatesPort;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.Work;

/**
 * 작업 현장의 좌표를 채우는 공통 단계. 기본주소가 있는데 좌표가 없을 때만 외부 지오코딩을 부르므로, 기본주소가 그대로인 수정(이전 좌표를 이어받음)은 다시 부르지
 * 않고 좌표를 구하지 못했던 작업은 다음 저장 때 다시 시도한다. 구하지 못하면 좌표 없이 둔다. 외부 호출이라 작업 입력 검증 뒤, 작업 유형 확인(유형 행 공유 잠금)과
 * 기사 일정 잠금 전에 불러 잠금을 쥔 채 기다리지 않는다. 그래서 그 뒤 단계(유형·담당기사 확인, 겹침 판정)에서 거부·보류되는 요청도 조회한다.
 */
final class SiteLocations {

    private SiteLocations() {}

    static void locate(SiteCoordinatesPort siteCoordinatesPort, Work work) {
        work.customerInfo()
                .address()
                .filter(SiteAddress::needsCoordinates)
                .flatMap(SiteAddress::baseAddress)
                .flatMap(siteCoordinatesPort::find)
                .ifPresent(work::locateSite);
    }
}
