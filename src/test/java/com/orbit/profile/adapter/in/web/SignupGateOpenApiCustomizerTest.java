package com.orbit.profile.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;

@DisplayName("가입 완료 공통 검사의 OpenAPI 문서화")
class SignupGateOpenApiCustomizerTest {

    @Test
    @DisplayName("허용 목록 밖의 인증 API에 403 PROFILE-005 응답 예시를 덧붙인다")
    void documentsSignupGateOnProtectedOperations() {
        Operation createOrganization = bearerOperation();
        OpenAPI openApi = openApi(Map.of("/api/v1/organizations", new PathItem().post(createOrganization)));

        new SignupGateOpenApiCustomizer().customise(openApi);

        ApiResponse forbidden = createOrganization.getResponses().get("403");
        assertThat(forbidden).isNotNull();
        Example example =
                forbidden.getContent().get("application/json").getExamples().get("PROFILE-005");
        assertThat(example.getValue())
                .isEqualTo(Map.of(
                        "success", false,
                        "code", "PROFILE-005",
                        "message", "가입 또는 약관 재동의를 마친 뒤 이용할 수 있습니다."));
    }

    @Test
    @DisplayName("이미 403 응답이 있으면 예시만 더한다")
    void addsExampleToExistingForbiddenResponse() {
        Operation operation = bearerOperation();
        new SignupGateOpenApiCustomizer().customise(openApi(Map.of("/api/v1/works", new PathItem().get(operation))));
        new SignupGateOpenApiCustomizer().customise(openApi(Map.of("/api/v1/works", new PathItem().get(operation))));

        assertThat(operation
                        .getResponses()
                        .get("403")
                        .getContent()
                        .get("application/json")
                        .getExamples())
                .containsOnlyKeys("PROFILE-005");
    }

    @Test
    @DisplayName("가입 전에 허용하는 profile·auth API와 인증이 필요 없는 API에는 덧붙이지 않는다")
    void skipsAllowedAndPublicOperations() {
        Operation profile = bearerOperation();
        Operation auth = bearerOperation();
        Operation publicOperation = new Operation().responses(new ApiResponses());
        OpenAPI openApi = openApi(Map.of(
                "/api/v1/profiles/{accountId}", new PathItem().get(profile),
                "/api/v1/auth/me", new PathItem().get(auth),
                "/api/v1/users", new PathItem().post(publicOperation)));

        new SignupGateOpenApiCustomizer().customise(openApi);

        assertThat(profile.getResponses()).doesNotContainKey("403");
        assertThat(auth.getResponses()).doesNotContainKey("403");
        assertThat(publicOperation.getResponses()).doesNotContainKey("403");
    }

    private static Operation bearerOperation() {
        return new Operation()
                .responses(new ApiResponses())
                .security(List.of(new SecurityRequirement().addList("Bearer Authentication")));
    }

    private static OpenAPI openApi(Map<String, PathItem> pathItems) {
        Paths paths = new Paths();
        pathItems.forEach(paths::addPathItem);
        return new OpenAPI().paths(paths);
    }
}
