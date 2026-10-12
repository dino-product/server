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

import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 완료보고 사진 한 장. 테이블 구조는 팀 ERD의 {@code Work_Execution_Images}를 따른다. 작업 전·후 구분({@link Kind})과 저장소 식별자를 담고, 같은 구분 안의
 * 순서는 {@code id} 순이다. 사진 업로드 방식이 정해지기 전까지 {@code image_url}에는 Domain이 받은 사진 식별자 문자열을 그대로 둔다.
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

    @Column(name = "image_url", nullable = false, length = 500)
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
