package com.orbit;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.orbit.profile.WithdrawalBlockReason;
import com.orbit.support.IntegrationTestSupport;

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
    void documentsMemberCategoryOperationsPerKind() throws Exception {
        String collection = "$.paths['/api/v1/organizations/{organizationId}/staff-categories']";
        String item = "$.paths['/api/v1/organizations/{organizationId}/technician-categories/{categoryId}']";
        String schemas = "#/components/schemas/com.orbit.organization.adapter.in.web.";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(collection + ".get.summary").value("직원 유형 목록 조회"))
                .andExpect(jsonPath(collection + ".get.security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(collection + ".get.responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value(schemas + "MemberCategoriesResponse"))
                .andExpect(jsonPath(collection + ".get.responses['403'].content['application/json']"
                                + ".examples['ORGANIZATION-003']")
                        .doesNotExist())
                .andExpect(jsonPath(collection + ".post.summary").value("직원 유형 생성"))
                .andExpect(jsonPath(collection + ".post.responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value(schemas + "MemberCategoryResponse"))
                .andExpect(jsonPath(collection + ".post.responses['400'].content['application/json']"
                                + ".examples['ORGANIZATION-007'].value.code")
                        .value("ORGANIZATION-007"))
                .andExpect(jsonPath(collection + ".post.responses['409'].content['application/json']"
                                + ".examples['ORGANIZATION-009'].value.code")
                        .value("ORGANIZATION-009"))
                .andExpect(jsonPath(item + ".put.summary").value("기사 유형 수정"))
                .andExpect(jsonPath(item + ".put.parameters[*].name").value(contains("organizationId", "categoryId")))
                .andExpect(jsonPath(item + ".put.responses['404'].content['application/json']"
                                + ".examples['ORGANIZATION-008'].value.code")
                        .value("ORGANIZATION-008"))
                .andExpect(jsonPath(item + ".delete.summary").value("기사 유형 삭제"))
                .andExpect(jsonPath(item + ".delete.responses['204']").exists())
                .andExpect(jsonPath(item + ".delete.responses['409'].content['application/json']"
                                + ".examples['ORGANIZATION-010'].value.code")
                        .value("ORGANIZATION-010"));
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
    void documentsMyMembershipListAsProtectedOperation() throws Exception {
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.paths['/api/v1/memberships'].get.summary").value("내 소속 목록 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/memberships'].get.security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/memberships'].get.parameters")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/memberships'].get.responses['200']"
                                + ".content['application/json'].schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.organization.adapter.in.web.MyMembershipsResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/memberships'].get.responses['401']"
                                + ".content['application/json'].examples['COMMON-401'].value.code")
                        .value("COMMON-401"));
    }

    @Test
    void documentsOwnerDesignationAsProtectedOperationWithAccessErrors() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/owners/{membershipId}'].put";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("총관리자 지정"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".parameters.length()").value(2))
                .andExpect(jsonPath(operation + ".responses['204']").exists())
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json']"
                                + ".examples['ORGANIZATION-002'].value.code")
                        .value("ORGANIZATION-002"))
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json']"
                                + ".examples['ORGANIZATION-003'].value.code")
                        .value("ORGANIZATION-003"))
                .andExpect(jsonPath(operation + ".responses['404'].content['application/json']"
                                + ".examples['ORGANIZATION-004'].value.code")
                        .value("ORGANIZATION-004"))
                .andExpect(jsonPath(operation + ".responses['409'].content['application/json']"
                                + ".examples['ORGANIZATION-006'].value.code")
                        .value("ORGANIZATION-006"));
    }

    @Test
    void documentsOwnerRevocationWithLastOwnerConflict() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/owners/{membershipId}'].delete";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("총관리자 해제"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".responses['204']").exists())
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json']"
                                + ".examples['ORGANIZATION-003'].value.code")
                        .value("ORGANIZATION-003"))
                .andExpect(jsonPath(operation + ".responses['409'].content['application/json']"
                                + ".examples['ORGANIZATION-005'].value.code")
                        .value("ORGANIZATION-005"));
    }

    @Test
    void documentsWorkRegistrationWithOptionalSchedule() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/works'].post";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("작업 등록"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['400'].content['application/json']"
                                + ".examples['SCHEDULE-013'].value.code")
                        .value("SCHEDULE-013"))
                .andExpect(jsonPath("$.components.schemas['com.orbit.schedule.adapter.in.web.CreateWorkRequest']"
                                + ".properties.schedule")
                        .exists())
                .andExpect(jsonPath("$.components.schemas['com.orbit.schedule.adapter.in.web.CreateWorkRequest']"
                                + ".properties.sitePhotoIds")
                        .exists());
    }

    @Test
    void documentsWorkDetailsUpdateWithScheduleErrors() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/works/{workId}'].put";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("작업 기본정보 수정"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".responses['204']").exists())
                .andExpect(jsonPath(operation + ".responses['400'].content['application/json']"
                                + ".examples['SCHEDULE-014'].value.code")
                        .value("SCHEDULE-014"))
                .andExpect(jsonPath(operation + ".responses['404'].content['application/json']"
                                + ".examples['SCHEDULE-001'].value.code")
                        .value("SCHEDULE-001"))
                .andExpect(jsonPath(operation + ".responses['409'].content['application/json']"
                                + ".examples['SCHEDULE-002'].value.code")
                        .value("SCHEDULE-002"));
    }

    @Test
    void documentsWorkCancellationWithReasonList() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/works/{workId}/cancellation'].post";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("작업 취소"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".responses['204']").exists())
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json']"
                                + ".examples['SCHEDULE-005'].value.code")
                        .value("SCHEDULE-005"))
                .andExpect(jsonPath("$.components.schemas['com.orbit.schedule.adapter.in.web.CancelWorkRequest']"
                                + ".properties.reason.enum")
                        .value(containsInAnyOrder(
                                "CUSTOMER_REQUEST", "TECHNICIAN_ISSUE", "DUPLICATE_REGISTRATION", "OTHER")));
    }

    @Test
    void documentsAssignmentChangeWithConflictResultAndErrors() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/works/{workId}/assignment'].put";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("배정 변경"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['400'].content['application/json']"
                                + ".examples['SCHEDULE-013'].value.code")
                        .value("SCHEDULE-013"))
                .andExpect(jsonPath(operation + ".responses['409'].content['application/json']"
                                + ".examples['SCHEDULE-008'].value.code")
                        .value("SCHEDULE-008"))
                .andExpect(jsonPath("$.components.schemas['com.orbit.schedule.adapter.in.web.ScheduleChangeResponse']"
                                + ".properties.applied")
                        .exists())
                .andExpect(jsonPath(operation + ".responses['409'].content['application/json']"
                                + ".examples['SCHEDULE-016'].value.code")
                        .value("SCHEDULE-016"))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.schedule.adapter.in.web.ChangeWorkAssignmentRequest']"
                                + ".properties.expectedVersion")
                        .exists())
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.schedule.adapter.in.web.timetable.TimetableResponse.ScheduledWork']"
                                + ".properties.version")
                        .exists())
                .andExpect(jsonPath("$.components.schemas['com.orbit.schedule.adapter.in.web.BacklogWorkResponse']"
                                + ".properties.version")
                        .exists())
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.schedule.adapter.in.web.detail.WorkDetailResponse']"
                                + ".properties.version")
                        .exists());
    }

    @Test
    void documentsTimetableQueryWithFiltersAndBacklogCards() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/timetable'].get";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("타임테이블 조회"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'technicianTypeIds')]")
                        .exists())
                .andExpect(jsonPath(operation + ".responses['400'].content['application/json']"
                                + ".examples['SCHEDULE-003'].value.code")
                        .value("SCHEDULE-003"))
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json']"
                                + ".examples['SCHEDULE-005'].value.code")
                        .value("SCHEDULE-005"))
                .andExpect(jsonPath("$.components.schemas['com.orbit.schedule.adapter.in.web.BacklogWorkResponse']"
                                + ".properties.stale")
                        .exists());
    }

    @Test
    void documentsWorkSearchAndBacklogSummary() throws Exception {
        String search = "$.paths['/api/v1/organizations/{organizationId}/works'].get";
        String backlog = "$.paths['/api/v1/organizations/{organizationId}/works/backlog'].get";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(search + ".summary").value("작업 목록 검색"))
                .andExpect(jsonPath(search + ".parameters[?(@.name == 'registrarId')]")
                        .exists())
                .andExpect(jsonPath(search + ".responses['400'].content['application/json']"
                                + ".examples['SCHEDULE-003'].value.code")
                        .value("SCHEDULE-003"))
                .andExpect(jsonPath(backlog + ".summary").value("대기함 요약 조회"))
                .andExpect(jsonPath(backlog + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.schedule.adapter.in.web.search.BacklogSummaryResponse']"
                                + ".properties.rejectedCount")
                        .exists())
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.schedule.adapter.in.web.search.BacklogSummaryResponse']"
                                + ".properties.registeredCount")
                        .exists())
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.schedule.adapter.in.web.search.BacklogSummaryResponse']"
                                + ".properties.totalCount")
                        .exists());
    }

    @Test
    void documentsWorkExportAsSpreadsheetWithRowLimit() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/works/export'].get";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("작업내역 출력"))
                .andExpect(jsonPath(operation + ".responses['200'].content"
                                + "['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet']")
                        .exists())
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'timeZone')].schema.type")
                        .value(contains("string")))
                .andExpect(jsonPath(operation + ".responses['400'].content['application/json']"
                                + ".examples['SCHEDULE-015'].value.code")
                        .value("SCHEDULE-015"));
    }

    @Test
    void documentsWorkDetailWithoutStatusCorrections() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/works/{workId}'].get";
        String schema = "$.components.schemas['com.orbit.schedule.adapter.in.web.detail.WorkDetailResponse']";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("작업 상세 조회"))
                .andExpect(jsonPath(operation + ".responses['404'].content['application/json']"
                                + ".examples['SCHEDULE-001'].value.code")
                        .value("SCHEDULE-001"))
                .andExpect(jsonPath(schema + ".properties.registeredAt").exists())
                .andExpect(jsonPath(schema + ".properties.sitePhotos").exists())
                .andExpect(jsonPath(schema + ".properties.statusCorrections").doesNotExist())
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.schedule.adapter.in.web.detail.WorkDetailResponse.Customer']"
                                + ".properties.addressDetail")
                        .exists())
                .andExpect(jsonPath("$.components.schemas['com.orbit.schedule.adapter.in.web.WorkCustomerRequest']"
                                + ".properties.postalCode")
                        .exists());
    }

    @Test
    void documentsProgressBoardWithCardsAndSummary() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/progress-board'].get";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("진행 보드 조회"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".responses['400'].content['application/json']"
                                + ".examples['SCHEDULE-003'].value.code")
                        .value("SCHEDULE-003"))
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json']"
                                + ".examples['SCHEDULE-005'].value.code")
                        .value("SCHEDULE-005"))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.schedule.adapter.in.web.board.ProgressBoardResponse.Card']"
                                + ".properties.customerName")
                        .exists());
    }

    @Test
    void documentsWorkUnassignment() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/works/{workId}/assignment'].delete";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("배정 해제"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".responses['204']").exists())
                .andExpect(jsonPath(operation + ".responses['409'].content['application/json']"
                                + ".examples['SCHEDULE-002'].value.code")
                        .value("SCHEDULE-002"))
                .andExpect(jsonPath(operation + ".responses['409'].content['application/json']"
                                + ".examples['SCHEDULE-016'].value.code")
                        .value("SCHEDULE-016"))
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'expectedVersion')].required")
                        .value(contains(true)));
    }

    @Test
    void documentsCompletionReportRequestWithIntervalLimit() throws Exception {
        String operation =
                "$.paths['/api/v1/organizations/{organizationId}/works/{workId}/completion-report-requests'].post";
        String detail = "$.components.schemas['com.orbit.schedule.adapter.in.web.detail.WorkDetailResponse']";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("완료보고 요청"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['409'].content['application/json']"
                                + ".examples['SCHEDULE-002'].value.code")
                        .value("SCHEDULE-002"))
                .andExpect(jsonPath(operation + ".responses['429'].content['application/json']"
                                + ".examples['SCHEDULE-019'].value.code")
                        .value("SCHEDULE-019"))
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json']"
                                + ".examples['SCHEDULE-005'].value.code")
                        .value("SCHEDULE-005"))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.schedule.adapter.in.web.CompletionReportRequestResponse']"
                                + ".properties.requestedAt")
                        .exists())
                .andExpect(jsonPath(detail + ".properties.completionReportRequests")
                        .exists())
                .andExpect(jsonPath(detail + ".properties.completionReportRequestable")
                        .exists());
    }

    @Test
    void documentsAppleLoginOperationsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/nonces'].post.summary")
                        .value("Apple 로그인 nonce 발급"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/nonces'].post.security")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/nonces'].post.responses['200']"
                                + ".content['application/json'].schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.auth.adapter.in.web.AppleLoginNonceResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/login'].post.summary")
                        .value("Apple id_token으로 로그인"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/login'].post.security")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/login'].post.responses['200']"
                                + ".content['application/json'].schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.auth.adapter.in.web.LoginResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/login'].post.responses['401']"
                                + ".content['application/json'].examples['AUTH-002'].value.code")
                        .value("AUTH-002"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/login'].post.responses['401']"
                                + ".content['application/json'].examples['AUTH-003'].value.code")
                        .value("AUTH-003"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/login'].post.responses['401']"
                                + ".content['application/json'].examples['AUTH-005'].value.code")
                        .value("AUTH-005"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/login'].post.responses['502']"
                                + ".content['application/json'].examples['AUTH-006'].value.code")
                        .value("AUTH-006"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/authorize'].get.summary")
                        .value("Apple 웹 로그인 시작"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/authorize'].get.security")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/authorize'].get.responses['302']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/callback'].post.responses['302']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/callback'].post.responses['401']"
                                + ".content['application/json'].examples['AUTH-003'].value.code")
                        .value("AUTH-003"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/exchange'].post.responses['200']"
                                + ".content['application/json'].schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.auth.adapter.in.web.LoginResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/apple/exchange'].post.responses['401']"
                                + ".content['application/json'].examples['AUTH-007'].value.code")
                        .value("AUTH-007"));
    }

    @Test
    void documentsCompanyCheckWithCodeParameterAndLookupErrors() throws Exception {
        String operation = "$.paths['/api/v1/organizations/lookup'].get";
        String notFound = operation + ".responses['404'].content['application/json'].examples";
        String tooMany = operation + ".responses['429'].content['application/json'].examples";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("회사 확인"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".parameters[0].name").value("companyCode"))
                .andExpect(jsonPath(operation + ".parameters[0].in").value("query"))
                .andExpect(jsonPath(operation + ".responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.organization.adapter.in.web.CompanyCheckResponse"))
                .andExpect(
                        jsonPath(notFound + "['ORGANIZATION-011'].value.code").value("ORGANIZATION-011"))
                .andExpect(
                        jsonPath(notFound + "['ORGANIZATION-012'].value.code").value("ORGANIZATION-012"))
                .andExpect(jsonPath(tooMany + "['ORGANIZATION-018'].value.code").value("ORGANIZATION-018"));
    }

    @Test
    void documentsParticipationRequestWithBodyAndConflicts() throws Exception {
        String operation = "$.paths['/api/v1/participation-requests'].post";
        String conflict = operation + ".responses['409'].content['application/json'].examples";
        String notFound = operation + ".responses['404'].content['application/json'].examples";
        String tooMany = operation + ".responses['429'].content['application/json'].examples";
        String schemas = "#/components/schemas/com.orbit.organization.adapter.in.web.";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("참여 요청"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".requestBody.content['application/json'].schema['$ref']")
                        .value(schemas + "RequestParticipationRequest"))
                .andExpect(jsonPath(operation + ".responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value(schemas + "ParticipationRequestResponse"))
                .andExpect(
                        jsonPath(notFound + "['ORGANIZATION-012'].value.code").value("ORGANIZATION-012"))
                .andExpect(
                        jsonPath(conflict + "['ORGANIZATION-013'].value.code").value("ORGANIZATION-013"))
                .andExpect(
                        jsonPath(conflict + "['ORGANIZATION-014'].value.code").value("ORGANIZATION-014"))
                .andExpect(jsonPath(conflict + "['ORGANIZATION-015']").doesNotExist())
                .andExpect(
                        jsonPath(conflict + "['ORGANIZATION-010'].value.code").value("ORGANIZATION-010"))
                .andExpect(jsonPath(tooMany + "['ORGANIZATION-018'].value.code").value("ORGANIZATION-018"))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.organization.adapter.in.web.RequestParticipationRequest']"
                                + ".properties.desiredType.enum.length()")
                        .value(2))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.organization.adapter.in.web.RequestParticipationRequest']"
                                + ".properties.requestChannel.enum.length()")
                        .value(3));
    }

    @Test
    void documentsPendingParticipationRequestListForOwners() throws Exception {
        String operation = "$.paths['/api/v1/organizations/{organizationId}/participation-requests'].get";
        String forbidden = operation + ".responses['403'].content['application/json'].examples";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("승인 대기 목록 조회"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".parameters[0].name").value("organizationId"))
                .andExpect(jsonPath(operation + ".responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value("#/components/schemas/"
                                + "com.orbit.organization.adapter.in.web.PendingParticipationRequestsResponse"))
                .andExpect(
                        jsonPath(forbidden + "['ORGANIZATION-002'].value.code").value("ORGANIZATION-002"))
                .andExpect(
                        jsonPath(forbidden + "['ORGANIZATION-003'].value.code").value("ORGANIZATION-003"));
    }

    @Test
    void documentsParticipationRequestApprovalAndRejectionAsBatches() throws Exception {
        String base = "$.paths['/api/v1/organizations/{organizationId}/participation-requests";
        String approve = base + "/approvals'].post";
        String reject = base + "/rejections'].post";
        String schemas = "#/components/schemas/com.orbit.organization.adapter.in.web.";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(approve + ".summary").value("참여 요청 승인"))
                .andExpect(jsonPath(approve + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(approve + ".requestBody.content['application/json'].schema['$ref']")
                        .value(schemas + "ApproveParticipationRequestsRequest"))
                .andExpect(jsonPath(approve + ".responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value(schemas + "ParticipationRequestDecisionsResponse"))
                .andExpect(jsonPath(approve + ".responses['403'].content['application/json']"
                                + ".examples['ORGANIZATION-003'].value.code")
                        .value("ORGANIZATION-003"))
                .andExpect(jsonPath(approve + ".responses['409'].content['application/json']"
                                + ".examples['ORGANIZATION-010'].value.code")
                        .value("ORGANIZATION-010"))
                .andExpect(jsonPath(reject + ".responses['409']").doesNotExist())
                .andExpect(jsonPath(reject + ".summary").value("참여 요청 거절"))
                .andExpect(jsonPath(reject + ".requestBody.content['application/json'].schema['$ref']")
                        .value(schemas + "RejectParticipationRequestsRequest"))
                .andExpect(jsonPath(reject + ".responses['400'].content['application/json']"
                                + ".examples['ORGANIZATION-019'].value.code")
                        .value("ORGANIZATION-019"))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.organization.adapter.in.web.ApproveParticipationRequestsRequest']"
                                + ".properties.approvals.maxItems")
                        .value(100));
    }

    @Test
    void documentsParticipationRequestCancelWithoutBody() throws Exception {
        String operation = "$.paths['/api/v1/participation-requests/{requestId}/cancel'].post";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("참여 요청 취소"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".parameters[0].name").value("requestId"))
                .andExpect(jsonPath(operation + ".requestBody").doesNotExist())
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json']"
                                + ".examples['ORGANIZATION-016'].value.code")
                        .value("ORGANIZATION-016"))
                .andExpect(jsonPath(operation + ".responses['409'].content['application/json']"
                                + ".examples['ORGANIZATION-017'].value.code")
                        .value("ORGANIZATION-017"));
    }

    @Test
    void documentsMyParticipationRequestListAsProtectedOperation() throws Exception {
        String operation = "$.paths['/api/v1/participation-requests'].get";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".summary").value("내 신청 현황 조회"))
                .andExpect(jsonPath(operation + ".security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operation + ".parameters").doesNotExist())
                .andExpect(jsonPath(operation + ".responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.organization.adapter.in.web."
                                + "MyParticipationRequestsResponse"));
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

    @Test
    void documentsProfileSignupApis() throws Exception {
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/profiles/{accountId}'].get.summary")
                        .value("내 프로필·가입 단계 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/profiles/{accountId}'].get.security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/profiles/{accountId}'].get.responses['200']"
                                + ".content['application/json'].schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.profile.adapter.in.web.ProfileResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/profiles/{accountId}'].get.responses['404']"
                                + ".content['application/json'].examples['PROFILE-002'].value.code")
                        .value("PROFILE-002"))
                .andExpect(jsonPath("$.paths['/api/v1/profiles/{accountId}'].put.responses['400']"
                                + ".content['application/json'].examples['PROFILE-001'].value.code")
                        .value("PROFILE-001"))
                .andExpect(jsonPath("$.paths['/api/v1/profiles/{accountId}/terms-agreements'].post.responses['400']"
                                + ".content['application/json'].examples['PROFILE-004'].value.code")
                        .value("PROFILE-004"))
                .andExpect(jsonPath("$.paths['/api/v1/profiles/{accountId}/terms-agreements'].post.responses['409']"
                                + ".content['application/json'].examples['PROFILE-003'].value.code")
                        .value("PROFILE-003"))
                .andExpect(jsonPath("$.paths['/api/v1/profiles/{accountId}/marketing-consents'].post.responses['403']"
                                + ".content['application/json'].examples['PROFILE-005'].value.code")
                        .value("PROFILE-005"));
    }

    @Test
    void documentsWithdrawalApis() throws Exception {
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/profiles/{accountId}/withdrawals'].post.responses['409']"
                                + ".content['application/json'].examples['PROFILE-006'].value.code")
                        .value("PROFILE-006"))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.profile.adapter.in.web.WithdrawalEligibilityResponse.Block']"
                                + ".properties.reason.enum")
                        .value(containsInAnyOrder(Arrays.stream(WithdrawalBlockReason.values())
                                .map(Enum::name)
                                .toArray())));
    }

    @Test
    void documentsNotificationCenterAsProtectedOperations() throws Exception {
        String list = "$.paths['/api/v1/notifications'].get";
        String unreadCount = "$.paths['/api/v1/notifications/unread-count'].get";
        String read = "$.paths['/api/v1/notifications/{notificationId}/read'].put";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(list + ".summary").value("내 알림 목록 조회"))
                .andExpect(
                        jsonPath(list + ".security[0]['Bearer Authentication']").exists())
                .andExpect(
                        jsonPath(list + ".parameters[*].name").value(containsInAnyOrder("unreadOnly", "page", "size")))
                .andExpect(jsonPath(list + ".responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.notification.adapter.in.web.NotificationsResponse"))
                .andExpect(jsonPath(list + ".responses['400'].content['application/json']"
                                + ".examples['NOTIFICATION-001'].value.code")
                        .value("NOTIFICATION-001"))
                .andExpect(jsonPath(unreadCount + ".summary").value("안 읽은 알림 수 조회"))
                .andExpect(jsonPath(unreadCount + ".responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.notification.adapter.in.web."
                                + "UnreadNotificationCountResponse"))
                .andExpect(jsonPath(read + ".summary").value("알림 읽음 처리"))
                .andExpect(
                        jsonPath(read + ".security[0]['Bearer Authentication']").exists())
                .andExpect(jsonPath(read + ".parameters[0].name").value("notificationId"))
                .andExpect(jsonPath(read + ".requestBody").doesNotExist())
                .andExpect(jsonPath(read + ".responses['204']").exists())
                .andExpect(jsonPath(read + ".responses['403'].content['application/json']"
                                + ".examples['NOTIFICATION-003'].value.code")
                        .value("NOTIFICATION-003"))
                .andExpect(jsonPath(read + ".responses['404'].content['application/json']"
                                + ".examples['NOTIFICATION-002'].value.code")
                        .value("NOTIFICATION-002"));
    }

    @Test
    void documentsNotificationSettingReadAndChange() throws Exception {
        String operations = "$.paths['/api/v1/notification-settings']";
        mockMvc.perform(get("/docs-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operations + ".get.summary").value("내 알림 설정 조회"))
                .andExpect(jsonPath(operations + ".get.responses['200'].content['application/json']"
                                + ".schema.properties.result['$ref']")
                        .value("#/components/schemas/com.orbit.notification.adapter.in.web."
                                + "NotificationSettingResponse"))
                .andExpect(jsonPath(operations + ".put.summary").value("내 알림 설정 저장"))
                .andExpect(jsonPath(operations + ".put.security[0]['Bearer Authentication']")
                        .exists())
                .andExpect(jsonPath(operations + ".put.requestBody").exists())
                .andExpect(jsonPath(operations + ".put.responses['204']").exists())
                .andExpect(jsonPath(operations + ".put.responses['409'].content['application/json']"
                                + ".examples['NOTIFICATION-004'].value.code")
                        .value("NOTIFICATION-004"))
                .andExpect(jsonPath("$.components.schemas"
                                + "['com.orbit.notification.adapter.in.web.NotificationSettingRequest']"
                                + ".required.length()")
                        .value(3));
    }
}
