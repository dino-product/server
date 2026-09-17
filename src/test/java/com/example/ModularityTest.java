package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

@DisplayName("애플리케이션 모듈 구조")
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(SpringSettingsApplication.class);

    @Test
    @DisplayName("shared, user, auth 모듈만 구성한다")
    void containsExpectedModules() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder("shared", "user", "auth");
    }

    @Test
    @DisplayName("shared 공개 계약을 error와 openapi named interface로 구분한다")
    void exposesSharedContractsThroughNamedInterfaces() {
        var shared = modules.getModuleByName("shared").orElseThrow();

        assertThat(shared.getNamedInterfaces().stream()
                        .filter(namedInterface -> namedInterface.isNamed())
                        .map(namedInterface -> namedInterface.getName()))
                .containsExactlyInAnyOrder("error", "openapi");
    }

    @Test
    @DisplayName("모듈 경계와 의존성 규칙을 지킨다")
    void verifiesModularStructure() {
        modules.verify();
    }
}
