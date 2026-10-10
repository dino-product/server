package com.orbit.profile.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 계정의 가입 프로필 애그리게잇 루트. 계정마다 하나이며 auth가 발급한 계정 식별자로 찾는다. 프로필을 처음 입력하면 가입 미완료로 시작하고, 이름·연락처는 가입 중이든
 * 가입을 마친 뒤든 같은 규칙으로 바꿀 수 있다.
 */
public final class Profile {

    private final AccountId accountId;
    private PersonName name;
    private PhoneNumber phoneNumber;
    private SignupStatus status;
    private Instant signedUpAt;

    private Profile(
            AccountId accountId, PersonName name, PhoneNumber phoneNumber, SignupStatus status, Instant signedUpAt) {
        this.accountId = accountId;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.status = status;
        this.signedUpAt = signedUpAt;
    }

    /** 처음 입력한 프로필로 가입 미완료 프로필을 만든다. */
    public static Profile start(AccountId accountId, PersonName name, PhoneNumber phoneNumber) {
        return new Profile(
                Objects.requireNonNull(accountId, "accountId must not be null"),
                Objects.requireNonNull(name, "name must not be null"),
                Objects.requireNonNull(phoneNumber, "phoneNumber must not be null"),
                SignupStatus.PENDING_SIGNUP,
                null);
    }

    public static Profile reconstitute(
            AccountId accountId, PersonName name, PhoneNumber phoneNumber, SignupStatus status, Instant signedUpAt) {
        Objects.requireNonNull(status, "status must not be null");
        if ((status == SignupStatus.ACTIVE) != (signedUpAt != null)) {
            throw new IllegalArgumentException("only active profiles have signedUpAt");
        }
        return new Profile(
                Objects.requireNonNull(accountId, "accountId must not be null"),
                Objects.requireNonNull(name, "name must not be null"),
                Objects.requireNonNull(phoneNumber, "phoneNumber must not be null"),
                status,
                signedUpAt);
    }

    public void changeBasics(PersonName name, PhoneNumber phoneNumber) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber must not be null");
    }

    public SignupStep nextStep() {
        return status == SignupStatus.ACTIVE ? SignupStep.COMPLETED : SignupStep.TERMS;
    }

    public AccountId accountId() {
        return accountId;
    }

    public PersonName name() {
        return name;
    }

    public PhoneNumber phoneNumber() {
        return phoneNumber;
    }

    public SignupStatus status() {
        return status;
    }

    public Optional<Instant> signedUpAt() {
        return Optional.ofNullable(signedUpAt);
    }
}
