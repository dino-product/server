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
     * 발주사 행을 트랜잭션 끝까지 잠가, 같은 발주사를 바꾸는 요청을 한 줄로 세운다(회사 코드 변경·발주사 정보 수정, 총관리자 지정·해제). 잠근 뒤에 읽어야 앞 요청이
     * 커밋한 값을 본다. 발주사가 없으면 아무것도 잠그지 않는다.
     */
    void lock(OrganizationId id);

    /** 어느 발주사의 현재 코드나 폐기 코드로 발급된 적 있는지 한 번에 확인한다. */
    boolean existsIssuedCode(CompanyCode code);

    void saveRetiredCode(RetiredCompanyCode retiredCode);

    /** 회사 코드 변경으로 폐기된 코드인지 확인한다. */
    boolean existsRetiredCode(CompanyCode code);
}
