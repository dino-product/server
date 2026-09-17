package com.example.shared.internal.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ApiResponse")
class ApiResponseTest {

    @Test
    @DisplayName("성공 응답을 생성한다")
    void createSuccessResponse() {
        String result = "result";

        ApiResponse<String> response = ApiResponse.success(result);

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("COMMON-200");
        assertThat(response.result()).isEqualTo(result);
    }
}
