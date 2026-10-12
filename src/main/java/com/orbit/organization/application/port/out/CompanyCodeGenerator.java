package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.CompanyCode;

/** 회사 코드 후보를 무작위로 만든다. 기존 코드와의 중복 여부는 호출자가 확인한다. */
public interface CompanyCodeGenerator {

    CompanyCode generate();
}
