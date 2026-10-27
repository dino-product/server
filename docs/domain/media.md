<a id="media"></a>
# 파일 컨텍스트 (`media`) — Generic Subdomain

- 결정 배경: [ADR-006](../adr/006-file-storage.md). 사진 개수·용량·권한 같은 제품 규칙은 파일을 쓰는 업무 모듈의 Notion 정책서가 원본이다(예: [schedule](schedule.md#schedule-product)의 현장 사진·완료보고)
- 다른 컨텍스트와의 관계: [바운디드 컨텍스트 지도](bounded-contexts.md#media)

이 문서는 `media`의 공개 계약과 구현 결정을 소유한다. 누가 어떤 파일을 올리고 볼 수 있는지는 판단하지 않는다(호출 모듈의 몫).

## 개요

| 항목 | 내용 |
| --- | --- |
| 책임 | 업로드 주소 발급(presigned PUT)과 업로드 대기 기록, 연결 전 저장소 확인·복사, 업무 트랜잭션 안의 연결 확정·빠진 파일 만료, 내려받기 주소(presigned GET), 커밋 뒤 만료 표시 |
| 소유 개념 | **UploadedFile**(Root) — 발주사·업로더 계정(ID 참조), 용도, 형식·크기, 상태(PENDING → ATTACHED → EXPIRED), 연결 대상(호출 모듈이 정한 문자열), 연결 시각, 보관 기한 |
| 관계 | `schedule` → 참조(완료보고 사진·현장 사진, schedule 서비스가 공개 계약 `MediaFiles`를 직접 주입받음, Conformist) |

<a id="media-contract"></a>
## 공개 계약

- `MediaFiles` — `issueUpload`(호출자 트랜잭션 참여, `UploadRequest`·`UploadSpec` → `UploadTicket`, 거부는 `UploadNotAllowedException`), `stage`(트랜잭션 밖 전용, `StageRequest` → `StagedFiles`, 확인 실패는 결과의 거부 사유, 저장소 장애는 `MediaStorageUnavailableException`), `attach`(호출자 트랜잭션 필수, 목록을 대상의 그 용도 파일 전체로 확정, 확인 뒤 경합은 `AttachmentConflictException`), `downloadUrl`(서명만). 각 메서드·record의 조건은 Javadoc이 원본이다.
- 소비자: `schedule`(`WORK_SITE_PHOTO`·`WORK_COMPLETION_PHOTO` 용도, 연결 대상 `work:{workId}`).

<a id="media-implementation"></a>
## 구현 결정

- **순서**: 저장소 확인·복사(`stage`)는 DB 트랜잭션 밖, 기록 확정(`attach`)은 업무 트랜잭션 안. 복사 뒤 업무가 실패하면 PENDING으로 남고 같은 요청의 재시도가 다시 복사·연결한다. 기록만으로 거를 수 있는 파일(없음·남의 파일·용도 다름·중복·UUID 아님)은 저장소를 부르기 전에 거르고, 첫 거부에서 멈춘다.
- **확인 항목**: HeadObject의 크기·Content-Type이 발급 때 밝힌 값과 같고, 앞 16바이트가 그 형식의 이미지 시그니처(`ImageFormat`: JPEG·PNG·WebP·HEIC/HEIF)인지. 앞 16바이트 읽기와 복사는 HeadObject가 돌려준 ETag 조건(`If-Match`·`x-amz-copy-source-if-match`)으로 확인한 내용에만 한다. 그 사이 같은 키에 다시 올렸거나 지워졌으면(412·404) 업로드 안 됨(`NOT_UPLOADED`)이다(LocalStack 4.4에서 두 조건이 412로 막히는 것을 어댑터 테스트로 확인). 허용 형식은 설정 `app.media.image-content-types`이며 시그니처로 확인할 수 없는 형식은 둘 수 없다.
- **동시성**: `attach`는 요청한 파일 행과 그 대상에 지금 연결된 행을 식별자 순으로 한 번에 잠근다(PESSIMISTIC_WRITE). 같은 파일을 두 대상에 붙이거나 같은 대상을 동시에 바꾸는 요청은 한 줄로 서고, 뒤 요청은 `AttachmentConflictException`이다.
- **만료 표시**: 빠진 파일은 같은 트랜잭션에서 EXPIRED로 바꾸고 모듈 내부 사실 `UploadedFilesExpired`를 발행한다. 커밋 뒤 비동기 리스너가 객체에 태그 `status=expired`를 붙이며, 실패하면 경고만 남긴다(객체가 남을 뿐이다). 롤백되면 표시하지 않는다.
- **저장소**: `S3ObjectStorageAdapter`(모든 프로필). 업로드 서명에 `content-type`·`content-length`를 넣고 `requiredHeaders`로 돌려준다(SDK v2가 소문자 이름으로 서명, LocalStack 서명 검사로 다른 형식·크기 업로드가 403임을 확인). 호출 제한 시간 5초(시도당 3초), 저장소 오류·제한 시간 초과와 서명 실패(자격 증명을 얻지 못함)는 `ObjectStorageUnavailableException`이고 공개 계약에서는 `MediaStorageUnavailableException`(발급·확인·내려받기 주소 모두). 호출마다 타이머 `media.storage.calls`(Prometheus `media_storage_calls_seconds_*`, 태그 `operation`·`outcome`(success·missing·error)·`status`(S3 오류 응답의 HTTP 상태, 없으면 none))를 남기고, 장애는 작업·객체 키·상태 코드·S3 오류 코드를 WARN으로 남긴다(자격 증명·서명 주소는 남기지 않음). schedule은 장애 예외를 503 `SCHEDULE-023`의 원인으로 잇는다. 없는 키의 HeadObject는 404로 오고(앱 역할의 ListBucket 전제) "아직 올라오지 않음"으로 본다. 403은 권한 설정 오류라 업로드 전으로 가리지 않고 저장소 장애(경고·지표 `status=403`)로 드러낸다. 삭제 권한이 없어 객체를 지우지 않는다.
- **설정**: `app.media.storage.*`(버킷 `MEDIA_BUCKET`, 리전 `MEDIA_REGION`, S3 호환 주소·path-style·고정 자격 증명은 로컬·테스트 전용, 주소 유효 시간 10분), `app.media.retention-max-days`(`MEDIA_RETENTION_MAX_DAYS`), 용도별 보관 기간 `app.media.retention-days`(지금 비어 있음, 상한을 넘으면 기동 실패). 잘못된 값은 기동 시점에 거부한다.
- **테이블(ERD 반영용)**: `uploaded_files`(마이그레이션 `V2__create_uploaded_files.sql`이 만들고 Entity는 그 매핑, [ADR-005](../adr/005-schema-migration.md#결정)) — `id`(uuid PK), `version`, `organization_id`·`uploader_account_id`(다른 모듈 ID), `purpose`(50), `object_key`(200, 지금 상태의 객체 위치), `content_type`(100), `size_bytes`, `status`(20), `owner_ref`(100, null), `attached_at`·`retain_until`(null), 감사 컬럼, 인덱스 `ix_uploaded_files_owner(organization_id, purpose, owner_ref)`.

<a id="media-gaps"></a>
## 남은 작업

- 보관 배치: `retain_until` 채우기와 기간이 지난 파일의 만료, PENDING으로 남은 파일(복사했지만 연결되지 않은 `media/` 객체 포함)의 만료 표시, 실패한 만료 표시의 재시도. 용도별 보관 기간은 기획 결정 뒤 `app.media.retention-days`에 둔다.
