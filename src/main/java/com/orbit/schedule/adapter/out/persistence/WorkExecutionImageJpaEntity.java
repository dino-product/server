package com.orbit.schedule.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.orbit.schedule.domain.CompletionReport;
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 완료보고 사진 한 장. 테이블 구조는 팀 ERD의 {@code Work_Execution_Images}를 따른다. 작업 전·후 구분({@link Kind})과 저장소 식별자를 담고, 같은 구분 안의
 * 순서는 {@code id} 순이다. {@code image_url}은 이름과 달리 주소가 아니라 media 모듈의 파일 식별자(UUID)를 담는다(주소는 조회 때 서명, ADR-007). 컬럼 이름은 ERD를
 * 따르며 바꾸려면 ERD와 함께 바꾼다.
 *
 * <p>ERD에는 {@code created_at}만 있지만 규약대로 {@code BaseTimeEntity}를 상속해 {@code updated_at}도 가진다(ERD에 추가 예정).
 */
@Entity
@Table(name = "work_execution_images", indexes = @Index(name = "ix_work_execution_images_work", columnList = "work_id"))
class WorkExecutionImageJpaEntity extends BaseTimeEntity {

    /** ERD {@code image_type}: 작업 전·후. */
    enum Kind {
        BEFORE,
        AFTER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private WorkJpaEntity work;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 10)
    private Kind kind;

    @Column(name = "image_url", nullable = false, length = CompletionReport.MAX_PHOTO_ID_LENGTH)
    private String imageUrl;

    protected WorkExecutionImageJpaEntity() {}

    WorkExecutionImageJpaEntity(WorkJpaEntity work, Kind kind, String imageUrl) {
        this.work = work;
        this.kind = kind;
        this.imageUrl = imageUrl;
    }

    Long id() {
        return id;
    }

    Kind kind() {
        return kind;
    }

    String imageUrl() {
        return imageUrl;
    }
}
