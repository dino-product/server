package com.orbit.media.adapter.out.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** S3 클라이언트와 서명기. 둘 다 같은 리전·주소·자격 증명을 쓴다. 저장소 호출은 제한 시간을 넘기면 실패한다. */
@Configuration(proxyBeanMethods = false)
class S3ClientConfiguration {

    /** 기본 체인은 닫을 수 있는 구현이라 Spring이 종료 때 닫는다(추론한 close). */
    @Bean
    AwsCredentialsProvider mediaCredentialsProvider(S3StorageProperties properties) {
        if (properties.hasStaticCredentials()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean(destroyMethod = "close")
    S3Client mediaS3Client(S3StorageProperties properties, AwsCredentialsProvider mediaCredentialsProvider) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(mediaCredentialsProvider)
                .serviceConfiguration(serviceConfiguration(properties))
                .overrideConfiguration(override -> override.apiCallTimeout(properties.apiCallTimeout())
                        .apiCallAttemptTimeout(properties.apiCallAttemptTimeout()));
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner mediaS3Presigner(S3StorageProperties properties, AwsCredentialsProvider mediaCredentialsProvider) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(mediaCredentialsProvider)
                .serviceConfiguration(serviceConfiguration(properties));
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        return builder.build();
    }

    private static S3Configuration serviceConfiguration(S3StorageProperties properties) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build();
    }
}
