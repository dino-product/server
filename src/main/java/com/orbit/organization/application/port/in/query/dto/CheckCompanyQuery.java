package com.orbit.organization.application.port.in.query.dto;

/** {@code companyCode}는 사용자가 입력한 그대로다. 정규화는 서비스가 한다. */
public record CheckCompanyQuery(Long accountId, String companyCode) {}
