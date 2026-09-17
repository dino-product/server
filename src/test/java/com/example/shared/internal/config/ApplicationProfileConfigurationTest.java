package com.example.shared.internal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

class ApplicationProfileConfigurationTest {

    @Test
    void doesNotActivateLocalProfileWhenNoProfileIsSpecified() {
        try (ConfigurableApplicationContext context = runApplication()) {
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
        }
    }

    @Test
    void localProfileUsesDisposableSchemaAndLocalDatabaseDefaults() {
        try (ConfigurableApplicationContext context = runApplication("--spring.profiles.active=local")) {
            Environment environment = context.getEnvironment();

            assertThat(environment.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:54321/postgres");
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
            assertThat(environment.getProperty("springdoc.api-docs.enabled")).isEqualTo("true");
        }
    }

    @Test
    void prodProfileRequiresDatabaseConnectionValues() {
        try (ConfigurableApplicationContext context = runApplication("--spring.profiles.active=prod")) {
            Environment environment = context.getEnvironment();

            assertThatThrownBy(() -> environment.getRequiredProperty("spring.datasource.url"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DATABASE_URL");
            assertThatThrownBy(() -> environment.getRequiredProperty("spring.datasource.username"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DATABASE_USERNAME");
            assertThatThrownBy(() -> environment.getRequiredProperty("spring.datasource.password"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DATABASE_PASSWORD");
        }
    }

    @Test
    void prodProfileDisablesSchemaChangesAndOpenApi() {
        try (ConfigurableApplicationContext context = runApplication(
                "--spring.profiles.active=prod",
                "--DATABASE_URL=jdbc:postgresql://prod.example:5432/app",
                "--DATABASE_USERNAME=app",
                "--DATABASE_PASSWORD=secret")) {
            Environment environment = context.getEnvironment();

            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
            assertThat(environment.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        }
    }

    @Test
    void prodProfileCannotBeOverriddenByLocalProfile() {
        try (ConfigurableApplicationContext context = runApplication("--spring.profiles.active=prod,local")) {
            Environment environment = context.getEnvironment();

            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
            assertThat(environment.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
            assertThatThrownBy(() -> environment.getRequiredProperty("spring.datasource.url"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DATABASE_URL");
        }
    }

    private ConfigurableApplicationContext runApplication(String... arguments) {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

        return new SpringApplicationBuilder(ProfileTestApplication.class)
                .environment(environment)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .run(arguments);
    }

    @Configuration(proxyBeanMethods = false)
    static class ProfileTestApplication {}
}
