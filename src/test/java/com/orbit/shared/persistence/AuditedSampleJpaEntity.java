package com.orbit.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@link BaseTimeEntity}의 감사 컬럼 동작을 확인하기 위한 테스트 전용 Entity. 테스트 클래스패스의 {@code com.orbit} 아래에 있어 Entity 스캔에 포함되며,
 * 운영 코드가 참조하지 않는다.
 */
@Entity
@Table(name = "audited_sample")
class AuditedSampleJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    protected AuditedSampleJpaEntity() {}

    AuditedSampleJpaEntity(String label) {
        this.label = label;
    }

    Long id() {
        return id;
    }

    void relabel(String newLabel) {
        this.label = newLabel;
    }
}
