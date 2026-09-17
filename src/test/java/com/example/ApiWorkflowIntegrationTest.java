package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.support.IntegrationTestSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class ApiWorkflowIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registersUserAndReadsExampleAuthSubject() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"홍길동"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.displayName").value("홍길동"))
                .andReturn();

        JsonNode body = objectMapper.readTree(registration.getResponse().getContentAsByteArray());
        long userId = body.path("result").path("id").asLong();

        mockMvc.perform(get("/api/v1/auth/examples/subjects/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.subject").value("user:" + userId));
    }

    @Test
    void returnsAuthErrorForMissingUser() throws Exception {
        mockMvc.perform(get("/api/v1/auth/examples/subjects/{userId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    void doesNotExposeLoginAsPublicExample() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBlankDisplayName() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    void rejectsNonPositiveAuthUserId() throws Exception {
        mockMvc.perform(get("/api/v1/auth/examples/subjects/{userId}", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }
}
