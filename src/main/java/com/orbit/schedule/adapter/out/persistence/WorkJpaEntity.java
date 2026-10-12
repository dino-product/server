package com.orbit.schedule.adapter.out.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.orbit.schedule.domain.ActualPaymentMethod;
import com.orbit.schedule.domain.Cancellation;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 작업(Work) 애그리게잇 루트의 JPA Entity. 테이블 구조는 팀 ERD의 {@code Work}를 따르고 매핑 결정(현재 일정 비정규화, 완료보고 인라인·사진 분리, 배정 순번,
 * 정정 기록 미저장)은 {@link WorkMapper}가 원본이다. 값 검증은 Domain이 하며 이 Entity는 컬럼 제약만 선언한다.
 *
 * <p>이 Entity와 자식 Entity는 모두 {@code BaseTimeEntity}를 상속한다. ERD의 배정 이력·사진 테이블에는 감사 컬럼이, 활성 작업 조회용 인덱스
 * {@code (company_id, technician_id, status)}와 자식 테이블의 {@code work_id} 인덱스는 ERD에 빠져 있어 Entity 기준으로 ERD에 추가한다(운영은
 * {@code ddl-auto: none}이라 ERD에 올라야 실제 스키마에 반영된다).
 */
@Entity
@Table(
        name = "work",
        indexes = @Index(name = "ix_work_company_technician_status", columnList = "company_id, technician_id, status"))
class WorkJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "job_type_id")
    private Long jobTypeId;

    @Column(name = "name", nullable = false, length = Work.MAX_NAME_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkStatus status;

    @Column(name = "technician_id")
    private Long technicianId;

    @Column(name = "scheduled_start_at")
    private Instant scheduledStartAt;

    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "expected_duration")
    private Duration expectedDuration;

    @Column(name = "customer_name", length = CustomerInfo.MAX_NAME_LENGTH)
    private String customerName;

    @Column(name = "customer_phone", length = CustomerInfo.MAX_PHONE_LENGTH)
    private String customerPhone;

    @Column(name = "customer_address", length = CustomerInfo.MAX_ADDRESS_LENGTH)
    private String customerAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "fee")
    private Long fee;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason", length = Cancellation.MAX_REASON_LENGTH)
    private String cancelReason;

    @Column(name = "used_parts", length = CompletionReport.MAX_USED_PARTS_LENGTH)
    private String usedParts;

    @Column(name = "work_note", length = CompletionReport.MAX_WORK_NOTE_LENGTH)
    private String workNote;

    @Column(name = "actual_fee")
    private Long actualFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "actual_payment_method", length = 20)
    private ActualPaymentMethod actualPaymentMethod;

    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<WorkAssignmentJpaEntity> assignments = new ArrayList<>();

    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<WorkExecutionImageJpaEntity> images = new ArrayList<>();

    protected WorkJpaEntity() {}

    WorkJpaEntity(Long companyId, Long createdBy, String name, WorkStatus status) {
        this.companyId = companyId;
        this.createdBy = createdBy;
        this.name = name;
        this.status = status;
    }

    Long id() {
        return id;
    }

    long version() {
        return version;
    }

    Long companyId() {
        return companyId;
    }

    Long createdBy() {
        return createdBy;
    }

    Long jobTypeId() {
        return jobTypeId;
    }

    String name() {
        return name;
    }

    WorkStatus status() {
        return status;
    }

    Long technicianId() {
        return technicianId;
    }

    Instant scheduledStartAt() {
        return scheduledStartAt;
    }

    Duration expectedDuration() {
        return expectedDuration;
    }

    String customerName() {
        return customerName;
    }

    String customerPhone() {
        return customerPhone;
    }

    String customerAddress() {
        return customerAddress;
    }

    PaymentMethod paymentMethod() {
        return paymentMethod;
    }

    Long fee() {
        return fee;
    }

    Instant startedAt() {
        return startedAt;
    }

    Instant completedAt() {
        return completedAt;
    }

    Instant cancelledAt() {
        return cancelledAt;
    }

    Long cancelledBy() {
        return cancelledBy;
    }

    String cancelReason() {
        return cancelReason;
    }

    String usedParts() {
        return usedParts;
    }

    String workNote() {
        return workNote;
    }

    Long actualFee() {
        return actualFee;
    }

    ActualPaymentMethod actualPaymentMethod() {
        return actualPaymentMethod;
    }

    List<WorkAssignmentJpaEntity> assignments() {
        return assignments;
    }

    List<WorkExecutionImageJpaEntity> images() {
        return images;
    }

    /** 작업명·작업 유형·고객정보·결제정보. 기본정보 수정과 저장 때 Domain 값으로 덮어쓴다. */
    void updateDetails(
            String newName,
            Long newJobTypeId,
            String newCustomerName,
            String newCustomerPhone,
            String newCustomerAddress,
            PaymentMethod newPaymentMethod,
            Long newFee) {
        this.name = newName;
        this.jobTypeId = newJobTypeId;
        this.customerName = newCustomerName;
        this.customerPhone = newCustomerPhone;
        this.customerAddress = newCustomerAddress;
        this.paymentMethod = newPaymentMethod;
        this.fee = newFee;
    }

    /** 상태와 현재 일정(없으면 null 셋). 최신 배정 이력의 사본이므로 이력과 함께 바꾼다. */
    void updateStatusAndSchedule(
            WorkStatus newStatus, Long newTechnicianId, Instant newScheduledStartAt, Duration newExpectedDuration) {
        this.status = newStatus;
        this.technicianId = newTechnicianId;
        this.scheduledStartAt = newScheduledStartAt;
        this.expectedDuration = newExpectedDuration;
    }

    /** 시작·완료·취소 기록. 정정으로 치워진 값은 null로 넘어온다. */
    void updateProgress(
            Instant newStartedAt,
            Instant newCompletedAt,
            Instant newCancelledAt,
            Long newCancelledBy,
            String newCancelReason) {
        this.startedAt = newStartedAt;
        this.completedAt = newCompletedAt;
        this.cancelledAt = newCancelledAt;
        this.cancelledBy = newCancelledBy;
        this.cancelReason = newCancelReason;
    }

    /** 완료보고 스칼라 항목. 보고가 없으면 null 넷. 사진은 {@link #images()}를 바꾼다. */
    void updateCompletionReport(
            String newUsedParts, String newWorkNote, Long newActualFee, ActualPaymentMethod newActualPaymentMethod) {
        this.usedParts = newUsedParts;
        this.workNote = newWorkNote;
        this.actualFee = newActualFee;
        this.actualPaymentMethod = newActualPaymentMethod;
    }
}
