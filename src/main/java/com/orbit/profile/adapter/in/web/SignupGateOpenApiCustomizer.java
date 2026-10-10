package com.orbit.profile.adapter.in.web;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import com.orbit.profile.application.error.ProfileErrorCode;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * 가입 완료 공통 검사({@link SignupGateInterceptor})가 다른 모듈 API에서 돌려줄 수 있는 403 PROFILE-005를 문서에 덧붙인다. 다른 모듈의 ControllerDocs는
 * profile 내부 오류 타입을 선언할 수 없으므로, Bearer 인증이 필요하고 허용 목록({@link ProfileWebConfig#ALLOWED_BEFORE_SIGNUP}) 밖인 {@code /api/**}
 * 연산마다 profile이 대신 붙인다. 응답 봉투와 예시 모양은 공통 OpenAPI 커스터마이저의 실패 응답과 같다.
 */
@Component
class SignupGateOpenApiCustomizer implements OpenApiCustomizer {

    private static final String BEARER_SCHEME = "Bearer Authentication";
    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private static final ProfileErrorCode CODE = ProfileErrorCode.SIGNUP_NOT_COMPLETED;

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, item) -> {
            if (isGated(path)) {
                item.readOperations().stream()
                        .filter(SignupGateOpenApiCustomizer::requiresBearer)
                        .forEach(SignupGateOpenApiCustomizer::addForbiddenResponse);
            }
        });
    }

    private static boolean isGated(String path) {
        return PATHS.match("/api/**", path)
                && Arrays.stream(ProfileWebConfig.ALLOWED_BEFORE_SIGNUP)
                        .noneMatch(allowed -> PATHS.match(allowed, path));
    }

    private static boolean requiresBearer(Operation operation) {
        return operation.getSecurity() != null
                && operation.getSecurity().stream().anyMatch(requirement -> requirement.containsKey(BEARER_SCHEME));
    }

    private static void addForbiddenResponse(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        String status = Integer.toString(CODE.getHttpStatus().value());
        ApiResponse response = operation.getResponses().get(status);
        if (response == null) {
            response = new ApiResponse().description(CODE.getMessage());
            operation.getResponses().addApiResponse(status, response);
        }
        if (response.getContent() == null) {
            response.setContent(new Content());
        }
        MediaType mediaType = response.getContent().get(JSON_MEDIA_TYPE);
        if (mediaType == null) {
            mediaType = new MediaType().schema(errorEnvelopeSchema());
            response.getContent().addMediaType(JSON_MEDIA_TYPE, mediaType);
        }
        mediaType.addExamples(
                CODE.getCode(), new Example().summary(CODE.getMessage()).value(errorExample()));
    }

    private static ObjectSchema errorEnvelopeSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("success", new BooleanSchema().example(false));
        schema.addProperty("code", new StringSchema().example(CODE.getCode()));
        schema.addProperty("message", new StringSchema().example(CODE.getMessage()));
        schema.addProperty("result", new Schema<>());
        schema.required(List.of("success", "code", "message"));
        return schema;
    }

    private static Map<String, Object> errorExample() {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("success", false);
        example.put("code", CODE.getCode());
        example.put("message", CODE.getMessage());
        return example;
    }
}
