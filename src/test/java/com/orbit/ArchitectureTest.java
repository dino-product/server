package com.orbit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import jakarta.persistence.Entity;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

class ArchitectureTest {

    private static final String ROOT_PACKAGE = "com.orbit";
    private static final String SHARED_MODULE = "shared";

    private static final JavaClasses APPLICATION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE);

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
    void domainDoesNotDependOnOtherBusinessModules() {
        classes()
                .that()
                .resideInAPackage("..domain..")
                .should(notDependOnOtherBusinessModules())
                .because("Domain은 다른 비즈니스 모듈의 공개 계약도 참조하지 않고 필요한 값만 받는다")
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

    private static ArchCondition<JavaClass> notDependOnOtherBusinessModules() {
        return new ArchCondition<>("다른 비즈니스 모듈 타입에 의존하지 않는다") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String ownModule = moduleOf(item.getPackageName());
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    String targetPackage =
                            dependency.getTargetClass().getBaseComponentType().getPackageName();
                    if (!targetPackage.startsWith(ROOT_PACKAGE + ".")) {
                        continue;
                    }
                    String targetModule = moduleOf(targetPackage);
                    if (!targetModule.equals(ownModule) && !targetModule.equals(SHARED_MODULE)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static String moduleOf(String packageName) {
        String rest = packageName.substring(ROOT_PACKAGE.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
