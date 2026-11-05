package com.orbit.schedule.application.port.out;

import java.util.Optional;

import com.orbit.schedule.domain.SiteCoordinates;

/**
 * 현장 기본주소를 좌표로 바꾸는 출력 포트(지오코딩). 좌표는 길찾기를 돕는 부가 정보라 구하지 못해도 작업 저장을 막지 않는다. 그래서 주소를 찾지 못했거나 외부
 * 서비스가 실패·지연되면 예외 대신 빈 값을 돌려준다. 주소는 null이 아니고 비어 있지 않다.
 */
public interface SiteCoordinatesPort {

    Optional<SiteCoordinates> find(String baseAddress);
}
