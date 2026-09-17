package com.example.shared.internal.config;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.example.shared.error.BaseCode;
import com.example.shared.internal.response.ApiResponse;
import com.example.shared.openapi.ApiErrorCodes;
import com.example.shared.openapi.ApiErrorCodesGroup;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

@Configuration
public class OpenApiResponseCustomizer {

    private static final String JSON_MEDIA_TYPE = "application/json";

    @Bean
    OperationCustomizer apiResponseOperationCustomizer() {
        return (operation, handlerMethod) -> {
            if (!handlerMethod.getBeanType().getPackageName().startsWith("com.example")) {
                return operation;
            }

            if (supportsResponseEnvelope(handlerMethod)) {
                wrapSuccessfulResponses(operation);
            }
            findErrorCodeAnnotations(handlerMethod).forEach(annotation -> addErrorResponses(operation, annotation));
            return operation;
        };
    }

    private boolean supportsResponseEnvelope(HandlerMethod handlerMethod) {
        Class<?> responseType = handlerMethod.getMethod().getReturnType();
        return !ApiResponse.class.isAssignableFrom(responseType)
                && !ResponseEntity.class.isAssignableFrom(responseType)
                && !Resource.class.isAssignableFrom(responseType)
                && !StreamingResponseBody.class.isAssignableFrom(responseType)
                && !byte[].class.isAssignableFrom(responseType)
                && !String.class.isAssignableFrom(responseType);
    }

    private void wrapSuccessfulResponses(Operation operation) {
        if (operation.getResponses() == null) {
            return;
        }

        operation.getResponses().forEach((statusCode, response) -> {
            if (!statusCode.startsWith("2") || response.getContent() == null) {
                return;
            }

            MediaType mediaType = response.getContent().get(JSON_MEDIA_TYPE);
            if (mediaType == null || mediaType.getSchema() == null) {
                return;
            }

            Schema<?> resultSchema = mediaType.getSchema();
            mediaType.setSchema(successEnvelopeSchema(resultSchema));
        });
    }

    private ObjectSchema successEnvelopeSchema(Schema<?> resultSchema) {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("success", new BooleanSchema().example(true));
        schema.addProperty("code", new StringSchema().example("COMMON-200"));
        schema.addProperty("message", new StringSchema().example("요청에 성공했습니다."));
        schema.addProperty("result", resultSchema);
        schema.required(List.of("success", "code", "message"));
        return schema;
    }

    private Set<ApiErrorCodes> findErrorCodeAnnotations(HandlerMethod handlerMethod) {
        Set<ApiErrorCodes> annotations = new LinkedHashSet<>(findAnnotations(handlerMethod.getMethod()));
        Class<?> beanType = handlerMethod.getBeanType();

        for (Class<?> interfaceType : ClassUtils.getAllInterfacesForClassAsSet(beanType)) {
            try {
                Method method = interfaceType.getMethod(
                        handlerMethod.getMethod().getName(),
                        handlerMethod.getMethod().getParameterTypes());
                annotations.addAll(findAnnotations(method));
            } catch (NoSuchMethodException ignored) {
            }
        }
        return annotations;
    }

    private Set<ApiErrorCodes> findAnnotations(Method method) {
        return AnnotatedElementUtils.findMergedRepeatableAnnotations(
                method, ApiErrorCodes.class, ApiErrorCodesGroup.class);
    }

    private void addErrorResponses(Operation operation, ApiErrorCodes annotation) {
        Set<String> includedNames = Set.of(annotation.includes());
        Collection<? extends BaseCode> codes =
                Arrays.asList(annotation.enumClass().getEnumConstants());

        Set<String> unknownNames = new LinkedHashSet<>(includedNames);
        codes.stream()
                .filter(code -> includedNames.contains(((Enum<?>) code).name()))
                .forEach(code -> {
                    unknownNames.remove(((Enum<?>) code).name());
                    addErrorResponse(operation, code);
                });

        if (!unknownNames.isEmpty()) {
            throw new IllegalStateException("Unknown error codes " + unknownNames + " in "
                    + annotation.enumClass().getName());
        }
    }

    private void addErrorResponse(Operation operation, BaseCode code) {
        String statusCode = Integer.toString(code.getHttpStatus().value());
        io.swagger.v3.oas.models.responses.ApiResponse response =
                operation.getResponses().get(statusCode);
        if (response == null) {
            response = new io.swagger.v3.oas.models.responses.ApiResponse().description(code.getMessage());
            operation.getResponses().addApiResponse(statusCode, response);
        }

        Content content = response.getContent();
        if (content == null) {
            content = new Content();
            response.setContent(content);
        }

        MediaType mediaType = content.get(JSON_MEDIA_TYPE);
        if (mediaType == null) {
            mediaType = new MediaType().schema(errorEnvelopeSchema(code));
            content.addMediaType(JSON_MEDIA_TYPE, mediaType);
        }

        mediaType.addExamples(
                code.getCode(), new Example().summary(code.getMessage()).value(errorExample(code)));
    }

    private ObjectSchema errorEnvelopeSchema(BaseCode code) {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("success", new BooleanSchema().example(false));
        schema.addProperty("code", new StringSchema().example(code.getCode()));
        schema.addProperty("message", new StringSchema().example(code.getMessage()));
        schema.addProperty("result", new Schema<>());
        schema.required(List.of("success", "code", "message"));
        return schema;
    }

    private Map<String, Object> errorExample(BaseCode code) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("success", false);
        example.put("code", code.getCode());
        example.put("message", code.getMessage());
        return example;
    }
}
