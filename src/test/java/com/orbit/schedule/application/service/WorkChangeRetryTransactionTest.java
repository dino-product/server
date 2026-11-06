package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * 저장 자리의 충돌을 {@link WorkVersions#retryOnConflict}로 다시 실행하는 서비스는 트랜잭션 없이 실행해야 한다. 트랜잭션을 붙이면 트랜잭션 부분이 그
 * 트랜잭션에 참여해, 충돌한 뒤 새로 읽지 못하고 다시 실행 없이 409로 끝난다(서비스 단위 테스트는 트랜잭션이 없어 이를 잡지 못한다).
 */
@DisplayName("충돌을 다시 실행하는 서비스의 트랜잭션")
class WorkChangeRetryTransactionTest {

    @Test
    @DisplayName("다시 실행하는 서비스는 알려진 여섯 개이고, 공개 메서드에 트랜잭션이 없다")
    void retryingServicesRunWithoutTransaction() {
        List<Class<?>> retrying = new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.orbit.schedule.application.service")
                        .stream()
                        .filter(javaClass -> javaClass.getMethodCallsFromSelf().stream()
                                .anyMatch(call -> call.getTargetOwner().isEquivalentTo(WorkVersions.class)
                                        && call.getName().equals("retryOnConflict")))
                        .map(JavaClass::reflect)
                        .toList();

        assertThat(retrying)
                .extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder(
                        "AcceptWorkService",
                        "CancelWorkService",
                        "RejectWorkService",
                        "StartWorkService",
                        "SubmitCompletionReportService",
                        "UpdateWorkDetailsService");
        AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();
        for (Class<?> service : retrying) {
            List<Method> publicMethods = Arrays.stream(service.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
                    .toList();
            assertThat(publicMethods).as(service.getSimpleName()).isNotEmpty().allSatisfy(method -> assertThat(
                            attributes.getTransactionAttribute(method, service))
                    .as(method.toString())
                    .isNull());
        }
    }
}
