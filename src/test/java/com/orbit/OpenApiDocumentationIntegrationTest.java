package com.orbit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.orbit.support.IntegrationTestSupport;

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
                        .value("#/components/schemas/com.orbit.user.adapter.in.web.UserResponse"))
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
    void documentsKakaoLoginErrorsAndProtectedSessionOperations() throws Exception {
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao/nonces'].post.summary")
                        .value("로그인 nonce 발급"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao/nonces'].post.security")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.summary")
                        .value("카카오 id_token으로 로그인"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.responses['200']"
                                + ".content['application/json'].schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.auth.adapter.in.web.LoginResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.responses['401']"
                                + ".content['application/json'].examples['AUTH-002'].value.code")
                        .value("AUTH-002"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.responses['401']"
                                + ".content['application/json'].examples['AUTH-003'].value.code")
                        .value("AUTH-003"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.responses['404']"
                                + ".content['application/json'].examples['COMMON-404'].value.code")
                        .value("COMMON-404"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.responses['404']"
                                + ".content['application/json'].examples['AUTH-004'].value.code")
                        .value("AUTH-004"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']")
                        .exists());
    }

    @Test
    void documentsOrganizationCreationAsProtectedOperationWithInputErrors() throws Exception {
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/organizations'].post.summary")
                        .value("발주사 생성"))
                .andExpect(jsonPath("$.paths['/api/v1/organizations'].post.security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/organizations'].post.parameters")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/organizations'].post.responses['200']"
                                + ".content['application/json'].schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.organization.adapter.in.web."
                                + "CreatedOrganizationResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/organizations'].post.responses['400']"
                                + ".content['application/json'].examples['ORGANIZATION-001'].value.code")
                        .value("ORGANIZATION-001"))
                .andExpect(jsonPath("$.paths['/api/v1/organizations'].post.responses['400']"
                                + ".content['application/json'].examples['COMMON-400'].value.code")
                        .value("COMMON-400"))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.organization.adapter.in.web.CreateOrganizationRequest']"
                                + ".properties.industry.enum.length()")
                        .value(6));
    }

    @Test
    void documentsOrganizationReadAsOwnerOnlyOperation() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}'].get";
        String forbidden = operation + ".responses['403'].content['application/json'].examples";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("발주사 정보 조회"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".parameters[0].name").value("organizationId"))
                .andExpect(jsonPath(operation + ".parameters[0].in").value("path"))
                .andExpect(jsonPath(operation + ".responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.organization.adapter.in.web.OrganizationResponse"))
                .andExpect(
                        jsonPath(forbidden + "['ORGANIZATION-002'].value.code").value("ORGANIZATION-002"))
                .andExpect(
                        jsonPath(forbidden + "['ORGANIZATION-003'].value.code").value("ORGANIZATION-003"));
    }

    @Test
    void documentsOrganizationUpdateAsOwnerOnlyOperationWithInputErrors() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}'].put";
        String badRequest = operation + ".responses['400'].content['application/json'].examples";
        String forbidden = operation + ".responses['403'].content['application/json'].examples";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("발주사 정보 수정"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".parameters[0].name").value("organizationId"))
                .andExpect(jsonPath(operation + ".responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.organization.adapter.in.web.OrganizationResponse"))
                .andExpect(
                        jsonPath(badRequest + "['ORGANIZATION-001'].value.code").value("ORGANIZATION-001"))
                .andExpect(
                        jsonPath(forbidden + "['ORGANIZATION-002'].value.code").value("ORGANIZATION-002"))
                .andExpect(
                        jsonPath(forbidden + "['ORGANIZATION-003'].value.code").value("ORGANIZATION-003"))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.organization.adapter.in.web.UpdateOrganizationRequest']"
                                + ".properties.industry.enum.length()")
                        .value(6));
    }

    @Test
    void documentsCompanyCodeReadAsOwnerOnlyOperation() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/company-code'].get";
        String forbidden = operation + ".responses['403'].content['application/json'].examples";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("회사 코드 조회"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".parameters[0].name").value("organizationId"))
                .andExpect(jsonPath(operation + ".responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.organization.adapter.in.web.CompanyCodeResponse"))
                .andExpect(
                        jsonPath(forbidden + "['ORGANIZATION-002'].value.code").value("ORGANIZATION-002"))
                .andExpect(
                        jsonPath(forbidden + "['ORGANIZATION-003'].value.code").value("ORGANIZATION-003"));
    }

    @Test
    void documentsCompanyCodeChangeAsOwnerOnlyOperationWithoutBody() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/company-code'].post";
        String forbidden = operation + ".responses['403'].content['application/json'].examples";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("회사 코드 변경"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".parameters[0].name").value("organizationId"))
                .andExpect(jsonPath(operation + ".requestBody").doesNotExist())
                .andExpect(jsonPath(operation + ".responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.organization.adapter.in.web.CompanyCodeResponse"))
                .andExpect(
                        jsonPath(forbidden + "['ORGANIZATION-002'].value.code").value("ORGANIZATION-002"))
                .andExpect(
                        jsonPath(forbidden + "['ORGANIZATION-003'].value.code").value("ORGANIZATION-003"));
    }

    @Test
    void documentsRequestAndResponseFields() throws Exception {
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.user.adapter.in.web.RegisterUserRequest']"
                                + ".properties.displayName.description")
                        .value("표시 이름"))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.user.adapter.in.web.UserResponse']"
                                + ".properties.id.example")
                        .value(1))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.auth.adapter.in.web.AuthSubjectResponse']"
                                + ".properties.subject.description")
                        .value("예제 subject 식별자"));
    }
}
