package com.example;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import jakarta.persistence.Entity;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class ArchitectureTest {

    private static final JavaClasses APPLICATION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example");

    @Test
    void domainDoesNotDependOnFrameworksOrOuterLayers() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "org.hibernate..",
                        "com.querydsl..",
                        "..application..",
                        "..adapter..")
                .because("Domain은 기술 프레임워크와 Application, Adapter에 의존하지 않는다")
                .check(APPLICATION_CLASSES);
    }

    @Test
    void applicationDoesNotDependOnAdaptersOrPersistenceTechnology() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..adapter..",
                        "jakarta.persistence..",
                        "org.springframework.data..",
                        "org.hibernate..",
                        "com.querydsl..")
                .because("Application은 출력 Port를 통해 영속성에 접근한다")
                .check(APPLICATION_CLASSES);
    }

    @Test
    void inputAdaptersDoNotAccessOutputPortsOrPersistence() {
        noClasses()
                .that()
                .resideInAPackage("..adapter.in..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..application.port.out..",
                        "..adapter.out..",
                        "jakarta.persistence..",
                        "org.springframework.data.repository..",
                        "org.springframework.data.jpa..",
                        "org.hibernate..",
                        "com.querydsl..")
                .because("HTTP와 이벤트 입력은 입력 Port를 통해 유스케이스를 실행한다")
                .check(APPLICATION_CLASSES);
    }

    @Test
    void inputAdaptersDoNotDependOnApplicationImplementations() {
        noClasses()
                .that()
                .resideInAPackage("..adapter.in..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith(org.springframework.stereotype.Service.class)
                .because("입력 Adapter는 Service 구현 대신 입력 Port에 의존한다")
                .check(APPLICATION_CLASSES);
    }

    @Test
    void jpaEntitiesStayInPersistenceAdapters() {
        classes()
                .that()
                .areAnnotatedWith(Entity.class)
                .should()
                .resideInAPackage("..adapter.out.persistence..")
                .because("JPA Entity는 Domain 모델 및 Web 응답과 분리한다")
                .check(APPLICATION_CLASSES);
    }
}
