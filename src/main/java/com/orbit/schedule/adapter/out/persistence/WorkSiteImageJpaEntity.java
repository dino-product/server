package com.orbit.schedule.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.orbit.schedule.domain.Work;
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 작업 등록·기본정보 수정 때 붙인 현장 사진 한 장. 순서는 {@code id} 순이다. {@code image_url}은 이름과 달리 주소가 아니라 media 모듈의 파일 식별자(UUID)를 담는다.
 * 주소는 조회 때마다 서명해 만든다(ADR-007). 컬럼 이름은 ERD·운영 스키마와 맞춰 두고 바꾸려면 ERD와 함께 바꾼다. 완료보고 사진({@link WorkExecutionImageJpaEntity})과
 * 생명주기가 달라 테이블을 나눈다(ERD에 없는 테이블이라 ERD에 추가한다).
 */
@Entity
@Table(name = "work_site_images", indexes = @Index(name = "ix_work_site_images_work", columnList = "work_id"))
class WorkSiteImageJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private WorkJpaEntity work;

    @Column(name = "image_url", nullable = false, length = Work.MAX_SITE_PHOTO_ID_LENGTH)
    private String imageUrl;

    protected WorkSiteImageJpaEntity() {}

    WorkSiteImageJpaEntity(WorkJpaEntity work, String imageUrl) {
        this.work = work;
        this.imageUrl = imageUrl;
    }

    Long id() {
        return id;
    }

    String imageUrl() {
        return imageUrl;
    }
}
