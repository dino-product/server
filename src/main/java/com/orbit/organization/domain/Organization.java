package com.orbit.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 발주사. 총관리자는 직원 소속의 표시로 나타내므로 발주사는 소속을 가리키지 않는다. */
public final class Organization {

    private final OrganizationId id;
    private OrganizationName name;
    private Industry industry;
    private CompanyCode code;

    private Organization(OrganizationId id, OrganizationName name, Industry industry, CompanyCode code) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.industry = Objects.requireNonNull(industry, "industry must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public static Organization create(OrganizationName name, Industry industry, CompanyCode code) {
        return new Organization(null, name, industry, code);
    }

    public static Organization reconstitute(
            OrganizationId id, OrganizationName name, Industry industry, CompanyCode code) {
        return new Organization(Objects.requireNonNull(id, "id must not be null"), name, industry, code);
    }

    /** 발주사명·업종을 함께 교체한다. 회사 코드는 이 메서드로 바꾸지 않는다. */
    public void changeInfo(OrganizationName name, Industry industry) {
        OrganizationName newName = Objects.requireNonNull(name, "name must not be null");
        Industry newIndustry = Objects.requireNonNull(industry, "industry must not be null");
        this.name = newName;
        this.industry = newIndustry;
    }

    /**
     * 회사 코드를 새 코드로 바꾸고 이전 코드를 폐기 코드로 돌려준다. 새 코드가 발급된 적 없는 코드인지는 호출자가 확인한다. 폐기 코드는 발주사 식별자로 남으므로
     * 저장된 발주사만 바꿀 수 있다.
     */
    public RetiredCompanyCode changeCode(CompanyCode newCode, Instant retiredAt) {
        Objects.requireNonNull(newCode, "newCode must not be null");
        if (newCode.equals(code)) {
            throw new IllegalArgumentException("newCode must differ from the current code");
        }
        OrganizationId organizationId =
                id().orElseThrow(() -> new IllegalStateException("only saved organizations can change their code"));
        RetiredCompanyCode retired = new RetiredCompanyCode(organizationId, code, retiredAt);
        this.code = newCode;
        return retired;
    }

    public Optional<OrganizationId> id() {
        return Optional.ofNullable(id);
    }

    public OrganizationName name() {
        return name;
    }

    public Industry industry() {
        return industry;
    }

    public CompanyCode code() {
        return code;
    }
}
