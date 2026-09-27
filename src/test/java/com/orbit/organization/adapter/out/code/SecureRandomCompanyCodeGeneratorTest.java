package com.orbit.organization.adapter.out.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

class SecureRandomCompanyCodeGeneratorTest {
    @Test
    void mapsEveryRandomIndexToTheCrockfordAlphabet() {
        var random = mock(RandomGenerator.class);
        var index = new AtomicInteger();
        when(random.nextInt(32)).thenAnswer(invocation -> index.getAndIncrement() % 32);
        var generator = new SecureRandomCompanyCodeGenerator(random);

        var codes = new StringBuilder();
        for (int count = 0; count < 4; count++) {
            codes.append(generator.generate().value());
        }

        assertThat(codes.toString()).isEqualTo("0123456789ABCDEFGHJKMNPQRSTVWXYZ");
        assertThat(index).hasValue(32);
    }

    @Test
    void defaultGeneratorProducesEightValidCharacters() {
        var code = new SecureRandomCompanyCodeGenerator().generate();

        assertThat(code.value()).matches("[0-9A-HJKMNP-TV-Z]{8}");
    }
}
