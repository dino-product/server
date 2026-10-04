package com.orbit.auth.adapter.out.kakao;

import java.net.MalformedURLException;
import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
class KakaoOidcConfig {

    /** 카카오 JWKS를 캐시·재시도와 함께 조회한다. 실제 조회는 첫 검증 시점에 일어나며 기동 시 네트워크에 접근하지 않는다. */
    @Bean
    JWKSource<SecurityContext> kakaoJwkSource(KakaoOidcProperties properties) throws MalformedURLException {
        return JWKSourceBuilder.create(URI.create(properties.jwkSetUri()).toURL())
                .retrying(true)
                .build();
    }
}
