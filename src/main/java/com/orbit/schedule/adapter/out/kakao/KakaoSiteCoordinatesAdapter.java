package com.orbit.schedule.adapter.out.kakao;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orbit.schedule.application.port.out.SiteCoordinatesPort;
import com.orbit.schedule.domain.SiteCoordinates;

import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 로컬 API의 주소 검색으로 현장 기본주소의 좌표를 찾는다. 검색 결과 중 지번·도로명 주소 하나까지 맞은 첫 결과를 쓰며({@code x}가 경도,
 * {@code y}가 위도, WGS84), 시·구·동이나 도로 전체만 맞은 결과는 길찾기에 부정확해 쓰지 않는다. 그런 결과뿐이거나 결과가 없거나, 오류 응답·통신 실패·제한
 * 시간 초과·해석할 수 없는 응답이면 빈 값을 돌려준다. 주소는 개인정보라 로그에 남기지 않는다.
 */
@Slf4j
@Component
class KakaoSiteCoordinatesAdapter implements SiteCoordinatesPort {

    /** 시·구·동이나 도로 전체만 맞은 결과(REGION·ROAD)를 거르고도 주소 하나까지 맞은 결과를 고를 만큼 받는다. */
    private static final int MAX_RESULTS = 5;

    /** 지번 주소(REGION_ADDR)·도로명 주소(ROAD_ADDR) 하나까지 맞은 결과. */
    private static final Set<String> FULL_ADDRESS_TYPES = Set.of("REGION_ADDR", "ROAD_ADDR");

    private final KakaoLocalApiProperties properties;
    private final RestClient restClient;

    KakaoSiteCoordinatesAdapter(KakaoLocalApiProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public Optional<SiteCoordinates> find(String baseAddress) {
        try {
            AddressSearchResponse response = restClient
                    .get()
                    // 주소는 변수로 넘겨 { } + & 같은 문자까지 모두 인코딩한다. 템플릿에 직접 넣으면 { }를 URI 변수로 해석한다.
                    .uri(UriComponentsBuilder.fromUriString(properties.addressSearchUri())
                            .queryParam("query", "{query}")
                            .queryParam("size", MAX_RESULTS)
                            .encode()
                            .buildAndExpand(baseAddress)
                            .toUri())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                    .retrieve()
                    .body(AddressSearchResponse.class);
            return fullAddressCoordinates(response);
        } catch (RestClientResponseException exception) {
            log.warn("Kakao address search API rejected request: status={}", exception.getStatusCode());
            return Optional.empty();
        } catch (RuntimeException exception) {
            // 포트 계약대로 어떤 실패도 작업 저장을 막지 않는다. 메시지에 주소가 들어갈 수 있어 예외 종류만 남긴다.
            log.warn(
                    "Kakao address search API call failed: {}",
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Optional<SiteCoordinates> fullAddressCoordinates(AddressSearchResponse response) {
        if (response == null || response.documents() == null) {
            return Optional.empty();
        }
        return response.documents().stream()
                .filter(document -> document != null && FULL_ADDRESS_TYPES.contains(document.addressType()))
                .findFirst()
                .filter(document -> document.x() != null && document.y() != null)
                .map(document -> new SiteCoordinates(new BigDecimal(document.y()), new BigDecimal(document.x())));
    }

    record AddressSearchResponse(List<Document> documents) {}

    /** 검색된 주소 하나. 카카오는 좌표를 문자열로 준다. */
    record Document(@JsonProperty("address_type") String addressType, String x, String y) {}
}
