package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.example.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class OpenApiDocumentationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsUserRegistrationResponseEnvelopeAndValidationError() throws Exception {
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/users'].post.summary").value("사용자 등록"))
                .andExpect(jsonPath("$.paths['/api/v1/users'].post.responses['200']"
                                + ".content['application/json'].schema.properties.success.type")
                        .value("boolean"))
                .andExpect(jsonPath("$.paths['/api/v1/users'].post.responses['200']"
                                + ".content['application/json'].schema.properties.result['$ref']")
                        .value("#/components/schemas/com.example.user.adapter.in.web.UserResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/users'].post.responses['200']"
                                + ".content['application/json'].schema.required[?(@ == 'result')]")
                        .isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/users'].post.responses['400']"
                                + ".content['application/json'].examples['COMMON-400'].value.code")
                        .value("COMMON-400"))
                .andExpect(jsonPath("$.paths['/api/v1/users'].post.responses['400']"
                                + ".content['application/json'].examples['COMMON-400'].value.result")
                        .doesNotHaveJsonPath());
    }

    @Test
    void documentsAuthSubjectNotFoundError() throws Exception {
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/examples/subjects/{userId}'].get.summary")
                        .value("예제 subject 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/examples/subjects/{userId}'].get.responses['404']"
                                + ".content['application/json'].schema.properties.code.example")
                        .value("AUTH-001"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/examples/subjects/{userId}'].get.responses['404']"
                                + ".content['application/json'].examples['AUTH-001'].value.code")
                        .value("AUTH-001"));
    }

    @Test
    void publishesBearerSchemeWithoutRequiringItForPublicOperations() throws Exception {
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes['Bearer Authentication'].scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/users'].post.security").doesNotExist());
    }

    @Test
    void documentsRequestAndResponseFields() throws Exception {
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.example.user.adapter.in.web.RegisterUserRequest']"
                                + ".properties.displayName.description")
                        .value("표시 이름"))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.example.user.adapter.in.web.UserResponse']"
                                + ".properties.id.example")
                        .value(1))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.example.auth.adapter.in.web.AuthSubjectResponse']"
                                + ".properties.subject.description")
                        .value("예제 subject 식별자"));
    }
}
