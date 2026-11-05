package com.orbit.support;

import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 업로드가 필요한 테스트의 S3 호환 저장소(LocalStack). JVM에서 한 번만 띄워 여러 테스트가 같이 쓰며, 테스트마다 다른 객체 키를 써서 서로의 데이터와 섞이지 않게
 * 한다. 버전은 Compose와 같게 고정한다(최신 이미지는 인증 토큰이 필요).
 */
public final class LocalStackTestSupport {

    public static final String BUCKET = "orbit-media-it";
    public static final String REGION = "ap-northeast-2";

    private static final DockerImageName IMAGE = DockerImageName.parse("localstack/localstack:4.4");
    private static LocalStackContainer container;

    private LocalStackTestSupport() {}

    /** 컨테이너를 (처음이면) 띄우고 버킷을 만든 뒤 S3 주소를 돌려준다. */
    public static synchronized String endpoint() {
        if (container == null) {
            LocalStackContainer started = new LocalStackContainer(IMAGE)
                    .withServices("s3")
                    .withEnv("AWS_DEFAULT_REGION", REGION)
                    // 기본값은 presigned URL 서명을 검사하지 않는다. 서명한 형식·크기만 받는지 확인하려고 켠다.
                    .withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0");
            started.start();
            try {
                var result = started.execInContainer(
                        "awslocal",
                        "s3api",
                        "create-bucket",
                        "--bucket",
                        BUCKET,
                        "--create-bucket-configuration",
                        "LocationConstraint=" + REGION);
                if (result.getExitCode() != 0) {
                    throw new IllegalStateException("bucket creation failed: " + result.getStderr());
                }
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            container = started;
        }
        return container.getEndpoint().toString();
    }
}
