import org.gradle.api.artifacts.VersionCatalogsExtension

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun version(alias: String): String = libsCatalog.findVersion(alias).get().requiredVersion

configurations {
    named("compileOnly") {
        extendsFrom(configurations.getByName("annotationProcessor"))
    }
}

dependencies {
    add("implementation", platform("org.springframework.boot:spring-boot-dependencies:${version("spring-boot")}"))
    add("implementation", platform("org.springframework.modulith:spring-modulith-bom:${version("spring-modulith")}"))
    add("implementation", "org.springframework.boot:spring-boot-starter-webmvc")
    add("implementation", "org.springframework.boot:spring-boot-starter-validation")
    add("implementation", "org.springframework.boot:spring-boot-starter-data-jpa")
    add("implementation", "org.springframework.boot:spring-boot-starter-security")
    add("implementation", "org.springframework.boot:spring-boot-starter-actuator")
    add("implementation", "org.springframework.modulith:spring-modulith-starter-core")
    add("implementation", "org.springframework.modulith:spring-modulith-events-api")
    add("annotationProcessor", "org.springframework.boot:spring-boot-configuration-processor")

    add("implementation", "com.querydsl:querydsl-jpa:${version("querydsl")}:jakarta")
    add("annotationProcessor", "com.querydsl:querydsl-apt:${version("querydsl")}:jakarta")
    add("annotationProcessor", "jakarta.annotation:jakarta.annotation-api")
    add("annotationProcessor", "jakarta.persistence:jakarta.persistence-api")

    add("runtimeOnly", "org.postgresql:postgresql")

    add("implementation", "org.springdoc:springdoc-openapi-starter-webmvc-ui:${version("springdoc")}")
    add("implementation", "io.micrometer:micrometer-registry-prometheus")

    add("developmentOnly", "org.springframework.boot:spring-boot-docker-compose")
    add("developmentOnly", "com.github.gavlyukovskiy:p6spy-spring-boot-starter:${version("p6spy")}")

    add("compileOnly", "org.projectlombok:lombok")
    add("annotationProcessor", "org.projectlombok:lombok")

    add("testImplementation", platform("org.springframework.boot:spring-boot-dependencies:${version("spring-boot")}"))
    add("testImplementation", platform("org.springframework.modulith:spring-modulith-bom:${version("spring-modulith")}"))
    add("testImplementation", "org.springframework.boot:spring-boot-starter-data-jpa-test")
    add("testImplementation", "org.springframework.boot:spring-boot-starter-security-test")
    add("testImplementation", "org.springframework.boot:spring-boot-starter-validation-test")
    add("testImplementation", "org.springframework.boot:spring-boot-starter-webmvc-test")
    add("testImplementation", "org.springframework.modulith:spring-modulith-starter-test")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    add("testImplementation", "org.springframework.boot:spring-boot-testcontainers")
    add("testImplementation", "org.testcontainers:testcontainers-junit-jupiter")
    add("testImplementation", "org.testcontainers:testcontainers-postgresql")
}
