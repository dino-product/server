package com.orbit.organization.adapter.out.code;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.domain.CompanyCode;

@Component
public final class SecureRandomCompanyCodeGenerator implements CompanyCodeGenerator {
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int CODE_LENGTH = 8;

    private final RandomGenerator random;

    public SecureRandomCompanyCodeGenerator() {
        this(new SecureRandom());
    }

    SecureRandomCompanyCodeGenerator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    @Override
    public CompanyCode generate() {
        var characters = new char[CODE_LENGTH];
        for (int index = 0; index < characters.length; index++) {
            characters[index] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new CompanyCode(new String(characters));
    }
}
