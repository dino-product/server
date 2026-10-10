package com.orbit.profile.adapter.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.MarketingConsent;
import com.orbit.profile.domain.PersonName;
import com.orbit.profile.domain.PhoneNumber;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.SignupStatus;
import com.orbit.profile.domain.TermsAgreement;

/** 계정 식별자를 기본 키로 쓴다. auth의 계정 테이블과는 모듈 경계를 넘는 외래 키를 두지 않는다. 연락처는 계정 사이에 중복을 허용해 유일 제약이 없다. */
@Entity
@Table(name = "profiles")
class ProfileJpaEntity {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "phone_number", nullable = false, length = 11)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SignupStatus status;

    @Column(name = "signed_up_at")
    private Instant signedUpAt;

    @Column(name = "marketing_agreed")
    private Boolean marketingAgreed;

    @Column(name = "marketing_changed_at")
    private Instant marketingChangedAt;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("id")
    private List<TermsAgreementJpaEntity> agreements = new ArrayList<>();

    protected ProfileJpaEntity() {}

    static ProfileJpaEntity from(Profile profile) {
        ProfileJpaEntity entity = new ProfileJpaEntity();
        entity.accountId = profile.accountId().value();
        entity.apply(profile);
        return entity;
    }

    void apply(Profile profile) {
        this.name = profile.name().value();
        this.phoneNumber = profile.phoneNumber().value();
        this.status = profile.status();
        this.signedUpAt = profile.signedUpAt().orElse(null);
        this.marketingAgreed =
                profile.marketingConsent().map(MarketingConsent::agreed).orElse(null);
        this.marketingChangedAt =
                profile.marketingConsent().map(MarketingConsent::changedAt).orElse(null);
        // 동의 기록은 도메인에서 추가만 되므로 이미 저장한 개수 뒤의 것만 새로 넣는다.
        List<TermsAgreement> domainAgreements = profile.agreements();
        for (int index = agreements.size(); index < domainAgreements.size(); index++) {
            agreements.add(TermsAgreementJpaEntity.from(this, domainAgreements.get(index)));
        }
    }

    Profile toDomain() {
        MarketingConsent marketingConsent =
                marketingAgreed == null ? null : new MarketingConsent(marketingAgreed, marketingChangedAt);
        return Profile.reconstitute(
                new AccountId(accountId),
                new PersonName(name),
                new PhoneNumber(phoneNumber),
                status,
                signedUpAt,
                agreements.stream().map(TermsAgreementJpaEntity::toDomain).toList(),
                marketingConsent);
    }
}
