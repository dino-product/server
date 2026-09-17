plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
    checkstyle
    jacoco
}

apply(from = "gradle/dependencies.gradle.kts")
apply(from = "gradle/querydsl.gradle.kts")
apply(from = "gradle/quality.gradle.kts")
apply(from = "gradle/testing.gradle.kts")

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "Reusable Spring Boot project settings template"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

springBoot {
    buildInfo()
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX

    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        importOrder("#", "java", "javax", "jakarta", "org", "com", "")
        forbidWildcardImports()
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("misc") {
        target(
            "*.gradle.kts",
            "gradle/**/*.gradle.kts",
            "gradle/**/*.toml",
            ".editorconfig",
            "config/**/*.xml"
        )
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }

    format("yaml") {
        target(
            "*.yml",
            "*.yaml",
            ".github/**/*.yml",
            ".github/**/*.yaml",
            "src/**/resources/**/*.yml",
            "src/**/resources/**/*.yaml"
        )
        trimTrailingWhitespace()
        leadingTabsToSpaces(2)
        endWithNewline()
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("application.jar")
    layered {
        enabled.set(true)
    }
}

tasks.named<Jar>("jar") {
    enabled = false
}
