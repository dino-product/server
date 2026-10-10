package com.orbit.profile.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 계정의 가입 프로필 애그리게잇 루트. 계정마다 하나이며 auth가 발급한 계정 식별자로 찾는다. 프로필을 처음 입력하면 가입 미완료로 시작하고 필수 약관에 동의하면
 * 활성이 된다. 이름·연락처는 가입 중이든 가입을 마친 뒤든 같은 규칙으로 바꿀 수 있다. 약관 동의 기록은 추가만 한다.
 */
public final class Profile {

    private final AccountId accountId;
    private PersonName name;
    private PhoneNumber phoneNumber;
    private SignupStatus status;
    private Instant signedUpAt;
    private final List<TermsAgreement> agreements;
    private MarketingConsent marketingConsent;

    private Profile(
            AccountId accountId,
            PersonName name,
            PhoneNumber phoneNumber,
            SignupStatus status,
            Instant signedUpAt,
            List<TermsAgreement> agreements,
            MarketingConsent marketingConsent) {
        this.accountId = accountId;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.status = status;
        this.signedUpAt = signedUpAt;
        this.agreements = new ArrayList<>(agreements);
        this.marketingConsent = marketingConsent;
    }

    /** 처음 입력한 프로필로 가입 미완료 프로필을 만든다. */
    public static Profile start(AccountId accountId, PersonName name, PhoneNumber phoneNumber) {
        return new Profile(
                Objects.requireNonNull(accountId, "accountId must not be null"),
                Objects.requireNonNull(name, "name must not be null"),
                Objects.requireNonNull(phoneNumber, "phoneNumber must not be null"),
                SignupStatus.PENDING_SIGNUP,
                null,
                List.of(),
                null);
    }

    public static Profile reconstitute(
            AccountId accountId,
            PersonName name,
            PhoneNumber phoneNumber,
            SignupStatus status,
            Instant signedUpAt,
            List<TermsAgreement> agreements,
            MarketingConsent marketingConsent) {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(agreements, "agreements must not be null");
        if ((status == SignupStatus.ACTIVE) != (signedUpAt != null)) {
            throw new IllegalArgumentException("only active profiles have signedUpAt");
        }
        return new Profile(
                Objects.requireNonNull(accountId, "accountId must not be null"),
                Objects.requireNonNull(name, "name must not be null"),
                Objects.requireNonNull(phoneNumber, "phoneNumber must not be null"),
                status,
                signedUpAt,
                agreements,
                marketingConsent);
    }

    public void changeBasics(PersonName name, PhoneNumber phoneNumber) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber must not be null");
    }

    /**
     * 필수 약관의 현재 버전에 동의한다. 이미 현재 버전에 동의한 약관은 다시 기록하지 않는다. 가입 미완료 프로필은 이 시각에 활성이 되고, 마케팅 동의 값이 아직
     * 없으면 동의하지 않은 상태로 시작한다. 활성 프로필이면 개정된 필수 약관의 재동의가 된다.
     */
    public void agreeToTerms(TermsVersions current, Instant agreedAt) {
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(agreedAt, "agreedAt must not be null");
        for (TermsType type : TermsType.values()) {
            if (type.required() && !hasAgreedToCurrent(type, current)) {
                agreements.add(new TermsAgreement(type, current.versionOf(type), agreedAt));
            }
        }
        if (marketingConsent == null) {
            marketingConsent = new MarketingConsent(false, agreedAt);
        }
        if (status == SignupStatus.PENDING_SIGNUP) {
            status = SignupStatus.ACTIVE;
            signedUpAt = agreedAt;
        }
    }

    /** 가입을 마친 프로필의 마케팅 수신 동의를 바꾼다. 동의하면 마케팅 약관의 현재 버전을 기록하고, 값이 그대로면 아무것도 바꾸지 않는다. */
    public void changeMarketingConsent(boolean agreed, TermsVersions current, Instant changedAt) {
        if (status != SignupStatus.ACTIVE) {
            throw new IllegalStateException("marketing consent can change only after signup");
        }
        if (marketingConsent != null && marketingConsent.agreed() == agreed) {
            return;
        }
        marketingConsent = new MarketingConsent(agreed, changedAt);
        if (agreed) {
            agreements.add(new TermsAgreement(TermsType.MARKETING, current.versionOf(TermsType.MARKETING), changedAt));
        }
    }

    /** 가입을 마쳤고 필수 약관마다 마지막 동의가 현재 버전이면 서비스를 쓸 수 있다. 선택 약관의 개정은 이용을 막지 않는다. */
    public boolean isUsable(TermsVersions current) {
        if (status != SignupStatus.ACTIVE) {
            return false;
        }
        for (TermsType type : TermsType.values()) {
            if (type.required() && !hasAgreedToCurrent(type, current)) {
                return false;
            }
        }
        return true;
    }

    public SignupStep nextStep(TermsVersions current) {
        return isUsable(current) ? SignupStep.COMPLETED : SignupStep.TERMS;
    }

    private boolean hasAgreedToCurrent(TermsType type, TermsVersions current) {
        String latest = null;
        for (TermsAgreement agreement : agreements) {
            if (agreement.type() == type) {
                latest = agreement.version();
            }
        }
        return current.versionOf(type).equals(latest);
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

    public List<TermsAgreement> agreements() {
        return List.copyOf(agreements);
    }

    public Optional<MarketingConsent> marketingConsent() {
        return Optional.ofNullable(marketingConsent);
    }
}
