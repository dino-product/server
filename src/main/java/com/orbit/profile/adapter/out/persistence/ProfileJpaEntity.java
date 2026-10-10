package com.orbit.profile.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.PersonName;
import com.orbit.profile.domain.PhoneNumber;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.SignupStatus;

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
    }

    Profile toDomain() {
        return Profile.reconstitute(
                new AccountId(accountId), new PersonName(name), new PhoneNumber(phoneNumber), status, signedUpAt);
    }
}
