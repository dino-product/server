package com.orbit.shared.internal.config;

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

    private static final String EVENT_SCHEMA_INITIALIZATION =
            "spring.modulith.events.jdbc.schema-initialization.enabled";
    private static final String EVENT_RESUBMISSION = "app.events.resubmission.enabled";
    private static final String EVENT_REGISTRY_LOG_LEVEL =
            "logging.level.org.springframework.modulith.events.core.DefaultEventPublicationRegistry";

    @Test
    void doesNotActivateLocalProfileWhenNoProfileIsSpecified() {
        try (ConfigurableApplicationContext context = runApplication()) {
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            assertThat(context.getEnvironment().getProperty(EVENT_SCHEMA_INITIALIZATION))
                    .isEqualTo("false");
        }
    }

    @Test
    void localProfileUsesDisposableSchemaAndLocalDatabaseDefaults() {
        try (ConfigurableApplicationContext context = runApplication("--spring.profiles.active=local")) {
            Environment environment = context.getEnvironment();

            assertThat(environment.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:54321/postgres");
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
            assertThat(environment.getProperty(EVENT_SCHEMA_INITIALIZATION)).isEqualTo("true");
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
            assertThat(environment.getProperty(EVENT_SCHEMA_INITIALIZATION)).isEqualTo("false");
            assertThat(environment.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        }
    }

    @Test
    void resubmitsFailedEventPublicationsOutsideTestProfile() {
        try (ConfigurableApplicationContext context = runApplication()) {
            assertThat(context.getEnvironment().getProperty(EVENT_RESUBMISSION)).isEqualTo("true");
        }
        try (ConfigurableApplicationContext context = runApplication("--spring.profiles.active=prod")) {
            assertThat(context.getEnvironment().getProperty(EVENT_RESUBMISSION)).isEqualTo("true");
        }
    }

    @Test
    void hidesPeriodicStalePublicationCheckInfoLogs() {
        try (ConfigurableApplicationContext context = runApplication()) {
            assertThat(context.getEnvironment().getProperty(EVENT_REGISTRY_LOG_LEVEL))
                    .isEqualTo("WARN");
        }
        try (ConfigurableApplicationContext context = runApplication("--spring.profiles.active=prod")) {
            assertThat(context.getEnvironment().getProperty(EVENT_REGISTRY_LOG_LEVEL))
                    .isEqualTo("WARN");
        }
    }

    @Test
    void prodProfileCannotBeOverriddenByLocalProfile() {
        try (ConfigurableApplicationContext context = runApplication("--spring.profiles.active=prod,local")) {
            Environment environment = context.getEnvironment();

            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
            assertThat(environment.getProperty(EVENT_SCHEMA_INITIALIZATION)).isEqualTo("false");
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
