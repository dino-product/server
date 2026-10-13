package com.orbit.organization.application.port.out;

import java.util.Optional;

import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.RetiredCompanyCode;

public interface OrganizationRepository {

    Organization save(Organization organization);

    Optional<Organization> findById(OrganizationId id);

    /** 현재 회사 코드가 이 코드인 발주사. 폐기 코드로는 찾지 않는다. */
    Optional<Organization> findByCode(CompanyCode code);

    /**
     * 발주사 행을 트랜잭션 끝까지 잠가, 같은 발주사를 바꾸는 요청을 한 줄로 세운다(회사 코드 변경·발주사 정보 수정, 총관리자 지정·해제, 유형 생성·수정·삭제). 잠근 뒤에 읽어야 앞 요청이
     * 커밋한 값을 본다. 총관리자 지정·해제는 총관리자 확인보다 먼저 잡아야 서로를 동시에 해제해 총관리자가 0명이 되는 경합을 막는다. 발주사가 없으면 아무것도 잠그지
     * 않는다(요청자 소속도 없으므로 거부된다). 유형 생성·수정·삭제도 잠근 뒤 이름 중복·삭제 여부를 확인해, 중복 생성이나 삭제와 엇갈린
     * 수정이 삭제된 유형을 되살리는 경합을 막는다.
     *
     * @throws OrganizationBusyException 앞선 변경이 대기 한도 안에 끝나지 않아 잠그지 못했을 때
     */
    void lock(OrganizationId id);

    /** 어느 발주사의 현재 코드나 폐기 코드로 발급된 적 있는지 한 번에 확인한다. */
    boolean existsIssuedCode(CompanyCode code);

    void saveRetiredCode(RetiredCompanyCode retiredCode);

    /** 회사 코드 변경으로 폐기된 코드인지 확인한다. */
    boolean existsRetiredCode(CompanyCode code);
}
