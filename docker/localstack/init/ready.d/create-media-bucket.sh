#!/bin/sh
# 로컬 사진 저장소 버킷. LocalStack이 준비되면 실행된다(이미 있으면 그대로 둔다).
awslocal s3api head-bucket --bucket "${MEDIA_BUCKET:-orbit-media-local}" 2>/dev/null \
  || awslocal s3api create-bucket --bucket "${MEDIA_BUCKET:-orbit-media-local}" \
    --create-bucket-configuration LocationConstraint="${MEDIA_REGION:-ap-northeast-2}"
