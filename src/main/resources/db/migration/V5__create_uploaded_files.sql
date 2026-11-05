-- media: 업로드 파일 기록(ADR-007). 스키마의 원본은 이 파일이고 UploadedFileJpaEntity는 그 매핑이다(ADR-005). 컬럼 설명은 docs/domain/media.md의 테이블 항목.
-- 식별자는 media가 정하는 UUID라 IDENTITY가 아니다. 발주사·업로더 계정은 다른 모듈의 식별자라 FK를 두지 않는다.
-- purpose·owner_ref 길이는 공개 계약 UploadSpec.MAX_PURPOSE_LENGTH·StageRequest.MAX_OWNER_LENGTH와 같다.
create table uploaded_files (
    id uuid not null,
    version bigint not null,
    organization_id bigint not null,
    uploader_account_id bigint not null,
    purpose varchar(50) not null,
    object_key varchar(200) not null,
    content_type varchar(100) not null,
    size_bytes bigint not null,
    status varchar(20) not null,
    owner_ref varchar(100),
    attached_at timestamp(6) with time zone,
    retain_until timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint pk_uploaded_files primary key (id)
);

-- 연결 확정(attach)이 대상(발주사·용도·연결 대상)에 지금 연결된 파일을 잠가 읽는다.
create index ix_uploaded_files_owner on uploaded_files (organization_id, purpose, owner_ref);
