package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

@DisplayName("기사 일정 잠금을 쓰는 서비스의 트랜잭션")
class TechnicianScheduleLockIsolationTest {

    /**
     * 다른 모듈의 트랜잭션 안에서만 실행해 격리 수준을 지정할 수 없는 서비스. 바깥 트랜잭션을 요구하고(MANDATORY), 바깥이 READ COMMITTED보다 높은 격리 수준을
     * 지정했으면 실행 시 거부한다(각 서비스 단위 테스트가 확인).
     */
    private static final Set<String> CALLER_TRANSACTION_SERVICES = Set.of("ReleaseTechnicianWorksService");

    @Test
    @DisplayName("기사를 잠그는 서비스는 READ COMMITTED 트랜잭션으로 실행하거나, 다른 모듈의 트랜잭션에 참여하면 바깥 트랜잭션을 요구한다")
    void lockingServicesRunInReadCommitted() {
        List<Class<?>> lockingServices = new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.orbit.schedule.application.service")
                        .stream()
                        .filter(javaClass -> javaClass.getFields().stream()
                                .anyMatch(field -> field.getRawType().isEquivalentTo(LockTechnicianSchedulePort.class)))
                        .map(JavaClass::reflect)
                        .toList();

        assertThat(lockingServices)
                .extracting(Class::getSimpleName)
                .contains(
                        "AssignWorkService",
                        "ChangeWorkAssignmentService",
                        "CreateWorkService",
                        "ReassignWorkService",
                        "RescheduleWorkService",
                        "ReleaseTechnicianWorksService",
                        "ReleaseWithdrawnTechnicianWorksService");
        // 클래스 수준·합성 애너테이션까지 Spring이 실행 시 적용하는 규칙 그대로 판정한다.
        AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();
        for (Class<?> service : lockingServices) {
            List<Method> publicMethods = Arrays.stream(service.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
                    .toList();
            assertThat(publicMethods).as(service.getSimpleName()).isNotEmpty().allSatisfy(method -> {
                TransactionAttribute attribute = attributes.getTransactionAttribute(method, service);
                assertThat(attribute).as(method.toString()).isNotNull();
                if (CALLER_TRANSACTION_SERVICES.contains(service.getSimpleName())) {
                    assertThat(attribute.getPropagationBehavior())
                            .as(method.toString())
                            .isEqualTo(TransactionDefinition.PROPAGATION_MANDATORY);
                    return;
                }
                assertThat(attribute.getIsolationLevel())
                        .as(method.toString())
                        .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
            });
        }
    }
}
