package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.util.ClassUtils;

import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.application.port.out.WorkTypeUsagePort;

/**
 * 진행 보드 캐시는 {@link com.orbit.schedule.WorkChanged}로만 무효화된다. 작업 행을 바꾸는 출력 포트를 받는 쓰기 서비스(읽기 전용 트랜잭션만 있는 조회는 뺌)가
 * 새로 생기면 이 테스트가 실패한다. 그 서비스가 저장한 모든 경우에 WorkChanged를 발행하는지 서비스 테스트로 확인한 뒤 목록에 더한다.
 */
@DisplayName("진행 보드 캐시 무효화 대상 쓰기 경로")
class ProgressBoardCacheInvalidationCoverageTest {

    /**
     * 각 서비스의 *ServiceTest가 RecordingEventPublisher로 WorkChanged 발행을 확인한다. 트랜잭션 밖에서 사진을 확인한 뒤 저장하는 등록·기본정보
     * 수정·완료보고 제출은 *Transaction이 저장·발행하며 CreateWorkServiceTest·UpdateWorkDetailsServiceTest·SubmitCompletionReportServiceTest가
     * 확인한다.
     */
    private static final Set<String> WRITERS_PUBLISHING_WORK_CHANGED = Set.of(
            "AcceptWorkService",
            "AssignWorkService",
            "CancelWorkService",
            "ChangeWorkAssignmentService",
            "ReassignWorkService",
            "RejectWorkService",
            "ReleaseDeletedWorkTypeService",
            "ReleaseTechnicianWorksService",
            "ReleaseWithdrawnTechnicianWorksService",
            "RequestCompletionReportService",
            "RescheduleWorkService",
            "StartWorkService",
            "UnassignWorkService",
            "CreateWorkTransaction",
            "SubmitCompletionReportTransaction",
            "UpdateWorkDetailsTransaction");

    /** 쓰기 포트로 작업을 읽기만 하고 저장하지 않는 쓰기 서비스. 저장은 *Transaction이 하거나(기본정보 수정), 작업이 아닌 기록만 쓴다(사진 발급). */
    private static final Set<String> READERS_OF_WRITE_PORTS =
            Set.of("IssueCompletionPhotoUploadService", "UpdateWorkDetailsService");

    private static final Set<Class<?>> WRITE_PORTS = Set.of(WorkRepository.class, WorkTypeUsagePort.class);

    @Test
    @DisplayName("작업을 저장하는 서비스·트랜잭션 빈은 모두 WorkChanged를 발행한다고 확인된 목록에 있다")
    void everyWorkWriterIsKnownToPublishWorkChanged() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        Set<String> writers = new TreeSet<>();
        for (BeanDefinition candidate :
                scanner.findCandidateComponents(getClass().getPackageName())) {
            Class<?> service = ClassUtils.resolveClassName(
                    candidate.getBeanClassName(), getClass().getClassLoader());
            boolean usesWritePort = Arrays.stream(service.getDeclaredConstructors())
                    .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                    .anyMatch(WRITE_PORTS::contains);
            if (usesWritePort && !readOnly(service) && !READERS_OF_WRITE_PORTS.contains(service.getSimpleName())) {
                writers.add(service.getSimpleName());
            }
        }

        assertThat(writers).containsExactlyInAnyOrderElementsOf(WRITERS_PUBLISHING_WORK_CHANGED);
    }

    /** 조회 서비스(공개 메서드가 있고 모두 읽기 전용 트랜잭션)는 쓰기 포트로 읽기만 한다. 공개 메서드가 없으면 조회로 보지 않는다. */
    private static boolean readOnly(Class<?> service) {
        AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();
        List<Method> publicMethods = Arrays.stream(service.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
                .toList();
        return !publicMethods.isEmpty()
                && publicMethods.stream().allMatch(method -> {
                    TransactionAttribute attribute = attributes.getTransactionAttribute(method, service);
                    return attribute != null && attribute.isReadOnly();
                });
    }
}
