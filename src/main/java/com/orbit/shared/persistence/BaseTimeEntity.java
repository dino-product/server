package com.orbit.shared.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 생성·수정 시각 감사 컬럼({@code created_at}, {@code updated_at})을 가진 JPA Entity의 공통 부모. 두 컬럼이 있는 테이블의 Entity는 이 클래스를
 * 상속하고 직접 선언하지 않는다.
 *
 * <p>값은 Spring Data 감사 리스너가 저장·갱신 때 채우며, 시각은 shared가 등록한 주입 {@code Clock}(UTC, 마이크로초)에서 가져온다. 감사 컬럼은
 * 저장소 운영 정보라 Domain 모델에 없고 Domain으로 변환하지 않는다. 등록 시각·완료 시각처럼 업무 규칙이 쓰는 시각은 이 컬럼으로 대체하지 않고 Domain이
 * 정한 값을 별도 컬럼에 저장한다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BaseTimeEntity() {}

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
