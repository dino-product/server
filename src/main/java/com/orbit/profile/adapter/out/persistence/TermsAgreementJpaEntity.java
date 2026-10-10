package com.orbit.profile.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.orbit.profile.domain.TermsAgreement;
import com.orbit.profile.domain.TermsType;

/** 약관 동의 이력 한 줄. 추가만 하고 고치거나 지우지 않는다. 가입 공통 검사가 요청마다 계정별로 읽으므로 계정 식별자에 인덱스를 둔다. */
@Entity
@Table(
        name = "profile_terms_agreements",
        indexes = @Index(name = "idx_profile_terms_agreements_account_id", columnList = "account_id"))
class TermsAgreementJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private ProfileJpaEntity profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, length = 20)
    private TermsType type;

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "agreed_at", nullable = false)
    private Instant agreedAt;

    protected TermsAgreementJpaEntity() {}

    static TermsAgreementJpaEntity from(ProfileJpaEntity profile, TermsAgreement agreement) {
        TermsAgreementJpaEntity entity = new TermsAgreementJpaEntity();
        entity.profile = profile;
        entity.type = agreement.type();
        entity.version = agreement.version();
        entity.agreedAt = agreement.agreedAt();
        return entity;
    }

    TermsAgreement toDomain() {
        return new TermsAgreement(type, version, agreedAt);
    }
}
